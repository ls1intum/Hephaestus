package de.tum.in.www1.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.TurnAlreadyInFlightException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.mentor.ChatMessage;
import de.tum.in.www1.hephaestus.mentor.ChatMessageRepository;
import de.tum.in.www1.hephaestus.mentor.ChatThread;
import de.tum.in.www1.hephaestus.mentor.ChatThreadRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Validates the {@link MentorTurnPersistence} REQUIRES_NEW contract end-to-end against a real
 * Postgres container: DB unique partial index, JSONB metadata round-trip, status transitions,
 * reaper sweep.
 */
@TestPropertySource(properties = "hephaestus.sandbox.enabled=true")
@DisplayName("MentorTurnPersistence integration")
class MentorTurnPersistenceIntegrationTest extends BaseIntegrationTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Autowired
    private MentorTurnPersistence persistence;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private DataSource dataSource;

    /**
     * {@link MentorChatService} pulls in this collaborator unconditionally; the production bean
     * is only registered when {@code hephaestus.sandbox.enabled=true} (Docker required). Provide
     * a mock so the integration context loads — this test never touches the sandbox boundary.
     */
    @MockitoBean
    @SuppressWarnings("unused")
    private InteractiveSandboxService interactiveSandboxService;

    private Workspace workspace;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        databaseTestUtils.cleanDatabase();
        // ddl-auto=create skips Liquibase, so partial indexes + CHECK constraints (which JPA
        // can't infer from @Entity) never land. Re-create the production shape here so the
        // persistence tests below exercise the real DB-level invariants
        // (statusColumnCheckConstraintFires + the concurrent-race tests rely on this).
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_message_in_flight_v2 " +
                    "ON chat_message (thread_id) WHERE status = 'in_flight'"
            );
            // Drop-then-add so a previous run's constraint doesn't survive across tests.
            stmt.execute("ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS chk_chat_message_status");
            stmt.execute(
                "ALTER TABLE chat_message ADD CONSTRAINT chk_chat_message_status " +
                    "CHECK (status IN ('in_flight', 'completed', 'interrupted'))"
            );
        }
        workspace = new Workspace();
        workspace.setWorkspaceSlug("mentor-persist-ws");
        workspace.setDisplayName("Mentor Persistence Workspace");
        workspace.setAccountLogin("mentor-persist-org");
        workspace.setAccountType(AccountType.ORG);
        workspace = workspaceRepository.save(workspace);

        GitProvider gitProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://gitlab.com")));

        user = new User();
        user.setNativeId(7_001L);
        user.setLogin("mentor-tester");
        user.setName("Mentor Tester");
        user.setAvatarUrl("https://example.com/m.png");
        user.setHtmlUrl("https://gitlab.com/mentor-tester");
        user.setType(User.Type.USER);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setProvider(gitProvider);
        user = userRepository.save(user);
    }

    @Test
    @DisplayName("ensureThread creates a thread when absent")
    void ensureThread_createsWhenAbsent() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = persistence.ensureThread(workspace.getId(), threadId, user, "Hello mentor");
        assertThat(thread.getId()).isEqualTo(threadId);
        assertThat(thread.getUser().getId()).isEqualTo(user.getId());
        assertThat(thread.getWorkspace().getId()).isEqualTo(workspace.getId());
        assertThat(thread.getTitle()).isEqualTo("Hello mentor");
        assertThat(chatThreadRepository.findById(threadId)).isPresent();
    }

    @Test
    @DisplayName("ensureThread returns the existing row when one already exists")
    void ensureThread_returnsExisting() {
        UUID threadId = UUID.randomUUID();
        ChatThread first = persistence.ensureThread(workspace.getId(), threadId, user, "first prompt");
        ChatThread second = persistence.ensureThread(workspace.getId(), threadId, user, "second prompt");
        assertThat(second.getId()).isEqualTo(first.getId());
        // Title is fixed on first write — a second call with a different prompt must NOT
        // overwrite, otherwise the thread sidebar flickers between titles.
        assertThat(second.getTitle()).isEqualTo("first prompt");
    }

    @Test
    @DisplayName("ensureThread hides foreign-owner reads as 404 (no thread enumeration)")
    void ensureThread_foreignOwnerThrows() {
        UUID threadId = UUID.randomUUID();
        persistence.ensureThread(workspace.getId(), threadId, user, "hello");

        User other = new User();
        other.setNativeId(7_002L);
        other.setLogin("other");
        other.setName("Other");
        other.setAvatarUrl("https://example.com/o.png");
        other.setHtmlUrl("https://gitlab.com/other");
        other.setType(User.Type.USER);
        other.setCreatedAt(Instant.now());
        other.setUpdatedAt(Instant.now());
        other.setProvider(user.getProvider());
        other = userRepository.save(other);

        final User otherUser = other;
        assertThatThrownBy(() ->
            persistence.ensureThread(workspace.getId(), threadId, otherUser, "intruder")
        ).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("persistInFlight writes user+assistant rows, returns assistant id, status=in_flight")
    void persistInFlight_happyPath() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "hello mentor",
            assistantId,
            null
        );
        assertThat(cookie.assistantMessageId()).isEqualTo(assistantId);

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.in_flight);

        ChatMessage userMessage = chatMessageRepository.findById(cookie.userMessageId()).orElseThrow();
        assertThat(userMessage.getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(userMessage.getParts().get(0).path("text").asText()).isEqualTo("hello mentor");
    }

    @Test
    @DisplayName("persistInFlight honours the client-supplied user message id")
    void persistInFlight_honorsClientUserMessageId() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID clientUserId = UUID.randomUUID();
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "hello mentor",
            assistantId,
            clientUserId
        );
        assertThat(cookie.userMessageId()).isEqualTo(clientUserId);
        assertThat(chatMessageRepository.findById(clientUserId)).isPresent();
    }

    @Test
    @DisplayName("persistInFlight throws TurnAlreadyInFlightException on the DB unique partial index")
    void persistInFlight_secondCallThrows() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        persistence.persistInFlight(thread, "first", UUID.randomUUID(), null);
        assertThatThrownBy(() -> persistence.persistInFlight(thread, "second", UUID.randomUUID(), null)).isInstanceOf(
            TurnAlreadyInFlightException.class
        );
    }

    @Test
    @DisplayName("persistInFlight: TWO REAL THREADS race the same thread row — exactly one wins, exactly one 409s")
    void persistInFlight_concurrentRace_exactlyOneWins() throws Exception {
        // Sequential calls only prove the SQL `WHERE in_flight` semantics fire. The dual-lock
        // defence (Java MentorTurnLock + DB partial unique index) is supposed to handle the
        // case where two API replicas (or two virtual threads on one replica) race the same
        // thread id. This test proves the DB half: even if the Java lock were stripped,
        // exactly one writer wins at the row level.
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch fire = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Object> attempt = () -> {
                ready.countDown();
                fire.await(5, java.util.concurrent.TimeUnit.SECONDS);
                try {
                    return persistence.persistInFlight(thread, "race", UUID.randomUUID(), null);
                } catch (RuntimeException ex) {
                    return ex; // surface to caller for classification
                }
            };
            var fa = pool.submit(attempt);
            var fb = pool.submit(attempt);
            // Wait until both threads are at the barrier, then release simultaneously.
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            fire.countDown();
            Object resultA = fa.get(10, java.util.concurrent.TimeUnit.SECONDS);
            Object resultB = fb.get(10, java.util.concurrent.TimeUnit.SECONDS);

            int winners = 0;
            int conflicts = 0;
            for (Object r : java.util.List.of(resultA, resultB)) {
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
    @DisplayName("finalise flips status to completed and writes parts + usage")
    void finalise_writesCompletedRow() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "hello",
            assistantId,
            null
        );

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
            /* costUsd */ null
        );
        UIMessageChunk.Finish finish = new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, finishMeta);

        persistence.finalise(cookie, state, finish);

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.completed);
        JsonNode meta = assistant.getMetadata();
        assertThat(meta.path("finishReason").asText()).isEqualTo("stop");
        assertThat(meta.path("model").asText()).isEqualTo("openai/gpt-oss-120b");
        // Nested wire shape — must match UIMessageChunk.MessageMetadata + webapp MessageMetadata
        // so a rehydrated thread renders identically to the live stream.
        assertThat(meta.path("usage").path("input").asLong()).isEqualTo(123);
        assertThat(meta.path("usage").path("output").asLong()).isEqualTo(45);
        assertThat(meta.path("usage").path("totalTokens").asLong()).isEqualTo(168);
        assertThat(meta.has("inputTokens")).as("flat keys retired").isFalse();
        assertThat(assistant.getParts().isArray()).isTrue();
        assertThat(assistant.getParts().get(0).path("text").asText()).isEqualTo("Hello there!");
    }

    @Test
    void finalise_storesSessionJsonlByteIdentically() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = persistence.ensureThread(workspace.getId(), threadId, user, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "hello",
            assistantId,
            null
        );

        // 3-byte and 4-byte UTF-8 characters exercise any layer that round-trips through String.
        byte[] expectedBytes = (
            "{\"type\":\"user_message\",\"text\":\"hello €\"}\n" +
            "{\"type\":\"assistant_message\",\"text\":\"hi 😀\",\"stopReason\":\"stop\"}\n"
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        TranslatorState state = new TranslatorState(assistantId);
        state.observeSessionJsonl(expectedBytes);
        persistence.finalise(cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));

        assertThat(chatThreadRepository.findSessionJsonl(threadId))
            .as("byte-identical: any re-encoding kills prompt-cache prefix matching")
            .contains(expectedBytes);
    }

    @Test
    void finalise_storesSessionJsonlAboveToastThreshold() {
        // Postgres TOAST threshold is ~2KB; values above ~8KB go out-of-line into pg_toast.
        // A 1MB payload exercises detoast on read — the path that would surface any
        // encoding/transport regression introduced by JDBC stream handling. Far more
        // realistic for a 20+ turn conversation than the byte-string fixtures above.
        UUID threadId = UUID.randomUUID();
        ChatThread thread = persistence.ensureThread(workspace.getId(), threadId, user, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "hello",
            assistantId,
            null
        );

        byte[] bigBytes = new byte[1024 * 1024]; // 1 MiB
        // Pattern with a stable header + repeating non-zero filler so a partial-read regression
        // is detectable by a single-byte check anywhere in the array.
        byte[] header = "{\"type\":\"user_message\",\"text\":\"".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(header, 0, bigBytes, 0, header.length);
        for (int i = header.length; i < bigBytes.length - 3; i++) bigBytes[i] = (byte) ('a' + (i % 26));
        bigBytes[bigBytes.length - 3] = '"';
        bigBytes[bigBytes.length - 2] = '}';
        bigBytes[bigBytes.length - 1] = '\n';

        TranslatorState state = new TranslatorState(assistantId);
        state.observeSessionJsonl(bigBytes);
        persistence.finalise(cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));

        byte[] readBack = chatThreadRepository.findSessionJsonl(threadId).orElseThrow();
        assertThat(readBack).as("1MB TOAST round-trip preserves every byte").isEqualTo(bigBytes);
    }

    @Test
    void finalise_withoutSessionJsonl_preservesPriorTurn() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = persistence.ensureThread(workspace.getId(), threadId, user, "hello");

        byte[] priorBytes = "{\"prior\":\"turn\"}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        chatThreadRepository.updateSessionJsonl(threadId, priorBytes);

        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "follow-up",
            assistantId,
            null
        );
        persistence.finalise(
            cookie,
            new TranslatorState(assistantId),
            new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null)
        );

        assertThat(chatThreadRepository.findSessionJsonl(threadId)).contains(priorBytes);
    }

    @Test
    @DisplayName("interrupt flips status to interrupted and stores the error message")
    void interrupt_writesInterruptedRow() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "hello",
            assistantId,
            null
        );

        persistence.interrupt(cookie, new TranslatorState(assistantId), new IllegalStateException("upstream timeout"));

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        assertThat(assistant.getMetadata().path("error").asText()).isEqualTo("upstream timeout");
    }

    @Test
    @DisplayName("@Version: reaper bumps version, then a stale-snapshot save throws OptimisticLockingFailureException")
    void optimisticLocking_staleSnapshotSaveFails() {
        // Root-cause protection against the reaper-vs-late-finalise data corruption: a
        // writer that loaded the entity at version=N can no longer overwrite a row the
        // reaper bumped to N+1. Hibernate detects the version mismatch on the SQL UPDATE
        // predicate (`WHERE id = ? AND version = ?`) and throws — the persistence service
        // catches and skips, so the reaper's verdict survives.
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID assistantId = UUID.randomUUID();
        persistence.persistInFlight(thread, "hello", assistantId, null);

        // Simulate the in-flight runner: load a managed snapshot at the current version.
        ChatMessage stale = chatMessageRepository.findById(assistantId).orElseThrow();
        Long versionBefore = stale.getVersion();
        assertThat(versionBefore).isNotNull();

        // Reaper sweeps, bumps version explicitly via @Modifying SQL.
        chatMessageRepository.reapStaleInFlight(Instant.now().plusSeconds(60));
        ChatMessage afterReaper = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(afterReaper.getVersion()).isEqualTo(versionBefore + 1L);
        assertThat(afterReaper.getStatus()).isEqualTo(ChatMessage.Status.interrupted);

        // Stale-snapshot save attempt: Hibernate's UPDATE predicate fails on the version,
        // surfaces as OptimisticLockingFailureException. A prior read-before-write
        // `isStillInFlight` check is replaced by this DB-enforced protection — version
        // mismatch is the single durable signal that another writer touched the row.
        stale.setStatus(ChatMessage.Status.completed);
        assertThatThrownBy(() -> {
            chatMessageRepository.saveAndFlush(stale);
        }).isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        // After the failed save attempt, the row in the DB still reflects the reaper's verdict.
        ChatMessage finalState = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        assertThat(finalState.getMetadata().path("error").asText()).isEqualTo("server restart");
    }

    @Test
    @DisplayName("reapStaleInFlight flips in-flight rows older than the cutoff")
    void reaper_flipsStaleInFlightRows() throws Exception {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "stuck");
        UUID assistantId = UUID.randomUUID();
        persistence.persistInFlight(thread, "stuck", assistantId, null);

        // The cutoff is "older than now" — fresh rows are NOT reaped.
        int updated = chatMessageRepository.reapStaleInFlight(Instant.now().minusSeconds(60));
        assertThat(updated).isZero();
        assertThat(chatMessageRepository.findById(assistantId).orElseThrow().getStatus()).isEqualTo(
            ChatMessage.Status.in_flight
        );

        // Cutoff in the future → row is past the cutoff → reaped.
        int updatedSweep = chatMessageRepository.reapStaleInFlight(Instant.now().plusSeconds(60));
        assertThat(updatedSweep).isEqualTo(1);
        ChatMessage reaped = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(reaped.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        assertThat(reaped.getMetadata().path("error").asText()).isEqualTo("server restart");
    }

    @Test
    @DisplayName("chk_chat_message_status rejects values outside (in_flight,completed,interrupted)")
    void statusColumnCheckConstraintFires() throws Exception {
        // Pin the production DB-level guard. The column CHECK is the only backstop that
        // catches a future writer typo'ing `"in_flite"` or inventing a new status without a
        // matching application-side state machine update. The integration test profile
        // recreates the constraint in @BeforeEach so this assertion exercises the real
        // predicate, not just a JPA enum validation.
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "constraint test");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            try (
                var conn = dataSource.getConnection();
                var stmt = conn.prepareStatement(
                    "INSERT INTO chat_message (id, thread_id, role, parts, status, created_at, version) " +
                        "VALUES (?, ?, 'ASSISTANT', '[]'::jsonb, ?, now(), 0)"
                )
            ) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, thread.getId());
                // Must fit VARCHAR(16) so we exercise the CHECK constraint, not the
                // length truncation that fires before the CHECK runs.
                stmt.setString(3, "in_flite");
                stmt.executeUpdate();
            }
        })
            .isInstanceOf(java.sql.SQLException.class)
            // The constraint name varies by environment: production ships our explicit
            // `chk_chat_message_status` via Liquibase, while the test profile's ddl-auto=create
            // also generates a Hibernate-implicit `chat_message_status_check` from the
            // @Enumerated(EnumType.STRING) field. Either fires first — both encode the same
            // enum invariant — so the assertion accepts either.
            .satisfies(t ->
                org.assertj.core.api.Assertions.assertThat(t.getMessage()).containsAnyOf(
                    "chk_chat_message_status",
                    "chat_message_status_check"
                )
            );
    }
}
