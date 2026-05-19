package de.tum.cit.aet.hephaestus.workspace.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * ThreadLocal holder for workspace context with MDC enrichment.
 * Ensures workspace isolation and provides observability through structured logging.
 */
public final class WorkspaceContextHolder {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceContextHolder.class);

    private static final String MDC_WORKSPACE_ID = "workspace_id";
    private static final String MDC_WORKSPACE_SLUG = "workspace_slug";
    private static final String MDC_INSTALLATION_ID = "installation_id";

    private static final ThreadLocal<WorkspaceContext> contextHolder = new ThreadLocal<>();

    /**
     * Counts nested bypass scopes opened by {@code @WorkspaceAgnostic} (or programmatic
     * {@link #openBypass(String)} calls). When the depth is &gt; 0, the
     * {@code WorkspaceStatementInspector} treats emitted SQL as exempt from
     * {@code workspace_id} enforcement. Depth-counted so nested calls are safe.
     */
    private static final ThreadLocal<Integer> bypassDepth = ThreadLocal.withInitial(() -> 0);

    private WorkspaceContextHolder() {
        // Utility class
    }

    /**
     * Open a workspace-agnostic bypass scope on the current thread. SQL emitted while the
     * returned {@link AutoCloseable} is open will NOT be flagged by
     * {@code WorkspaceStatementInspector}.
     *
     * <p>Used by {@code WorkspaceAgnosticAspect} and rare admin operations that
     * legitimately query across workspaces. Always use with try-with-resources so the
     * depth is decremented even on exception.
     *
     * @param reason logged at DEBUG for traceability
     */
    public static AutoCloseable openBypass(String reason) {
        int depth = bypassDepth.get() + 1;
        bypassDepth.set(depth);
        if (log.isDebugEnabled()) {
            log.debug("Workspace-agnostic bypass opened (depth={}): {}", depth, reason);
        }
        return () -> {
            int current = bypassDepth.get();
            if (current <= 1) {
                bypassDepth.remove();
            } else {
                bypassDepth.set(current - 1);
            }
        };
    }

    /** True if the current thread is inside one or more open bypass scopes. */
    public static boolean isBypassActive() {
        return bypassDepth.get() > 0;
    }

    /**
     * Set the workspace context for the current thread.
     * Enriches MDC with workspace metadata for logging.
     * Warns if a context is already set (potential context leak).
     *
     * @param context WorkspaceContext to set
     */
    public static void setContext(WorkspaceContext context) {
        WorkspaceContext existing = contextHolder.get();
        if (existing != null && !existing.equals(context)) {
            log.warn(
                "Detected context overwrite: existingSlug={}, newSlug={}",
                existing.slug(),
                context != null ? context.slug() : "null"
            );
        }

        contextHolder.set(context);

        if (context != null) {
            enrichMDC(context);
        } else {
            clearMDC();
        }
    }

    /**
     * Get the workspace context for the current thread.
     *
     * @return WorkspaceContext or null if not set
     */
    public static WorkspaceContext getContext() {
        return contextHolder.get();
    }

    /**
     * Clear the workspace context and MDC for the current thread.
     * Must be called in finally blocks to prevent context leaks.
     */
    public static void clearContext() {
        contextHolder.remove();
        clearMDC();
    }

    /**
     * Enrich MDC with workspace metadata for structured logging.
     *
     * @param context WorkspaceContext to extract metadata from
     */
    private static void enrichMDC(WorkspaceContext context) {
        MDC.put(MDC_WORKSPACE_ID, String.valueOf(context.id()));
        MDC.put(MDC_WORKSPACE_SLUG, context.slug());

        if (context.installationId() != null) {
            MDC.put(MDC_INSTALLATION_ID, String.valueOf(context.installationId()));
        } else {
            MDC.remove(MDC_INSTALLATION_ID);
        }
    }

    /**
     * Clear workspace-related MDC keys.
     */
    private static void clearMDC() {
        MDC.remove(MDC_WORKSPACE_ID);
        MDC.remove(MDC_WORKSPACE_SLUG);
        MDC.remove(MDC_INSTALLATION_ID);
    }
}
