package de.tum.in.www1.hephaestus.workspace.context;

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

    private WorkspaceContextHolder() {
        // Utility class
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
                "Overwriting existing WorkspaceContext. Potential context leak. " + "existing={}, new={}",
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
