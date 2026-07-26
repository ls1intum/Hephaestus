package de.tum.cit.aet.hephaestus.agent.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetHeadroom;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * In-flight budget backstop for the LLM proxy. The submit and claim gates
 * ({@code AgentJobService.submit}, {@code AgentJobExecutor}) only decide whether a job may
 * <em>start</em>; once a job or mentor turn is running it can make many upstream calls, and the ledger
 * those gates read gains nothing until the run ENDS. This gate is what bounds a run in progress: it
 * refuses new calls for a workspace whose cap is already reached.
 *
 * <h2>The bound it guarantees</h2>
 *
 * <p>Because it judges recorded spend PLUS the calling execution's own consumed-but-unrecorded spend
 * ({@code ProxyRouting.BilledAttempt#spentUsd}, priced with the rates frozen onto it at admission),
 * the following holds for every agent-job attempt AND every mentor turn:
 *
 * <blockquote>An execution is refused as soon as its OWN completed calls have consumed the headroom
 * the ledger last showed. So it can overshoot the cap by at most the calls it had already dispatched
 * when that last forward was admitted — one call for a sequential runner or Pi's agent loop — never by
 * the whole run.</blockquote>
 *
 * <p>This is what the class did not do before: keyed on the ledger alone, every check during a run
 * saw zero of that run's spend, so one admitted job or turn could make unlimited calls against an
 * exhausted cap. {@code ProxyBudgetGateTest} pins the claim above.
 *
 * <p>An execution's spend-so-far reaches this gate from wherever it accrues — an agent job's own row,
 * a mentor turn's {@code MentorTurnMeter} — and both buffered and streamed calls feed it
 * ({@code ProxyStreamUsageTap} reads the usage frame off an SSE stream as it passes).
 *
 * <h2>What it still does not bound</h2>
 *
 * <ul>
 *   <li><b>Concurrency.</b> Each execution is bounded on its own, so N running concurrently for one
 *       workspace can together reach N times the cap before any of them stops. For jobs, N is the
 *       workspace's {@code maxConcurrentJobs} — an operator-set number, not an open end; for mentor
 *       turns it is the number of developers chatting at once.</li>
 *   <li><b>Calls a provider reports no usage for.</b> A streamed call whose provider rejects
 *       {@code stream_options.include_usage} (retried without it, counted as
 *       {@code llm.proxy.stream.usage.unsupported}) contributes nothing here, as does any response
 *       with no usage block. The execution is then bounded only by the ledger term.</li>
 *   <li><b>Calls made outside a mentor turn's window.</b> A mentor session's credential names a turn
 *       only while that turn owns the sandbox; a call outside that window carries no in-flight term.</li>
 * </ul>
 *
 * <h2>Why the verdict is cached but the attempt's spend is not</h2>
 *
 * <p>The month-window SUM is cached per workspace for a short TTL so the proxy does not run it on
 * every forward; the execution's own spend is read fresh on every request, at no cost, because
 * authenticating the token has already loaded the row (a job) or the meter (a mentor turn).
 * Staleness therefore only affects spend by OTHER
 * executions and cap changes — a workspace that crosses the cap starts being blocked within one TTL,
 * and one whose budget is raised unblocks within one TTL. Shrinking the TTL would not improve the
 * bound above (it is the fresh term that produces it) and would put a month-window SUM back on the hot
 * path, so it stays fixed. Never kills a call already streaming; only pre-forward.
 */
@Component
class ProxyBudgetGate {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final long MAX_CACHED_WORKSPACES = 10_000;

    private final LlmBudgetService budgetService;
    private final Cache<Long, LlmBudgetHeadroom> headroomByWorkspace;

    ProxyBudgetGate(LlmBudgetService budgetService) {
        this.budgetService = budgetService;
        this.headroomByWorkspace = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_CACHED_WORKSPACES)
            .build();
    }

    /**
     * True when calls funded the way {@code routing} is funded have crossed their payer's monthly cap
     * (or that payer's month is capped-and-unverifiable), counting what the calling attempt has already
     * spent. A {@code null} workspace id (legacy, unattributable route) fails open — never blocks. The
     * per-key loader collapses a concurrent burst for one workspace into a single ledger lookup.
     *
     * <p>Judged per funding source so an exhausted host budget cannot 429 calls the workspace pays for
     * through its own provider — the two purses pause independently. An attempt whose own funding
     * source is unknown has its in-flight spend charged to both purses, matching how an unattributable
     * call is judged against both caps.
     */
    boolean isBlocked(ProxyRouting routing) {
        Long workspaceId = routing.workspaceId();
        if (workspaceId == null) {
            return false;
        }
        LlmBudgetHeadroom headroom = headroomByWorkspace.get(workspaceId, budgetService::headroom);
        return (
            headroom != null &&
            headroom.decideWith(routing.connectionScope(), routing.inFlightSpendUsd()).blocks(routing.connectionScope())
        );
    }
}
