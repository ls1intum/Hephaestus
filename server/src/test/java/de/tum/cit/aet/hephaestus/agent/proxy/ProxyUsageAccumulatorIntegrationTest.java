package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobLlmUsage;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The proxy's running usage totals, asserted on the {@code agent_job} row they are written to.
 *
 * <p>Against the real database rather than a mocked repository, because the behaviour under test is
 * the ADD: a job makes many calls, and each must land on top of the previous ones. A verify-only test
 * would pin the arguments and still pass if {@code accumulateLlmUsage} overwrote instead of summing —
 * which would silently discard everything but the last call of a crashed run.
 *
 * <p>The bean itself is absent from the test context ({@code hephaestus.runtime.worker.enabled=false}),
 * so the accumulator is constructed directly and its {@code REQUIRES_NEW} boundary supplied by
 * {@link TransactionTemplate}.
 */
@Tag("integration")
class ProxyUsageAccumulatorIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static JsonNode json(String body) {
        return MAPPER.readTree(body);
    }

    private AgentJob persistedJob(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "Proxy " + slug, slug + "-org", AccountType.ORG, owner);
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(MAPPER.createObjectNode());
        job.prePersist();
        return jobRepository.saveAndFlush(job);
    }

    private void accumulate(UUID jobId, JsonNode body, boolean responsesProtocol) {
        accumulateAs(
            jobId == null
                ? null
                : new ProxyRouting.BilledAttempt(LlmUsageSourceType.AGENT_JOB, jobId, 0, BigDecimal.ZERO),
            body,
            responsesProtocol
        );
    }

    private void accumulateAs(ProxyRouting.BilledAttempt attempt, JsonNode body, boolean responsesProtocol) {
        accumulateAs(
            new ProxyUsageAccumulator(jobRepository, new SimpleMeterRegistry()),
            attempt,
            body,
            responsesProtocol
        );
    }

    private void accumulateAs(
        ProxyUsageAccumulator accumulator,
        ProxyRouting.BilledAttempt attempt,
        JsonNode body,
        boolean responsesProtocol
    ) {
        transactionTemplate.executeWithoutResult(tx ->
            accumulator.accumulate(attempt, ProxyTokenUsage.from(body, responsesProtocol))
        );
    }

    private AgentJobLlmUsage usageOf(AgentJob job) {
        return jobRepository.findLlmUsageById(job.getId()).orElseThrow();
    }

    @Test
    @DisplayName("chat-completions: the input bucket is prompt tokens MINUS the cached ones")
    void completionsUsageBillsNonCachedInputSeparatelyFromCacheReads() {
        AgentJob job = persistedJob("proxy-usage-completions");

        // prompt_tokens is inclusive of cached; billing both buckets in full would charge twice.
        accumulate(
            job.getId(),
            json(
                "{\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":50," +
                    "\"prompt_tokens_details\":{\"cached_tokens\":20}," +
                    "\"completion_tokens_details\":{\"reasoning_tokens\":10}}}"
            ),
            false
        );

        AgentJobLlmUsage usage = usageOf(job);
        assertThat(usage.inputTokens()).isEqualTo(80);
        assertThat(usage.cacheReadTokens()).isEqualTo(20);
        assertThat(usage.outputTokens()).isEqualTo(50);
        assertThat(usage.reasoningTokens()).isEqualTo(10);
        assertThat(usage.totalCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("responses protocol: the same totals from the input_tokens/output_tokens shape")
    void responsesUsageReadsInputAndOutputTokenShape() {
        AgentJob job = persistedJob("proxy-usage-responses");

        accumulate(
            job.getId(),
            json(
                "{\"usage\":{\"input_tokens\":200,\"output_tokens\":70," +
                    "\"input_tokens_details\":{\"cached_tokens\":50}," +
                    "\"output_tokens_details\":{\"reasoning_tokens\":25}}}"
            ),
            true
        );

        AgentJobLlmUsage usage = usageOf(job);
        assertThat(usage.inputTokens()).isEqualTo(150);
        assertThat(usage.cacheReadTokens()).isEqualTo(50);
        assertThat(usage.outputTokens()).isEqualTo(70);
        assertThat(usage.reasoningTokens()).isEqualTo(25);
    }

    /** The reason the class exists: a crashed run is billed for every call it made, not the last one. */
    @Test
    @DisplayName("successive calls add onto the row rather than replacing it")
    void repeatedCallsAccumulateOntoTheSameRow() {
        AgentJob job = persistedJob("proxy-usage-adds");
        String body =
            "{\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":10," +
            "\"prompt_tokens_details\":{\"cached_tokens\":5}," +
            "\"completion_tokens_details\":{\"reasoning_tokens\":2}}}";

        accumulate(job.getId(), json(body), false);
        accumulate(job.getId(), json(body), false);
        accumulate(job.getId(), json(body), false);

        AgentJobLlmUsage usage = usageOf(job);
        assertThat(usage.totalCalls()).isEqualTo(3);
        assertThat(usage.inputTokens()).isEqualTo(75); // 3 × (30 − 5)
        assertThat(usage.cacheReadTokens()).isEqualTo(15);
        assertThat(usage.outputTokens()).isEqualTo(30);
        assertThat(usage.reasoningTokens()).isEqualTo(6);
    }

    @Test
    @DisplayName("a response with no usage block leaves the row at zero")
    void missingUsageBlockRecordsNothing() {
        AgentJob job = persistedJob("proxy-usage-no-block");

        accumulate(job.getId(), json("{\"choices\":[]}"), false);

        AgentJobLlmUsage usage = usageOf(job);
        assertThat(usage.totalCalls()).isZero();
        assertThat(usage.inputTokens()).isZero();
    }

    @Test
    @DisplayName("the mentor route (no billing target) touches no row")
    void nullJobIdIsANoOp() {
        AgentJob job = persistedJob("proxy-usage-null-target");

        accumulate(null, json("{\"usage\":{\"prompt_tokens\":10}}"), false);

        assertThat(usageOf(job).totalCalls()).isZero();
    }

    /**
     * The orphan-recovery race, end to end on the real row.
     *
     * <p>Attempt 0 dispatches a provider call. While it waits, its worker's heartbeat goes stale and
     * orphan recovery requeues the job — bumping {@code retry_count} and zeroing these counters — after
     * which a sibling claims it as attempt 1. The original worker was never dead: its response arrives
     * and it accumulates. Without the attempt fence those tokens land on attempt 1's freshly-zeroed
     * counters and are billed at attempt 1's price and funding source.
     *
     * <p>Kills the mutation "drop {@code AND j.retryCount = :attempt} from {@code accumulateLlmUsage}":
     * without it the row reads 25 input tokens and one call belonging to nobody.
     */
    @Test
    @DisplayName("a late call from a superseded attempt is dropped, not billed to the attempt that now owns the row")
    void lateWriteFromASupersededAttemptDoesNotLandOnTheNewAttempt() {
        AgentJob job = persistedJob("proxy-usage-superseded");
        ProxyRouting.BilledAttempt attemptZero = new ProxyRouting.BilledAttempt(
            LlmUsageSourceType.AGENT_JOB,
            job.getId(),
            0,
            BigDecimal.ZERO
        );
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProxyUsageAccumulator accumulator = new ProxyUsageAccumulator(jobRepository, registry);

        // The requeue: retry_count bumped, per-attempt counters zeroed, row reclaimed as attempt 1.
        requeueTo(job, 1);

        accumulateAs(
            accumulator,
            attemptZero,
            json("{\"usage\":{\"prompt_tokens\":25,\"completion_tokens\":9}}"),
            false
        );

        AgentJobLlmUsage usage = usageOf(job);
        assertThat(usage.totalCalls()).isZero();
        assertThat(usage.inputTokens()).isZero();
        assertThat(usage.outputTokens()).isZero();
        assertThat(registry.counter("llm.proxy.usage.accumulate.superseded").count()).isEqualTo(1.0);
    }

    /** The same fence read the other way: the attempt that still owns the row is billed normally. */
    @Test
    @DisplayName("the attempt that still owns the row is accumulated onto as before")
    void theOwningAttemptStillAccumulates() {
        AgentJob job = persistedJob("proxy-usage-owning-attempt");
        requeueTo(job, 1);

        accumulateAs(
            new ProxyRouting.BilledAttempt(LlmUsageSourceType.AGENT_JOB, job.getId(), 1, BigDecimal.ZERO),
            json("{\"usage\":{\"prompt_tokens\":25,\"completion_tokens\":9}}"),
            false
        );

        AgentJobLlmUsage usage = usageOf(job);
        assertThat(usage.totalCalls()).isEqualTo(1);
        assertThat(usage.inputTokens()).isEqualTo(25);
        assertThat(usage.outputTokens()).isEqualTo(9);
    }

    /**
     * The terminal half of the same fence: after a clean finish the row's totals are the runner's
     * authoritative report, and a straggling proxy call must not add onto them.
     */
    @Test
    @DisplayName("a call that lands after the job went terminal does not corrupt the final totals")
    void lateWriteAfterTerminalIsDropped() {
        AgentJob job = persistedJob("proxy-usage-after-terminal");
        job.setStatus(AgentJobStatus.COMPLETED);
        job.setLlmTotalInputTokens(500);
        job.setLlmTotalCalls(4);
        jobRepository.saveAndFlush(job);

        accumulate(job.getId(), json("{\"usage\":{\"prompt_tokens\":25}}"), false);

        AgentJobLlmUsage usage = usageOf(job);
        assertThat(usage.inputTokens()).isEqualTo(500);
        assertThat(usage.totalCalls()).isEqualTo(4);
    }

    /** Reproduces what {@code AgentJobRepository#requeueOrphan} does to the row, then re-claims it. */
    private void requeueTo(AgentJob job, int attempt) {
        job.setRetryCount(attempt);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setLlmTotalCalls(0);
        job.setLlmTotalInputTokens(0);
        job.setLlmTotalOutputTokens(0);
        job.setLlmTotalReasoningTokens(0);
        job.setLlmCacheReadTokens(0);
        job.setLlmCacheWriteTokens(0);
        jobRepository.saveAndFlush(job);
    }

    /**
     * Only reachable with a stubbed repository: a real one has no way to fail on demand. The
     * behaviour under test is not the swallow — it is that the swallow is COUNTED, because these
     * tokens are the only record of a call that already cost money.
     */
    @Test
    @DisplayName("a failed write is swallowed, but counted so the under-billing is visible")
    void failedAccumulationIsCountedNotSilent() {
        AgentJobRepository failing = mock(AgentJobRepository.class);
        when(failing.accumulateLlmUsage(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt())).thenThrow(
            new IllegalStateException("connection reset")
        );
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProxyUsageAccumulator accumulator = new ProxyUsageAccumulator(failing, registry);

        assertThatCode(() ->
            accumulator.accumulate(
                new ProxyRouting.BilledAttempt(LlmUsageSourceType.AGENT_JOB, UUID.randomUUID(), 0, BigDecimal.ZERO),
                ProxyTokenUsage.from(json("{\"usage\":{\"prompt_tokens\":10}}"), false)
            )
        ).doesNotThrowAnyException();

        assertThat(registry.counter("llm.proxy.usage.accumulate.failure").count()).isEqualTo(1.0);
    }
}
