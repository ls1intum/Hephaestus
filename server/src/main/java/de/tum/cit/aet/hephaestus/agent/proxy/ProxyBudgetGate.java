package de.tum.cit.aet.hephaestus.agent.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetHeadroom;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * In-flight budget backstop for the LLM proxy: the submit and claim gates only decide whether a job
 * may <em>start</em>, and the ledger they read gains nothing until the run ENDS. An execution is
 * refused as soon as its OWN completed calls have consumed the headroom the ledger last showed, so it
 * can overshoot the cap by at most the calls already dispatched — never by the whole run. Why that
 * holds: {@code docs/decisions/0026-per-purpose-agent-bindings-and-llm-governance.md}.
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
     * An unattributable route (no workspace id) fails open. Judged per funding source so an exhausted
     * host budget cannot 429 calls the workspace pays for through its own provider.
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
