package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.mentor.MentorTurnLlmUsage;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * The proxy's per-call usage write for a mentor turn, asserted on the {@code chat_message} row it
 * lands on.
 *
 * <p>Against the real database rather than a mocked repository, because both behaviours under test
 * only exist in SQL. The first is the ADD: a turn makes many calls and each must land on top of the
 * previous ones — a verify-only test would pass just as happily against an UPDATE that overwrote,
 * which would discard everything but the last call of a crashed turn. The second is the
 * {@code status = 'in_flight'} fence, which is a predicate no mock can evaluate.
 *
 * <p>Mirrors {@code ProxyUsageAccumulatorIntegrationTest} on the agent-job side. The bean is absent
 * from the test context ({@code hephaestus.runtime.worker.enabled=false}), so the accumulator is
 * constructed directly and its {@code REQUIRES_NEW} boundary supplied by {@link TransactionTemplate}.
 */
@Tag("integration")
class MentorTurnUsageAccumulatorIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final LlmPriceSnapshot TEN_DOLLARS_PER_MILLION = new LlmPriceSnapshot(
        FundingSource.INSTANCE,
        PricingState.PRICED,
        1L,
        null,
        new BigDecimal("10"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO
    );

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private MentorProxyCredentialRegistry credentials;
    private SimpleMeterRegistry meters;
    private MentorTurnUsageAccumulator accumulator;
    private ChatThread thread;

    @BeforeEach
    void setUpFixture() {
        credentials = new MentorProxyCredentialRegistry();
        meters = new SimpleMeterRegistry();
        accumulator = new MentorTurnUsageAccumulator(chatMessageRepository, credentials, meters);

        User owner = persistUser("mentor-usage-owner");
        Workspace workspace = createWorkspace(
            "mentor-usage-ws",
            "Mentor Usage",
            "mentor-usage-org",
            AccountType.ORG,
            owner
        );
        thread = new ChatThread();
        thread.setId(UUID.randomUUID());
        thread.setUser(owner);
        thread.setWorkspace(workspace);
        thread.setTitle("usage");
        thread = chatThreadRepository.saveAndFlush(thread);
    }

    private UUID turn(ChatMessage.Status status) {
        ChatMessage assistant = new ChatMessage();
        assistant.setId(UUID.randomUUID());
        assistant.setThread(thread);
        assistant.setRole(ChatMessage.Role.ASSISTANT);
        assistant.setStatus(status);
        assistant.setParts(JsonNodeFactory.instance.arrayNode());
        return chatMessageRepository.saveAndFlush(assistant).getId();
    }

    private ProxyRouting.BilledAttempt attempt(UUID turnId) {
        return new ProxyRouting.BilledAttempt(LlmUsageSourceType.MENTOR_TURN, turnId, 0, BigDecimal.ZERO);
    }

    private void accumulate(UUID turnId, ProxyTokenUsage usage) {
        transactionTemplate.executeWithoutResult(tx -> accumulator.accumulate(attempt(turnId), usage));
    }

    private MentorTurnLlmUsage usageOf(UUID turnId) {
        return chatMessageRepository.findLlmUsageById(turnId).orElseThrow();
    }

    /**
     * The reason the columns exist: a turn that dies has every call it made on record, not the last one.
     *
     * <p>Kills "replace the totals instead of adding to them" — the row below would read one call.
     */
    @Test
    @DisplayName("successive calls add onto the turn's row rather than replacing it")
    void repeatedCallsAccumulateOntoTheSameRow() {
        UUID turnId = turn(ChatMessage.Status.in_flight);

        accumulate(turnId, new ProxyTokenUsage(100, 40, 10, 25));
        accumulate(turnId, new ProxyTokenUsage(200, 60, 5, 0));
        accumulate(turnId, new ProxyTokenUsage(300, 80, 0, 0));

        MentorTurnLlmUsage usage = usageOf(turnId);
        assertThat(usage.totalCalls()).isEqualTo(3);
        assertThat(usage.inputTokens()).isEqualTo(600);
        assertThat(usage.outputTokens()).isEqualTo(180);
        assertThat(usage.reasoningTokens()).isEqualTo(15);
        assertThat(usage.cacheReadTokens()).isEqualTo(25);
        assertThat(usage.hasBillableUsage()).isTrue();
    }

    /**
     * The fence, on the terminal side. A provider call can outlive the turn that issued it; by the time
     * it returns, the turn's totals have already been read and billed.
     *
     * <p>Kills "drop {@code AND status = 'in_flight'} from accumulateLlmUsage": the straggler below is
     * added to a turn that is already on the ledger, so the row and the ledger disagree forever and a
     * re-read (by the reaper, say) would double-bill it.
     */
    @Test
    @DisplayName("a call that lands after the turn ended is dropped, not added to a turn already billed")
    void aLateCallAfterTheTurnEndedIsDropped() {
        UUID turnId = turn(ChatMessage.Status.in_flight);
        accumulate(turnId, new ProxyTokenUsage(100, 40, 0, 0));

        transactionTemplate.executeWithoutResult(tx -> {
            ChatMessage message = chatMessageRepository.findById(turnId).orElseThrow();
            message.setStatus(ChatMessage.Status.completed);
            chatMessageRepository.saveAndFlush(message);
        });
        accumulate(turnId, new ProxyTokenUsage(999, 999, 0, 0));

        MentorTurnLlmUsage usage = usageOf(turnId);
        assertThat(usage.totalCalls()).isEqualTo(1);
        assertThat(usage.inputTokens()).isEqualTo(100);
        assertThat(meters.counter("llm.proxy.usage.mentor.superseded").count()).isEqualTo(1.0);
    }

    /**
     * The gate's in-memory mirror is derived from the durable write, never the other way round.
     *
     * <p>Kills "advance the meter before/regardless of the row write": the meter would then report
     * spend the durable record does not have, and the gate would refuse calls the workspace had
     * headroom for.
     */
    @Test
    @DisplayName("the budget gate's meter is advanced only by a call that actually landed on the row")
    void theMeterFollowsTheRow() {
        UUID sessionId = UUID.randomUUID();
        String token = credentials.mint(
            sessionId,
            new MentorProxyCredentialRegistry.Route(
                "openai-responses",
                "https://upstream.example.com/v1",
                FundingSource.INSTANCE,
                1L,
                2L,
                thread.getWorkspace().getId()
            )
        );
        UUID inFlight = turn(ChatMessage.Status.in_flight);
        MentorTurnMeter meter = new MentorTurnMeter(inFlight, TEN_DOLLARS_PER_MILLION);
        credentials.bindTurn(sessionId, meter);

        accumulate(inFlight, new ProxyTokenUsage(100_000, 0, 0, 0));

        assertThat(credentials.validate(token).orElseThrow().inFlightSpendUsd()).isEqualByComparingTo("1.00");

        // Same meter, but the turn has ended: the row rejects the write, so the meter must not move.
        transactionTemplate.executeWithoutResult(tx -> {
            ChatMessage message = chatMessageRepository.findById(inFlight).orElseThrow();
            message.setStatus(ChatMessage.Status.interrupted);
            chatMessageRepository.saveAndFlush(message);
        });
        accumulate(inFlight, new ProxyTokenUsage(100_000, 0, 0, 0));

        assertThat(credentials.validate(token).orElseThrow().inFlightSpendUsd()).isEqualByComparingTo("1.00");
        assertThat(usageOf(inFlight).inputTokens()).isEqualTo(100_000);
    }

    /**
     * The clobber the {@code updatable = false} mapping exists to prevent: an entity loaded BEFORE a
     * proxy call, saved AFTER it. Hibernate would write the snapshot's zeros back over the call.
     *
     * <p>Kills "make the llm_* columns writable on {@code ChatMessage}": the turn below loses the only
     * record of a call that already cost money, and does so silently — the terminal write looks fine.
     */
    @Test
    @DisplayName("saving a stale message snapshot cannot roll back a proxy call recorded meanwhile")
    void aStaleEntityFlushDoesNotClobberTheCounters() {
        UUID turnId = turn(ChatMessage.Status.in_flight);

        transactionTemplate.executeWithoutResult(tx -> {
            // Loaded first: this snapshot has all-zero counters and stays in the persistence context.
            ChatMessage stale = chatMessageRepository.findById(turnId).orElseThrow();
            accumulator.accumulate(attempt(turnId), new ProxyTokenUsage(4_242, 99, 0, 0));
            // ...and is then written back, the way finalise/the reaper write a terminal status.
            stale.setMetadata(JsonNodeFactory.instance.objectNode().put("finishReason", "stop"));
            chatMessageRepository.saveAndFlush(stale);
        });

        MentorTurnLlmUsage usage = usageOf(turnId);
        assertThat(usage.inputTokens()).isEqualTo(4_242);
        assertThat(usage.outputTokens()).isEqualTo(99);
        assertThat(usage.totalCalls()).isEqualTo(1);
    }

    /**
     * Only reachable with a stubbed repository: a real one has no way to fail on demand. The behaviour
     * under test is not the swallow — it is that the swallow is COUNTED, because these tokens may be
     * the only record of a call that already cost money.
     */
    @Test
    @DisplayName("a failed write is swallowed, but counted so the under-billing is visible")
    void failedAccumulationIsCountedNotSilent() {
        ChatMessageRepository failing = mock(ChatMessageRepository.class);
        when(failing.accumulateLlmUsage(any(), anyLong(), anyLong(), anyLong(), anyLong())).thenThrow(
            new IllegalStateException("connection reset")
        );
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MentorTurnUsageAccumulator failingAccumulator = new MentorTurnUsageAccumulator(
            failing,
            new MentorProxyCredentialRegistry(),
            registry
        );

        assertThatCode(() ->
            failingAccumulator.accumulate(attempt(UUID.randomUUID()), new ProxyTokenUsage(10, 1, 0, 0))
        ).doesNotThrowAnyException();

        assertThat(registry.counter("llm.proxy.usage.mentor.failure").count()).isEqualTo(1.0);
    }
}
