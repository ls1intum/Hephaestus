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
 * <p>The contract, for every agent-job attempt AND every mentor turn:
 *
 * <blockquote>An execution is refused as soon as its OWN completed calls have consumed the headroom
 * the ledger last showed. So it can overshoot the cap by at most the calls it had already dispatched
 * when that last forward was admitted — one call for a sequential runner or Pi's agent loop — never by
 * the whole run.</blockquote>
 *
 * <p>Why that holds, why the verdict is cached but the attempt's own spend is not, and what the bound
 * does NOT cover: {@code docs/decisions/0026-per-purpose-agent-bindings-and-llm-governance.md}.
 * {@code ProxyBudgetGateTest} pins the claim above.
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
