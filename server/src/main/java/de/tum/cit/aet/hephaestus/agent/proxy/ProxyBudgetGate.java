package de.tum.cit.aet.hephaestus.agent.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetBlockReason;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * In-flight budget backstop for the LLM proxy (#1368). The submit and claim gates
 * ({@code AgentJobService.submit}, {@code AgentJobExecutor}) only decide whether a job may
 * <em>start</em>; once a job or mentor turn is running it can make unbounded upstream calls. This
 * gate lets the proxy refuse <em>new</em> calls for a workspace that has already crossed its cap,
 * bounding a runaway that began after exhaustion to at most the calls already in flight.
 *
 * <p>It is a soft backstop, not a hard reservation: the verdict is cached per workspace for a short
 * TTL so the proxy does not run a month-window SUM on every forward. Staleness is bounded to the
 * TTL — a workspace that crosses the cap starts being blocked within one TTL, and one whose budget
 * is raised unblocks within one TTL. Never kills a call already streaming; only pre-forward.
 */
@Component
class ProxyBudgetGate {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final long MAX_CACHED_WORKSPACES = 10_000;

    private final LlmBudgetService budgetService;
    private final Cache<Long, Boolean> blockedByWorkspace;

    ProxyBudgetGate(LlmBudgetService budgetService) {
        this.budgetService = budgetService;
        this.blockedByWorkspace = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_CACHED_WORKSPACES)
            .build();
    }

    /**
     * True when the workspace has crossed its monthly LLM cap (or an unverifiable-and-capped month).
     * {@code null} workspace id (legacy, unattributable route) fails open — never blocks. The
     * per-key loader collapses a concurrent burst for one workspace into a single verdict lookup.
     */
    boolean isBlocked(Long workspaceId) {
        if (workspaceId == null) {
            return false;
        }
        return Boolean.TRUE.equals(
            blockedByWorkspace.get(workspaceId, id -> budgetService.blockReason(id) != LlmBudgetBlockReason.NONE)
        );
    }
}
