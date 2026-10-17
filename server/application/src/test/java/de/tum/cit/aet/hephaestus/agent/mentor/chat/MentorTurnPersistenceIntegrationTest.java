package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static de.tum.cit.aet.hephaestus.testconfig.LlmCatalogTestFixtures.admittedMentorConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.hephaestus.agent.mentor.MentorLlmConfig;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.exception.MentorRetryRejectedException;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.exception.TurnAlreadyInFlightException;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.PiEventToUiChunkTranslator;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEventRepository;
import de.tum.cit.aet.hephaestus.agent.usage.MeasurementVerification;
import de.tum.cit.aet.hephaestus.agent.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.UsageProvenance;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.privacy.PersonDataRegistry;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonScope;
import de.tum.cit.aet.hephaestus.integration.core.spi.UsagePricingState;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageDTO;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadService;
import de.tum.cit.aet.hephaestus.mentor.MentorTurnAccount;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Validates the {@link MentorTurnPersistence} REQUIRES_NEW contract end-to-end against a real
 * Postgres container: DB unique partial index, JSONB metadata round-trip, status transitions,
 * reaper sweep.
 */
class MentorTurnPersistenceIntegrationTest extends BaseIntegrationTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Autowired
    private MentorTurnPersistence persistence;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private LlmUsageEventRepository usageEventRepository;

    @Autowired
    private MentorInFlightAccounting accounting;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ChatThreadService chatThreadService;

    @Autowired
    private PersonDataRegistry personDataRegistry;

    private Workspace workspace;
    private long accountId;

    @BeforeEach
    void setUp() throws Exception {
        databaseTestUtils.cleanDatabase();
        try (var conn = dataSource.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_message_in_flight_v2 "
                    + "ON chat_message (thread_id) WHERE status = 'in_flight'");
            // Drop-then-add so a previous run's constraint doesn't survive across tests.
            stmt.execute("ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS chk_chat_message_status");
            stmt.execute("ALTER TABLE chat_message ADD CONSTRAINT chk_chat_message_status "
                    + "CHECK (status IN ('in_flight', 'completed', 'interrupted'))");
        }
        workspace = new Workspace();
        workspace.setWorkspaceSlug("mentor-persist-ws");
        workspace.setDisplayName("Mentor Persistence Workspace");
        workspace.setAccountLogin("mentor-persist-org");
        workspace.setAccountType(AccountType.ORG);
        workspace = workspaceRepository.save(workspace);

        accountId = Objects.requireNonNull(
                accountRepository.save(new Account("Mentor Tester")).getId());
    }

    @AfterEach
    void restoreGeneratedSchema() throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("DROP INDEX IF EXISTS ux_chat_message_in_flight_v2");
            statement.execute("ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS chk_chat_message_status");
        }
    }

    @Test
    void ensureThread_createsWhenAbsent() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = persistence.ensureThread(workspace.getId(), threadId, accountId, "Hello mentor");
        assertThat(thread.getId()).isEqualTo(threadId);
        assertThat(thread.getAccountId()).isEqualTo(accountId);
        assertThat(thread.getWorkspace().getId()).isEqualTo(workspace.getId());
        assertThat(thread.getTitle()).isEqualTo("Hello mentor");
        assertThat(chatThreadRepository.findById(threadId)).isPresent();
    }

    @Test
    void ensureThread_returnsExisting() {
        UUID threadId = UUID.randomUUID();
        ChatThread first = persistence.ensureThread(workspace.getId(), threadId, accountId, "first prompt");
        ChatThread second = persistence.ensureThread(workspace.getId(), threadId, accountId, "second prompt");
        assertThat(second.getId()).isEqualTo(first.getId());
        // Title is fixed on first write — a second call with a different prompt must NOT
        // overwrite, otherwise the thread sidebar flickers between titles.
        assertThat(second.getTitle()).isEqualTo("first prompt");
    }

    @Test
    void ensureThread_foreignOwnerThrows() {
        UUID threadId = UUID.randomUUID();
        persistence.ensureThread(workspace.getId(), threadId, accountId, "hello");

        long otherId = Objects.requireNonNull(
                accountRepository.save(new Account("Other")).getId());
        assertThatThrownBy(() -> persistence.ensureThread(workspace.getId(), threadId, otherId, "intruder"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void persistInFlight_happyPath(CapturedOutput output) {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello mentor", assistantId, null, admittedMentorConfig());
        assertThat(cookie.assistantMessageId()).isEqualTo(assistantId);

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.in_flight);
        assertThat(assistant.getParentMessageId()).isEqualTo(cookie.userMessageId());
        assertThat(output).doesNotContain("HHH90032022");

        ChatMessage userMessage =
                chatMessageRepository.findById(cookie.userMessageId()).orElseThrow();
        assertThat(userMessage.getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(userMessage.getParts().get(0).path("text").asString()).isEqualTo("hello mentor");
    }

    @Test
    void persistInFlight_honorsClientUserMessageId() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID clientUserId = UUID.randomUUID();
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello mentor", assistantId, clientUserId, admittedMentorConfig());
        assertThat(cookie.userMessageId()).isEqualTo(clientUserId);
        assertThat(chatMessageRepository.findById(clientUserId)).isPresent();
    }

    @Test
    void persistInFlight_secondCallThrows() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        persistence.persistInFlight(thread, "first", UUID.randomUUID(), null, admittedMentorConfig());
        assertThatThrownBy(() ->
                        persistence.persistInFlight(thread, "second", UUID.randomUUID(), null, admittedMentorConfig()))
                .isInstanceOf(TurnAlreadyInFlightException.class);
    }

    @Test
    void shouldRollBackTheNewParentWhenAnotherTurnAlreadyExists() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "first");
        persistence.persistInFlight(thread, "first", UUID.randomUUID(), null, admittedMentorConfig());
        UUID rejectedUserId = UUID.randomUUID();
        UUID rejectedAssistantId = UUID.randomUUID();

        assertThatThrownBy(() -> persistence.persistInFlight(
                        thread, "second", rejectedAssistantId, rejectedUserId, admittedMentorConfig()))
                .isInstanceOf(TurnAlreadyInFlightException.class);

        assertThat(chatMessageRepository.findById(rejectedUserId)).isEmpty();
        assertThat(chatMessageRepository.findById(rejectedAssistantId)).isEmpty();
    }

    @Test
    void persistInFlight_concurrentRace_theDbUniqueIndexAloneLetsExactlyOneWriterWin() throws Exception {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try {
            Callable<Object> attempt = () -> {
                ready.countDown();
                fire.await(5, TimeUnit.SECONDS);
                try {
                    return persistence.persistInFlight(thread, "race", UUID.randomUUID(), null, admittedMentorConfig());
                } catch (RuntimeException ex) {
                    return ex; // surface to caller for classification
                }
            };
            var fa = pool.submit(attempt);
            var fb = pool.submit(attempt);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            fire.countDown();
            Object resultA = fa.get(10, TimeUnit.SECONDS);
            Object resultB = fb.get(10, TimeUnit.SECONDS);

            int winners = 0;
            int conflicts = 0;
            for (Object r : List.of(resultA, resultB)) {
                if (r instanceof MentorTurnPersistence.TurnPersistenceCookie) {
                    winners++;
                } else if (r instanceof TurnAlreadyInFlightException) {
                    // Narrowed exception — production must translate DataIntegrityViolation into
                    // TurnAlreadyInFlight before the orchestrator sees it. A regression that
                    // drops the isInFlightUniqueViolation filter would leak the unwrapped type.
                    conflicts++;
                } else if (r instanceof Throwable t) {
                    throw new AssertionError("Unexpected exception type: " + t, t);
                }
            }
            assertThat(winners).as("exactly one writer succeeds").isEqualTo(1);
            assertThat(conflicts).as("exactly one writer 409s").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldAnswerAnInterruptedPromptAgainWithoutStoringItTwice() {
        FailedTurn failed = failedTurn("Plan issue 12");
        UUID retryId = UUID.randomUUID();

        MentorTurnPersistence.RetryAdmission retry = persistence.persistRetry(
                failed.thread(), failed.prompt(), failed.reply(), retryId, admittedMentorConfig());

        assertThat(retry.prompt()).isEqualTo("Plan issue 12");
        assertThat(retry.cookie().userMessageId()).isEqualTo(failed.prompt());
        ChatMessage attempt = chatMessageRepository.findById(retryId).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(ChatMessage.Status.in_flight);
        assertThat(attempt.getParentMessageId()).isEqualTo(failed.prompt());
        ChatMessage interrupted = chatMessageRepository.findById(failed.reply()).orElseThrow();
        assertThat(interrupted.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        assertThat(interrupted.getMetadata().path("error").asString())
                .isEqualTo("Heph took too long to reply and stopped. Try again.");
        assertThat(messagesIn(failed.thread()))
                .extracting(ChatMessage::getRole)
                .containsExactly(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT, ChatMessage.Role.ASSISTANT);
    }

    @Test
    void shouldAdmitOneRetryPerFailedReply() {
        FailedTurn failed = failedTurn("Plan issue 12");
        UUID retryId = UUID.randomUUID();
        MentorTurnPersistence.RetryAdmission retry = persistence.persistRetry(
                failed.thread(), failed.prompt(), failed.reply(), retryId, admittedMentorConfig());

        assertThatThrownBy(() -> persistence.persistRetry(
                        failed.thread(), failed.prompt(), failed.reply(), UUID.randomUUID(), admittedMentorConfig()))
                .isInstanceOf(TurnAlreadyInFlightException.class);
        assertThatThrownBy(() -> persistence.persistRetry(
                        failed.thread(), failed.prompt(), retryId, UUID.randomUUID(), admittedMentorConfig()))
                .isInstanceOf(TurnAlreadyInFlightException.class);

        completeWithText(retry.cookie(), "Here is a plan.");

        assertThatThrownBy(() -> persistence.persistRetry(
                        failed.thread(), failed.prompt(), failed.reply(), UUID.randomUUID(), admittedMentorConfig()))
                .isInstanceOf(MentorRetryRejectedException.class)
                .hasMessage(MentorRetryRejectedException.SUPERSEDED);
        assertThatThrownBy(() -> persistence.persistRetry(
                        failed.thread(), failed.prompt(), retryId, UUID.randomUUID(), admittedMentorConfig()))
                .isInstanceOf(MentorRetryRejectedException.class)
                .hasMessage(MentorRetryRejectedException.SUPERSEDED);
        // An ordinary resubmit of the stored prompt is still a replay.
        assertThatThrownBy(() -> persistence.persistInFlight(
                        failed.thread(), "Plan issue 12", UUID.randomUUID(), failed.prompt(), admittedMentorConfig()))
                .isInstanceOf(TurnAlreadyInFlightException.class);
        assertThat(messagesIn(failed.thread())).hasSize(3);
    }

    @Test
    void shouldLetOneOfTwoConcurrentRetriesRun() throws Exception {
        FailedTurn failed = failedTurn("Plan issue 12");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try {
            Callable<Object> attempt = () -> {
                ready.countDown();
                fire.await(5, TimeUnit.SECONDS);
                try {
                    return persistence.persistRetry(
                            failed.thread(),
                            failed.prompt(),
                            failed.reply(),
                            UUID.randomUUID(),
                            admittedMentorConfig());
                } catch (RuntimeException ex) {
                    return ex;
                }
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            ready.await(5, TimeUnit.SECONDS);
            fire.countDown();
            List<Object> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes)
                    .filteredOn(MentorTurnPersistence.RetryAdmission.class::isInstance)
                    .hasSize(1);
            assertThat(outcomes)
                    .filteredOn(TurnAlreadyInFlightException.class::isInstance)
                    .hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(messagesIn(failed.thread())).hasSize(3);
    }

    @Test
    void shouldRefuseARetryThatNamesNoFailedReplyOfThisPrompt() {
        FailedTurn failed = failedTurn("Plan issue 12");
        FailedTurn elsewhere = failedTurn("Another thread");

        assertThatThrownBy(() -> persistence.persistRetry(
                        failed.thread(),
                        elsewhere.prompt(),
                        elsewhere.reply(),
                        UUID.randomUUID(),
                        admittedMentorConfig()))
                .hasMessage(MentorRetryRejectedException.NOT_RETRYABLE);
        assertThatThrownBy(() -> persistence.persistRetry(
                        failed.thread(), UUID.randomUUID(), failed.reply(), UUID.randomUUID(), admittedMentorConfig()))
                .hasMessage(MentorRetryRejectedException.NOT_RETRYABLE);
        assertThatThrownBy(() -> persistence.persistRetry(
                        failed.thread(), failed.prompt(), failed.prompt(), UUID.randomUUID(), admittedMentorConfig()))
                .hasMessage(MentorRetryRejectedException.NOT_RETRYABLE);
        assertThat(messagesIn(failed.thread())).hasSize(2);
        assertThat(messagesIn(elsewhere.thread())).hasSize(2);
    }

    @Test
    void shouldRefuseARetryOfAnEarlierPromptOnceTheConversationMovedOn() {
        FailedTurn failed = failedTurn("Plan issue 12");
        completeWithText(
                persistence.persistInFlight(
                        failed.thread(), "And issue 13?", UUID.randomUUID(), UUID.randomUUID(), admittedMentorConfig()),
                "Issue 13 needs a plan too.");

        assertThatThrownBy(() -> persistence.persistRetry(
                        failed.thread(), failed.prompt(), failed.reply(), UUID.randomUUID(), admittedMentorConfig()))
                .hasMessage(MentorRetryRejectedException.NOT_RETRYABLE);
        assertThat(messagesIn(failed.thread())).hasSize(4);
    }

    @Test
    void shouldRefuseAnOlderRetryThatWaitedWhileItsReplyWasAnsweredElsewhere() throws Exception {
        FailedTurn failed = failedTurn("Plan issue 12");

        Object waiter = whileAnotherAdmissionHolds(
                failed.thread(),
                () -> message(failed.thread(), ChatMessage.Role.ASSISTANT, failed.prompt()),
                () -> persistence.persistRetry(
                        failed.thread(), failed.prompt(), failed.reply(), UUID.randomUUID(), admittedMentorConfig()));

        assertThat(waiter)
                .isInstanceOfSatisfying(
                        MentorRetryRejectedException.class,
                        rejected ->
                                assertThat(rejected.getMessage()).isEqualTo(MentorRetryRejectedException.SUPERSEDED));
        assertThat(messagesIn(failed.thread())).hasSize(3);
    }

    @Test
    void shouldRefuseAnOlderRetryThatWaitedWhileANewerPromptWasAnswered() throws Exception {
        FailedTurn failed = failedTurn("Plan issue 12");

        Object waiter = whileAnotherAdmissionHolds(
                failed.thread(),
                () -> message(
                        failed.thread(),
                        ChatMessage.Role.ASSISTANT,
                        message(failed.thread(), ChatMessage.Role.USER, null)),
                () -> persistence.persistRetry(
                        failed.thread(), failed.prompt(), failed.reply(), UUID.randomUUID(), admittedMentorConfig()));

        assertThat(waiter)
                .isInstanceOfSatisfying(
                        MentorRetryRejectedException.class,
                        rejected -> assertThat(rejected.getMessage())
                                .isEqualTo(MentorRetryRejectedException.NOT_RETRYABLE));
        assertThat(messagesIn(failed.thread())).hasSize(4);
    }

    @Test
    void shouldAdmitANewPromptOnlyAfterTheThreadsOtherAdmissionCommits() throws Exception {
        FailedTurn failed = failedTurn("Plan issue 12");

        Object waiter = whileAnotherAdmissionHolds(
                failed.thread(),
                () -> failed.prompt(),
                () -> persistence.persistInFlight(
                        failed.thread(),
                        "And issue 13?",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        admittedMentorConfig()));

        assertThat(waiter).isInstanceOf(MentorTurnPersistence.TurnPersistenceCookie.class);
    }

    /**
     * Holds {@code thread}'s admission lock on another connection while {@code admission} writes, starts {@code
     * waiter}, requires it to block on that lock, then commits and returns what the waiter produced.
     */
    private Object whileAnotherAdmissionHolds(ChatThread thread, Callable<UUID> admission, Callable<Object> waiter)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                assertThat(chatThreadRepository.lockForTurnAdmission(thread.getId(), workspace.getId()))
                        .isPresent();
                try {
                    admission.call();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                holding.countDown();
                awaitQuietly(release);
            }));
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();
            var waiting = pool.submit(() -> {
                try {
                    return waiter.call();
                } catch (RuntimeException ex) {
                    return ex;
                }
            });
            awaitAWaitingLock();
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            return waiting.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private UUID message(ChatThread thread, ChatMessage.Role role, @Nullable UUID parent) {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setThread(thread);
        message.setRole(role);
        if (parent != null) {
            message.setParentMessage(chatMessageRepository.getReferenceById(parent));
        }
        message.setParts(NODES.arrayNode());
        message.setStatus(ChatMessage.Status.completed);
        return chatMessageRepository.saveAndFlush(message).getId();
    }

    private void awaitAWaitingLock() throws Exception {
        Instant deadline = Instant.now().plusSeconds(10);
        while (true) {
            try (var connection = dataSource.getConnection();
                    var statement = connection.createStatement();
                    var waiting = statement.executeQuery("SELECT count(*) FROM pg_locks WHERE NOT granted")) {
                waiting.next();
                if (waiting.getLong(1) > 0) return;
            }
            assertThat(Instant.now())
                    .as("the second admission never waited for the first")
                    .isBefore(deadline);
            Thread.sleep(20);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void shouldShowTheRetriedAnswerInPlaceOfTheInterruptedOneAfterReload() {
        FailedTurn failed = failedTurn("Plan issue 12");
        UUID retryId = UUID.randomUUID();
        completeWithText(
                persistence
                        .persistRetry(failed.thread(), failed.prompt(), failed.reply(), retryId, admittedMentorConfig())
                        .cookie(),
                "Here is a plan.");

        MentorTurnAccount.set(accountId);
        try {
            assertThat(chatThreadService
                            .loadOwnedThreadDetail(
                                    workspace.getId(), failed.thread().getId())
                            .messages())
                    .extracting(ChatMessageDTO::id)
                    .containsExactly(failed.prompt(), retryId);
        } finally {
            MentorTurnAccount.clear();
        }
    }

    private record FailedTurn(ChatThread thread, UUID prompt, UUID reply) {}

    /** A prompt whose only reply the watchdog interrupted before it wrote anything. */
    private FailedTurn failedTurn(String prompt) {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, prompt);
        UUID promptId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, prompt, replyId, promptId, admittedMentorConfig());
        persistence.interrupt(
                cookie,
                new TranslatorState(replyId),
                new IllegalStateException("Heph took too long to reply and stopped. Try again."));
        return new FailedTurn(thread, promptId, replyId);
    }

    private void completeWithText(MentorTurnPersistence.TurnPersistenceCookie cookie, String text) {
        TranslatorState state = new TranslatorState(cookie.assistantMessageId());
        state.openTextBlock("text-0");
        state.appendText(text);
        state.closeTextBlock();
        assertThat(persistence.complete(
                        cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null)))
                .isPresent();
    }

    private List<ChatMessage> messagesIn(ChatThread thread) {
        return chatMessageRepository.findAll().stream()
                .filter(message -> thread.getId().equals(message.getThread().getId()))
                .toList();
    }

    @Test
    void complete_writesCompletedRow() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());

        TranslatorState state = new TranslatorState(assistantId);
        state.observeModel("openai/gpt-oss-120b");
        ObjectNode usage = NODES.objectNode();
        usage.put("input", 123).put("output", 45);
        state.observeUsage(usage);
        // Open + close a text block so partsSnapshot() has something to write.
        state.openTextBlock("text-0");
        state.appendText("Hello there!");
        state.closeTextBlock();

        UIMessageChunk.MessageMetadata finishMeta = new UIMessageChunk.MessageMetadata(
                "openai/gpt-oss-120b",
                new UIMessageChunk.MessageMetadata.Usage(123, 45, null, null, 168),
                /* costUsd */ null);
        UIMessageChunk.Finish finish = new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, finishMeta);

        assertThat(persistence.complete(cookie, state, finish)).isPresent();

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.completed);
        JsonNode meta = assistant.getMetadata();
        assertThat(meta.path("finishReason").asString()).isEqualTo("stop");
        assertThat(meta.path("model").asString()).isEqualTo("openai/gpt-oss-120b");
        // Nested wire shape — must match UIMessageChunk.MessageMetadata + webapp MessageMetadata
        // so a rehydrated thread renders identically to the live stream.
        assertThat(meta.path("usage").path("input").asLong()).isEqualTo(123);
        assertThat(meta.path("usage").path("output").asLong()).isEqualTo(45);
        assertThat(meta.path("usage").path("totalTokens").asLong()).isEqualTo(168);
        assertThat(meta.has("inputTokens")).as("flat keys retired").isFalse();
        assertThat(assistant.getParts().isArray()).isTrue();
        assertThat(assistant.getParts().get(0).path("text").asString()).isEqualTo("Hello there!");
    }

    @Test
    void complete_persistsProviderTotalTokensWhenItDivergesFromInputPlusOutput() {
        // A provider-reported totalTokens that includes cache tokens legitimately exceeds input+output. The
        // persisted block must round-trip the WIRE total unchanged (single source of truth), NOT re-derive
        // it as input+output — otherwise a rehydrated thread renders a different token count than the stream.
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hi");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hi", assistantId, null, admittedMentorConfig());

        TranslatorState state = new TranslatorState(assistantId);
        state.observeModel("openai/gpt-oss-120b");
        ObjectNode usage = NODES.objectNode();
        usage.put("input", 100).put("output", 50);
        state.observeUsage(usage);
        state.openTextBlock("text-0");
        state.appendText("ok");
        state.closeTextBlock();

        // Wire total = 200 ≠ input+output (150): provider counted 50 cache tokens on top.
        UIMessageChunk.MessageMetadata finishMeta = new UIMessageChunk.MessageMetadata(
                "openai/gpt-oss-120b",
                new UIMessageChunk.MessageMetadata.Usage(100, 50, 50, null, 200),
                /* costUsd */ null);
        persistence.complete(cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, finishMeta));

        JsonNode meta =
                chatMessageRepository.findById(assistantId).orElseThrow().getMetadata();
        assertThat(meta.path("usage").path("input").asLong()).isEqualTo(100);
        assertThat(meta.path("usage").path("output").asLong()).isEqualTo(50);
        assertThat(meta.path("usage").path("totalTokens").asLong()).isEqualTo(200);
    }

    @Test
    void complete_storesSessionJsonlByteIdentically() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = persistence.ensureThread(workspace.getId(), threadId, accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());

        // 3-byte and 4-byte UTF-8 characters exercise any layer that round-trips through String.
        byte[] expectedBytes = """
            {"type":"user_message","text":"hello €"}
            {"type":"assistant_message","text":"hi 😀","stopReason":"stop"}
            """.getBytes(StandardCharsets.UTF_8);

        TranslatorState state = new TranslatorState(assistantId);
        state.observeSessionJsonl(expectedBytes);
        persistence.complete(cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));

        assertThat(chatThreadRepository.findSessionJsonl(threadId))
                .as("byte-identical: any re-encoding kills prompt-cache prefix matching")
                .contains(expectedBytes);
    }

    @Test
    void complete_storesSessionJsonlAboveToastThreshold() {
        // Postgres TOAST threshold is ~2KB, so a 1MB payload exercises out-of-line storage and
        // detoast on read — the path that would surface an encoding/transport regression in JDBC
        // stream handling.
        UUID threadId = UUID.randomUUID();
        ChatThread thread = persistence.ensureThread(workspace.getId(), threadId, accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());

        byte[] bigBytes = new byte[1024 * 1024]; // 1 MiB
        // Pattern with a stable header + repeating non-zero filler so a partial-read regression
        // is detectable by a single-byte check anywhere in the array.
        byte[] header = "{\"type\":\"user_message\",\"text\":\"".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(header, 0, bigBytes, 0, header.length);
        for (int i = header.length; i < bigBytes.length - 3; i++) bigBytes[i] = (byte) ('a' + (i % 26));
        bigBytes[bigBytes.length - 3] = '"';
        bigBytes[bigBytes.length - 2] = '}';
        bigBytes[bigBytes.length - 1] = '\n';

        TranslatorState state = new TranslatorState(assistantId);
        state.observeSessionJsonl(bigBytes);
        persistence.complete(cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));

        byte[] readBack = chatThreadRepository.findSessionJsonl(threadId).orElseThrow();
        assertThat(readBack).as("1MB TOAST round-trip preserves every byte").isEqualTo(bigBytes);
    }

    @Test
    void complete_withoutSessionJsonl_preservesPriorTurn() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = persistence.ensureThread(workspace.getId(), threadId, accountId, "hello");

        byte[] priorBytes = "{\"prior\":\"turn\"}\n".getBytes(StandardCharsets.UTF_8);
        chatThreadRepository.updateSessionJsonl(threadId, priorBytes, sessionVersion(threadId));

        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "follow-up", assistantId, null, admittedMentorConfig());
        persistence.complete(
                cookie,
                new TranslatorState(assistantId),
                new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));

        assertThat(chatThreadRepository.findSessionJsonl(threadId)).contains(priorBytes);
    }

    @Test
    void shouldNotStoreARunningTurnsSessionOverAJournalThatPersonErasureCleared() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID threadId = thread.getId();
        chatThreadRepository.updateSessionJsonl(
                threadId, "{\"type\":\"earlier\"}\n".getBytes(StandardCharsets.UTF_8), sessionVersion(threadId));
        UUID runningId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie running =
                persistence.persistInFlight(thread, "hello", runningId, null, admittedMentorConfig());

        long colleagueId = Objects.requireNonNull(
                accountRepository.save(new Account("Erased Colleague")).getId());
        var membership = new Membership();
        membership.setId(new Membership.Id(workspace.getId(), colleagueId));
        membership.setWorkspace(workspace);
        memberships.saveAndFlush(membership);
        var journals = personDataRegistry.stores().stream()
                .filter(store -> store.store().equals("chat_thread_runtime_journal"))
                .findFirst()
                .orElseThrow();
        var selectionScope = new PersonScope(colleagueId, List.of(), List.of());
        var selection = journals.select(selectionScope);
        assertThat(selection.rows()).isNotEmpty();
        journals.erase(selection, selectionScope);

        TranslatorState warm = new TranslatorState(runningId);
        warm.observeSessionJsonl("{\"type\":\"warm\",\"context\":\"colleague\"}\n".getBytes(StandardCharsets.UTF_8));
        persistence.complete(running, warm, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));
        assertThat(chatThreadRepository.findSessionJsonl(threadId))
                .as("the runtime admitted before erasure cannot restore its session")
                .hasValueSatisfying(bytes -> assertThat(bytes).isEmpty());

        UUID nextId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie next =
                persistence.persistInFlight(thread, "again", nextId, null, admittedMentorConfig());
        byte[] fresh = "{\"type\":\"fresh\"}\n".getBytes(StandardCharsets.UTF_8);
        TranslatorState nextState = new TranslatorState(nextId);
        nextState.observeSessionJsonl(fresh);
        persistence.complete(next, nextState, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));
        assertThat(chatThreadRepository.findSessionJsonl(threadId)).contains(fresh);
    }

    private String sessionVersion(UUID threadId) {
        return chatThreadRepository.findSessionVersion(threadId).orElseThrow();
    }

    @Test
    void interrupt_writesInterruptedRow() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());

        persistence.interrupt(cookie, new TranslatorState(assistantId), new IllegalStateException("upstream timeout"));

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        assertThat(assistant.getMetadata().path("error").asString()).isEqualTo("upstream timeout");
    }

    @Test
    void interrupt_afterLlmCallStarted_writesUnverifiableLedgerEventWhenUsageIsMissing() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());
        TranslatorState state = new TranslatorState(assistantId);
        state.markLlmCallStarted();

        persistence.interrupt(cookie, state, new IllegalStateException("upstream disconnected"));

        var event = usageEventRepository.findAll().stream()
                .filter(row -> row.getSourceId().equals(assistantId))
                .findFirst();
        assertThat(event).isPresent();
        assertThat(event.orElseThrow().getPricingState()).isEqualTo(UsagePricingState.UNPRICED);
        assertThat(event.orElseThrow().getCostUsd()).isNull();
    }

    @Test
    void complete_cacheOnlyUsage_writesPricedLedgerEvent() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        LlmPriceSnapshot price = new LlmPriceSnapshot(
                UsageFundingSource.INSTANCE,
                UsagePricingState.PRICED,
                12L,
                null,
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("2"),
                new BigDecimal("3"));
        MentorLlmConfig config = new MentorLlmConfig(
                "openai-responses",
                "https://api.openai.com/v1",
                "test-model",
                null,
                null,
                null,
                UsageFundingSource.INSTANCE,
                1L,
                1L,
                null,
                price,
                false,
                600);
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, config);
        TranslatorState state = new TranslatorState(assistantId);
        state.markLlmCallStarted();
        ObjectNode usage = NODES.objectNode();
        usage.put("input", 0).put("output", 0).put("cacheRead", 500_000).put("cacheWrite", 0);
        state.observeUsage(usage);

        persistence.complete(cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));

        var event = usageEventRepository.findAll().stream()
                .filter(row -> row.getSourceId().equals(assistantId))
                .findFirst()
                .orElseThrow();
        assertThat(event.getPricingState()).isEqualTo(UsagePricingState.PRICED);
        assertThat(event.getCacheReadTokens()).isEqualTo(500_000);
        assertThat(event.getCostUsd()).isEqualByComparingTo("1.000000");
    }

    @ParameterizedTest(name = "summary calls recorded by the proxy: {0}")
    @ValueSource(ints = {2, 1})
    @DisplayName("a turn the watchdog ends after compacting keeps its checkpoint and bills every call the proxy saw")
    void shouldKeepTheCheckpointAndBillTheProxysCallsWhenACompactedTurnIsInterrupted(int summaryCalls) {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID threadId = thread.getId();
        chatThreadRepository.updateSessionJsonl(
                threadId, "{\"type\":\"before\"}\n".getBytes(StandardCharsets.UTF_8), sessionVersion(threadId));
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());
        // The runner saw one ordinary call; Pi reports its summaries as one total, or nothing when the second
        // of a split summary failed. The proxy recorded each call.
        TranslatorState state = new TranslatorState(assistantId);
        state.markLlmCallStarted();
        state.completeUsage(NODES.objectNode().put("input", 90_000).put("output", 40));
        state.markCompactionAttempted();
        accumulateProxyCall(assistantId, 90_000, 40, 0, 0, 0);
        for (int i = 0; i < summaryCalls; i++) {
            accumulateProxyCall(assistantId, 20_000, 3_000, 0, 0, 0);
        }
        byte[] checkpoint = "{\"type\":\"compaction\"}\n".getBytes(StandardCharsets.UTF_8);
        state.observeSessionJsonl(checkpoint);

        persistence.interrupt(
                cookie, state, new IllegalStateException("Heph took too long to reply and stopped. Try again."));

        assertThat(chatMessageRepository.findById(assistantId).orElseThrow().getStatus())
                .isEqualTo(ChatMessage.Status.interrupted);
        assertThat(chatThreadRepository.findSessionJsonl(threadId)).contains(checkpoint);
        var event = usageEventRepository.findAll().stream()
                .filter(row -> row.getSourceId().equals(assistantId))
                .findFirst()
                .orElseThrow();
        assertThat(event.getTotalCalls()).isEqualTo(1 + summaryCalls);
        assertThat(event.getInputTokens()).isEqualTo(90_000 + 20_000L * summaryCalls);
        assertThat(event.getOutputTokens()).isEqualTo(40 + 3_000L * summaryCalls);
        assertThat(event.getUsageProvenance()).isEqualTo(UsageProvenance.PROXY);
    }

    @ParameterizedTest(name = "summary calls recorded by the proxy: {0}")
    @ValueSource(ints = {2, 1})
    @DisplayName("a compacted turn's live Finish reports the usage and cost its row and ledger record")
    void shouldReportTheRecordedUsageOnTheFinishWhenACompactedTurnCompletes(int summaryCalls) {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        LlmPriceSnapshot price = new LlmPriceSnapshot(
                UsageFundingSource.INSTANCE,
                UsagePricingState.PRICED,
                12L,
                null,
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("2"),
                new BigDecimal("3"));
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, pricedMentorConfig(price));
        TranslatorState state = new TranslatorState(assistantId);
        state.bindAdmission("test-model", price);
        state.markLlmCallStarted();
        // The runner's own report covers the ordinary call only, with its provider total.
        state.completeUsage(
                NODES.objectNode().put("input", 90_000).put("output", 40).put("cacheRead", 1_000));
        state.markCompactionAttempted();
        accumulateProxyCall(assistantId, 90_000, 40, 0, 1_000, 0);
        for (int i = 0; i < summaryCalls; i++) {
            accumulateProxyCall(assistantId, 20_000, 3_000, 0, 500, 100);
        }
        UIMessageChunk.Finish streamed = new UIMessageChunk.Finish(
                UIMessageChunk.FinishReason.STOP,
                new UIMessageChunk.MessageMetadata(
                        "test-model", new UIMessageChunk.MessageMetadata.Usage(90_000, 40, 1_000, 0, 91_040), null));

        UIMessageChunk.Finish sent =
                persistence.complete(cookie, state, streamed).orElseThrow();

        int input = 90_000 + 20_000 * summaryCalls;
        int output = 40 + 3_000 * summaryCalls;
        int cacheRead = 1_000 + 500 * summaryCalls;
        int cacheWrite = 100 * summaryCalls;
        var recorded = new UIMessageChunk.MessageMetadata.Usage(
                input, output, cacheRead, cacheWrite, input + output + cacheRead + cacheWrite);
        var metadata = Objects.requireNonNull(sent.messageMetadata());
        assertThat(metadata.usage()).isEqualTo(recorded);
        double costUsd = Objects.requireNonNull(metadata.costUsd());
        JsonNode row = chatMessageRepository.findById(assistantId).orElseThrow().getMetadata();
        assertThat(row.path("usage").path("input").asInt()).isEqualTo(input);
        assertThat(row.path("usage").path("output").asInt()).isEqualTo(output);
        assertThat(row.path("usage").path("cacheRead").asInt()).isEqualTo(cacheRead);
        assertThat(row.path("usage").path("cacheWrite").asInt()).isEqualTo(cacheWrite);
        assertThat(row.path("usage").path("totalTokens").asInt()).isEqualTo(input + output + cacheRead + cacheWrite);
        assertThat(row.path("costUsd").asDouble()).isEqualTo(costUsd);
        var event = usageEventRepository.findAll().stream()
                .filter(e -> e.getSourceId().equals(assistantId))
                .findFirst()
                .orElseThrow();
        assertThat(event.getTotalCalls()).isEqualTo(1 + summaryCalls);
        assertThat(event.getInputTokens()).isEqualTo(input);
        assertThat(event.getCostUsd()).isEqualByComparingTo(BigDecimal.valueOf(costUsd));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldAccountForEarlierCallsWhenTheNativeRetrySettles(boolean recovered) {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        LlmPriceSnapshot price = new LlmPriceSnapshot(
                UsageFundingSource.INSTANCE,
                UsagePricingState.PRICED,
                12L,
                null,
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("2"),
                new BigDecimal("3"));
        var cookie = persistence.persistInFlight(thread, "hello", assistantId, null, pricedMentorConfig(price));
        TranslatorState state = new TranslatorState(assistantId);
        state.bindAdmission("test-model", price);
        state.markLlmCallStarted();
        state.completeUsage(NODES.objectNode().put("input", 1_000).put("output", 40));
        state.completeUsage(NODES.objectNode().put("input", 50).put("output", 2));
        var translator = new PiEventToUiChunkTranslator();
        translator.translate(NODES.objectNode().put("type", "auto_retry_start"), state);
        int finalInput = recovered ? 200 : 300;
        int finalOutput = recovered ? 20 : 30;
        ObjectNode end = NODES.objectNode().put("type", "agent_end");
        ObjectNode assistant = end.putArray("messages").addObject();
        assistant.put("role", "assistant").put("stopReason", recovered ? "stop" : "error");
        assistant.putArray("content");
        assistant.putObject("usage").put("input", finalInput).put("output", finalOutput);
        var chunks = translator.translate(end, state);
        accumulateProxyCall(assistantId, 1_000, 40, 0, 0, 0);
        accumulateProxyCall(assistantId, 50, 2, 0, 0, 0);
        accumulateProxyCall(assistantId, finalInput, finalOutput, 0, 0, 0);

        if (recovered) {
            var sent = persistence
                    .complete(cookie, state, (UIMessageChunk.Finish) chunks.get(0))
                    .orElseThrow();
            var metadata = Objects.requireNonNull(sent.messageMetadata());
            var usage = Objects.requireNonNull(metadata.usage());
            assertThat(usage.input()).isEqualTo(1_050 + finalInput);
            assertThat(usage.output()).isEqualTo(42 + finalOutput);
            JsonNode row =
                    chatMessageRepository.findById(assistantId).orElseThrow().getMetadata();
            assertThat(row.path("usage").path("input").asInt()).isEqualTo(usage.input());
            assertThat(row.path("costUsd").asDouble()).isEqualTo(Objects.requireNonNull(metadata.costUsd()));
        } else {
            persistence.interrupt(cookie, state, new IllegalStateException("The final retry failed."));
            assertThat(chatMessageRepository.findById(assistantId).orElseThrow().getStatus())
                    .isEqualTo(ChatMessage.Status.interrupted);
        }
        var event = usageEventRepository.findAll().stream()
                .filter(row -> row.getSourceId().equals(assistantId))
                .findFirst()
                .orElseThrow();
        assertThat(event.getInputTokens()).isEqualTo(1_050 + finalInput);
        assertThat(event.getOutputTokens()).isEqualTo(42 + finalOutput);
        assertThat(event.getTotalCalls()).isEqualTo(3);
        assertThat(event.getUsageProvenance()).isEqualTo(UsageProvenance.PROXY);
        assertThat(event.getCostUsd()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldLeaveRetryUsageUnverifiableWhenItsProxyAccountIsMissing(boolean recovered) {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        LlmPriceSnapshot price = new LlmPriceSnapshot(
                UsageFundingSource.INSTANCE,
                UsagePricingState.PRICED,
                12L,
                null,
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("2"),
                new BigDecimal("3"));
        var cookie = persistence.persistInFlight(thread, "hello", assistantId, null, pricedMentorConfig(price));
        TranslatorState state = new TranslatorState(assistantId);
        state.bindAdmission("test-model", price);
        state.markLlmCallStarted();
        state.completeUsage(NODES.objectNode().put("input", 1_000).put("output", 40));
        new PiEventToUiChunkTranslator().translate(NODES.objectNode().put("type", "auto_retry_start"), state);
        state.replaceCompletedUsage(List.of(NODES.objectNode().put("input", 200).put("output", 20)));

        if (recovered) {
            var streamed = new UIMessageChunk.Finish(
                    UIMessageChunk.FinishReason.STOP,
                    new UIMessageChunk.MessageMetadata(
                            "test-model", new UIMessageChunk.MessageMetadata.Usage(200, 20, 0, 0, 220), null));
            var sent = persistence.complete(cookie, state, streamed).orElseThrow();
            var metadata = Objects.requireNonNull(sent.messageMetadata());
            assertThat(metadata.usage()).isNull();
            assertThat(metadata.costUsd()).isNull();
            assertThat(chatMessageRepository
                            .findById(assistantId)
                            .orElseThrow()
                            .getMetadata()
                            .has("usage"))
                    .isFalse();
        } else {
            persistence.interrupt(cookie, state, new IllegalStateException("The final retry failed."));
        }
        var event = usageEventRepository.findAll().stream()
                .filter(row -> row.getSourceId().equals(assistantId))
                .findFirst()
                .orElseThrow();
        assertThat(event.getUsageProvenance()).isEqualTo(UsageProvenance.NONE);
        assertThat(event.getPricingState()).isEqualTo(UsagePricingState.UNPRICED);
        assertThat(event.getCostUsd()).isNull();
    }

    private static MentorLlmConfig pricedMentorConfig(LlmPriceSnapshot price) {
        return new MentorLlmConfig(
                "openai-responses",
                "https://api.openai.com/v1",
                "test-model",
                null,
                null,
                null,
                UsageFundingSource.INSTANCE,
                1L,
                1L,
                null,
                price,
                false,
                600);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
            value = {
                "completed, higher_normalized_prompt_and_output, 1000, 40, 900, 42, 150, 50, PROXY, VERIFIED, 0.010290",
                "interrupted, higher_normalized_prompt_and_output, 1000, 40, 900, 42, 150, 50, PROXY, VERIFIED,"
                        + " 0.010290",
                "completed, normalized_tie_keeps_runner, 1000, 40, 900, 40, 75, 25, RUNNER, VERIFIED, 0.010800",
                "interrupted, normalized_tie_keeps_runner, 1000, 40, 900, 40, 75, 25, RUNNER, VERIFIED, 0.010800",
                "completed, lower_keeps_runner, 1000, 40, 800, 30, 75, 25, RUNNER, VERIFIED, 0.010800",
                "interrupted, lower_keeps_runner, 1000, 40, 800, 30, 75, 25, RUNNER, VERIFIED, 0.010800",
                "completed, incomparable_preserves_runner_selection_only, 1000, 40, 900, 39, 150, 50, RUNNER,"
                        + " UNVERIFIABLE, unpriced",
                "interrupted, incomparable_preserves_runner_selection_only, 1000, 40, 900, 39, 150, 50, RUNNER,"
                        + " UNVERIFIABLE, unpriced",
                "completed, no_tokens_remains_unpriced, 0, 0, 0, 0, 0, 0, NONE, UNVERIFIABLE, unpriced",
                "interrupted, no_tokens_remains_unpriced, 0, 0, 0, 0, 0, 0, NONE, UNVERIFIABLE, unpriced"
            },
            nullValues = "unpriced")
    void shouldSelectTheWholeProxyVectorOnlyWhenNormalizedPromptAndOutputDominateOnAnOrdinaryTurn(
            String terminal,
            String scenario,
            int runnerInput,
            int runnerOutput,
            int proxyInput,
            int proxyOutput,
            int proxyCacheRead,
            int proxyCacheWrite,
            UsageProvenance expectedProvenance,
            MeasurementVerification expectedVerification,
            @Nullable String expectedCost) {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        LlmPriceSnapshot price = new LlmPriceSnapshot(
                UsageFundingSource.INSTANCE,
                UsagePricingState.PRICED,
                12L,
                null,
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("2"),
                new BigDecimal("3"));
        var cookie = persistence.persistInFlight(thread, "hello", assistantId, null, pricedMentorConfig(price));
        TranslatorState state = new TranslatorState(assistantId);
        state.bindAdmission("test-model", price);
        state.markLlmCallStarted();
        state.completeUsage(NODES.objectNode()
                .put("input", runnerInput)
                .put("output", runnerOutput)
                .put("cacheRead", 0)
                .put("cacheWrite", 0));
        int proxyReasoning = proxyOutput > 0 ? 7 : 0;
        accumulateProxyCall(assistantId, proxyInput, proxyOutput, proxyReasoning, proxyCacheRead, proxyCacheWrite);

        boolean proxySelected = expectedProvenance == UsageProvenance.PROXY;
        int input = proxySelected ? proxyInput : runnerInput;
        int output = proxySelected ? proxyOutput : runnerOutput;
        int cacheRead = proxySelected ? proxyCacheRead : 0;
        int cacheWrite = proxySelected ? proxyCacheWrite : 0;
        int total = input + output + cacheRead + cacheWrite;
        var selected = new UIMessageChunk.MessageMetadata.Usage(input, output, cacheRead, cacheWrite, total);
        if (terminal.equals("completed")) {
            var runnerUsage = runnerInput == 0 && runnerOutput == 0
                    ? null
                    : new UIMessageChunk.MessageMetadata.Usage(
                            runnerInput, runnerOutput, 0, 0, runnerInput + runnerOutput);
            var streamed = new UIMessageChunk.Finish(
                    UIMessageChunk.FinishReason.STOP,
                    new UIMessageChunk.MessageMetadata("test-model", runnerUsage, null));
            var sent = persistence.complete(cookie, state, streamed).orElseThrow();
            var metadata = Objects.requireNonNull(sent.messageMetadata());
            assertThat(sent.finishReason()).isEqualTo(UIMessageChunk.FinishReason.STOP);
            assertThat(metadata.model()).isEqualTo("test-model");
            if (expectedProvenance == UsageProvenance.NONE) {
                assertThat(metadata.usage()).isNull();
            } else {
                assertThat(metadata.usage()).isEqualTo(selected);
            }
            if (expectedCost == null) {
                assertThat(metadata.costUsd()).isNull();
            } else {
                assertThat(BigDecimal.valueOf(Objects.requireNonNull(metadata.costUsd())))
                        .isEqualByComparingTo(expectedCost);
            }
        } else {
            persistence.interrupt(cookie, state, new IllegalStateException("upstream disconnected"));
        }

        var assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.valueOf(terminal));
        JsonNode metadata = assistant.getMetadata();
        assertThat(metadata.at("/llmAdmission/model").asString()).isEqualTo("test-model");
        assertThat(metadata.at("/llmAdmission/price/appliedPriceId").asString()).isEqualTo("12");
        if (terminal.equals("completed") && expectedProvenance != UsageProvenance.NONE) {
            assertThat(metadata.path("usage"))
                    .isEqualTo(NODES.objectNode()
                            .put("input", input)
                            .put("output", output)
                            .put("cacheRead", cacheRead)
                            .put("cacheWrite", cacheWrite)
                            .put("totalTokens", total));
            if (expectedCost == null) {
                assertThat(metadata.has("costUsd")).isFalse();
            } else {
                assertThat(BigDecimal.valueOf(metadata.path("costUsd").asDouble()))
                        .isEqualByComparingTo(expectedCost);
            }
        } else {
            assertThat(metadata.has("usage")).isFalse();
            assertThat(metadata.has("costUsd")).isFalse();
        }
        if (terminal.equals("interrupted")) {
            assertThat(metadata.path("error").asString()).isEqualTo("upstream disconnected");
        }
        assertThat(usageEventRepository.findAll())
                .filteredOn(event -> event.getSourceId().equals(assistantId))
                .as("%s ledger entry", scenario)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getWorkspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.getModel()).isEqualTo("test-model");
                    assertThat(event.getInputTokens()).isEqualTo(input);
                    assertThat(event.getOutputTokens()).isEqualTo(output);
                    assertThat(event.getCacheReadTokens()).isEqualTo(cacheRead);
                    assertThat(event.getCacheWriteTokens()).isEqualTo(cacheWrite);
                    assertThat(event.getReasoningTokens()).isEqualTo(proxySelected ? proxyReasoning : 0);
                    assertThat(event.getTotalCalls()).isEqualTo(1);
                    assertThat(event.getUsageProvenance()).isEqualTo(expectedProvenance);
                    assertThat(event.getMeasurementVerification()).isEqualTo(expectedVerification);
                    assertThat(event.getFundingSource()).isEqualTo(UsageFundingSource.INSTANCE);
                    assertThat(event.getAppliedPriceId()).isEqualTo(12L);
                    assertThat(event.getAppliedPer1mInputUsd()).isEqualByComparingTo("10");
                    assertThat(event.getAppliedPer1mOutputUsd()).isEqualByComparingTo("20");
                    assertThat(event.getAppliedPer1mCacheReadUsd()).isEqualByComparingTo("2");
                    assertThat(event.getAppliedPer1mCacheWriteUsd()).isEqualByComparingTo("3");
                    if (expectedCost == null) {
                        assertThat(event.getPricingState()).isEqualTo(UsagePricingState.UNPRICED);
                        assertThat(event.getCostUsd()).isNull();
                    } else {
                        assertThat(event.getPricingState()).isEqualTo(UsagePricingState.PRICED);
                        assertThat(event.getCostUsd()).isEqualByComparingTo(expectedCost);
                    }
                });
    }

    @Test
    @DisplayName("a turn that never compacted bills the runner's own report, not the proxy's as well")
    void shouldBillTheRunnersReportWhenTheTurnDidNotCompact() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());
        TranslatorState state = new TranslatorState(assistantId);
        state.markLlmCallStarted();
        state.completeUsage(NODES.objectNode().put("input", 1_000).put("output", 40));
        accumulateProxyCall(assistantId, 1_000, 40, 0, 0, 0);

        persistence.interrupt(cookie, state, new IllegalStateException("upstream timeout"));

        var event = usageEventRepository.findAll().stream()
                .filter(row -> row.getSourceId().equals(assistantId))
                .findFirst()
                .orElseThrow();
        assertThat(event.getTotalCalls()).isEqualTo(1);
        assertThat(event.getInputTokens()).isEqualTo(1_000);
        assertThat(event.getUsageProvenance()).isEqualTo(UsageProvenance.RUNNER);
    }

    @Test
    void shouldBillEarlierUsageWhenTheFinalRetryReportsNoTokens() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());
        TranslatorState state = new TranslatorState(assistantId);
        state.markLlmCallStarted();
        state.completeUsage(NODES.objectNode().put("input", 1_000).put("output", 40));
        accumulateProxyCall(assistantId, 1_000, 40, 0, 0, 0);
        ObjectNode end = NODES.objectNode().put("type", "agent_end");
        ObjectNode failed = end.putArray("messages").addObject();
        failed.put("role", "assistant").put("stopReason", "error");
        failed.putArray("content");
        failed.putObject("usage").put("input", 0).put("output", 0);

        var chunks = new PiEventToUiChunkTranslator().translate(end, state);
        assertThat(chunks).singleElement().isInstanceOf(UIMessageChunk.TurnError.class);
        persistence.interrupt(
                cookie, state, new IllegalStateException(((UIMessageChunk.TurnError) chunks.get(0)).errorText()));

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        var event = usageEventRepository.findAll().stream()
                .filter(row -> row.getSourceId().equals(assistantId))
                .findFirst()
                .orElseThrow();
        assertThat(event.getInputTokens()).isEqualTo(1_000);
        assertThat(event.getOutputTokens()).isEqualTo(40);
        assertThat(event.getTotalCalls()).isEqualTo(1);
        assertThat(event.getUsageProvenance()).isEqualTo(UsageProvenance.PROXY);
    }

    @Test
    void interrupt_beforeLlmCallStarted_doesNotInventAUsageEvent() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());

        persistence.interrupt(
                cookie, new TranslatorState(assistantId), new IllegalStateException("sandbox attach failed"));

        assertThat(usageEventRepository.findAll())
                .noneMatch(row -> row.getSourceId().equals(assistantId));
    }

    @Test
    void optimisticLocking_aLateFinaliseCannotOverwriteWhatTheReaperWrote() throws Exception {
        UUID assistantId = persistInFlightTurn("hello");

        // Simulate the in-flight runner: load a managed snapshot at the current version.
        ChatMessage stale = chatMessageRepository.findById(assistantId).orElseThrow();
        Long versionBefore = stale.getVersion();
        assertThat(versionBefore).isNotNull();

        setCreatedAt(assistantId, Instant.now().minus(Duration.ofMinutes(200)));
        reaperWithAnUnsafeWindow().reap();
        ChatMessage afterReaper = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(afterReaper.getVersion()).isEqualTo(versionBefore + 1L);
        assertThat(afterReaper.getStatus()).isEqualTo(ChatMessage.Status.interrupted);

        stale.setStatus(ChatMessage.Status.completed);
        assertThatThrownBy(() -> {
                    chatMessageRepository.saveAndFlush(stale);
                })
                .isInstanceOf(OptimisticLockingFailureException.class);

        ChatMessage finalState = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        assertThat(finalState.getMetadata().path("error").asString()).isEqualTo("server restart");
    }

    @Test
    void complete_leavesATurnTheReaperAlreadyInterruptedAsItWas() throws Exception {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());
        setCreatedAt(assistantId, Instant.now().minus(Duration.ofMinutes(200)));
        reaperWithAnUnsafeWindow().reap();
        TranslatorState state = new TranslatorState(assistantId);
        state.openTextBlock("text-0");
        state.appendText("Hello there!");
        state.closeTextBlock();

        var completed =
                persistence.complete(cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));

        assertThat(completed).isEmpty();
        ChatMessage row = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        assertThat(row.getMetadata().path("error").asString()).isEqualTo("server restart");
        assertThat(row.getParts()).isEmpty();
    }

    @Test
    void interrupt_neverDowngradesACompletedTurn() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie =
                persistence.persistInFlight(thread, "hello", assistantId, null, admittedMentorConfig());
        TranslatorState state = new TranslatorState(assistantId);
        assertThat(persistence.complete(
                        cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null)))
                .isPresent();

        persistence.interrupt(cookie, state, new IllegalStateException("late loss"));

        ChatMessage row = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(ChatMessage.Status.completed);
        assertThat(row.getMetadata().has("error")).isFalse();
    }

    @Test
    void accountingReaper_neverSelectsLegitimateTurnsAndAccountsTrulyStaleTurnOnce() throws Exception {
        UUID tenMinuteTurn = persistInFlightTurn("ten-minute-turn");
        UUID maxDurationTurn = persistInFlightTurn("max-duration-turn");
        UUID staleTurn = persistInFlightTurn("stale-turn");
        Instant now = Instant.now();
        setCreatedAt(tenMinuteTurn, now.minus(Duration.ofMinutes(10)));
        setCreatedAt(maxDurationTurn, now.minus(Duration.ofHours(3)));
        setCreatedAt(staleTurn, now.minus(Duration.ofMinutes(200)));

        MentorInFlightReaper sweeper = reaperWithAnUnsafeWindow();
        assertThat(sweeper.window()).isEqualTo(Duration.ofMinutes(190));
        sweeper.reap();
        sweeper.reap();

        assertThat(chatMessageRepository.findById(tenMinuteTurn).orElseThrow().getStatus())
                .isEqualTo(ChatMessage.Status.in_flight);
        assertThat(chatMessageRepository.findById(maxDurationTurn).orElseThrow().getStatus())
                .isEqualTo(ChatMessage.Status.in_flight);
        assertThat(chatMessageRepository.findById(staleTurn).orElseThrow().getStatus())
                .isEqualTo(ChatMessage.Status.interrupted);
        assertThat(usageEventRepository.findAll())
                .filteredOn(row -> row.getSourceId().equals(staleTurn))
                .hasSize(1);
    }

    @Test
    @DisplayName("a turn abandoned by a crashed worker is billed for the calls the proxy recorded")
    void accountingReaper_billsACrashedTurnFromItsProxyRecordedUsage() throws Exception {
        UUID crashedTurn = persistInFlightTurn("crashed-turn");
        accumulateProxyCall(crashedTurn, 40_000, 1_000, 250, 5_000, 1_000);
        accumulateProxyCall(crashedTurn, 60_000, 2_000, 250, 5_000, 2_000);
        setCreatedAt(crashedTurn, Instant.now().minus(Duration.ofMinutes(200)));

        reaperWithAnUnsafeWindow().reap();

        assertThat(chatMessageRepository.findById(crashedTurn).orElseThrow().getStatus())
                .isEqualTo(ChatMessage.Status.interrupted);
        var event = usageEventRepository.findAll().stream()
                .filter(row -> row.getSourceId().equals(crashedTurn))
                .findFirst()
                .orElseThrow();
        assertThat(event.getInputTokens()).isEqualTo(100_000);
        assertThat(event.getOutputTokens()).isEqualTo(3_000);
        assertThat(event.getCacheReadTokens()).isEqualTo(10_000);
        assertThat(event.getCacheWriteTokens()).isEqualTo(3_000);
        assertThat(event.getReasoningTokens()).isEqualTo(500);
        assertThat(event.getTotalCalls()).isEqualTo(2);
        assertThat(event.getPricingState()).isEqualTo(UsagePricingState.PRICED);
        assertThat(event.getCostUsd()).isEqualByComparingTo("1.000000");
    }

    @Test
    @DisplayName("a turn with no recorded call stays unverifiable rather than being priced as free")
    void accountingReaper_keepsATurnWithNoRecordedCallUnverifiable() throws Exception {
        UUID silentTurn = persistInFlightTurn("silent-turn");
        setCreatedAt(silentTurn, Instant.now().minus(Duration.ofMinutes(200)));

        reaperWithAnUnsafeWindow().reap();

        var event = usageEventRepository.findAll().stream()
                .filter(row -> row.getSourceId().equals(silentTurn))
                .findFirst()
                .orElseThrow();
        assertThat(event.getPricingState()).isEqualTo(UsagePricingState.UNPRICED);
        assertThat(event.getInputTokens()).isZero();
        assertThat(event.getCostUsd()).isNull();
    }

    private void accumulateProxyCall(
            UUID turnId, long input, long output, long reasoning, long cacheRead, long cacheWrite) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> assertThat(chatMessageRepository.accumulateLlmUsage(
                                turnId, input, output, reasoning, cacheRead, cacheWrite))
                        .isEqualTo(1));
    }

    private UUID persistInFlightTurn(String prompt) {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, prompt);
        UUID assistantId = UUID.randomUUID();
        persistence.persistInFlight(thread, prompt, assistantId, null, admittedMentorConfig());
        return assistantId;
    }

    private MentorInFlightReaper reaperWithAnUnsafeWindow() {
        var logger = (Logger) LoggerFactory.getLogger(MentorInFlightReaper.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            var reaper =
                    new MentorInFlightReaper(chatMessageRepository, accounting, meterRegistry, Duration.ofMinutes(10));
            assertThat(events.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("PT10M is unsafe", "using PT3H10M");
            });
            return reaper;
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    private void setCreatedAt(UUID messageId, Instant createdAt) throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("UPDATE chat_message SET created_at = ? WHERE id = ?")) {
            statement.setTimestamp(1, Timestamp.from(createdAt));
            statement.setObject(2, messageId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("chk_chat_message_status rejects values outside (in_flight,completed,interrupted)")
    void statusColumnCheckConstraintFires() throws Exception {
        ChatThread thread =
                persistence.ensureThread(workspace.getId(), UUID.randomUUID(), accountId, "constraint test");
        assertThatThrownBy(() -> {
                    try (var conn = dataSource.getConnection();
                            var stmt = conn.prepareStatement(
                                    "INSERT INTO chat_message (id, thread_id, workspace_id, role, parts, status,"
                                            + " created_at, version) VALUES (?, ?, ?, 'ASSISTANT', '[]'::jsonb, ?, now(),"
                                            + " 0)")) {
                        stmt.setObject(1, UUID.randomUUID());
                        stmt.setObject(2, thread.getId());
                        // Must fit VARCHAR(16) so we exercise the CHECK constraint, not the
                        // length truncation that fires before the CHECK runs.
                        stmt.setLong(3, workspace.getId());
                        stmt.setString(4, "in_flite");
                        stmt.executeUpdate();
                    }
                })
                .isInstanceOf(SQLException.class)
                // Production ships the explicit `chk_chat_message_status` via Liquibase; ddl-auto=create
                // also generates a Hibernate-implicit `chat_message_status_check`. Either can fire first.
                .satisfies(t -> assertThat(t.getMessage())
                        .containsAnyOf("chk_chat_message_status", "chat_message_status_check"));
    }
}
