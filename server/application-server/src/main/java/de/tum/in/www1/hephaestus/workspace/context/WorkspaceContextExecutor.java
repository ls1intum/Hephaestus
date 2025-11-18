package de.tum.in.www1.hephaestus.workspace.context;

import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.MDC;

/**
 * Utility for propagating workspace context across async boundaries.
 * Captures ThreadLocal context and MDC from the calling thread and restores them
 * in the async execution thread.
 *
 * <p><strong>Why this class exists:</strong>
 * <p>ThreadLocal storage (like {@link WorkspaceContextHolder}) is bound to a specific thread.
 * When you spawn async tasks using {@code CompletableFuture.runAsync()}, {@code @Async} methods,
 * or custom thread pools, the execution happens on a different thread where the ThreadLocal
 * context is not available. This leads to:
 * <ul>
 *   <li>Lost workspace isolation (operations might target wrong workspace)</li>
 *   <li>Missing MDC context in logs (can't trace which workspace caused errors)</li>
 *   <li>Authorization failures (workspace roles not available)</li>
 * </ul>
 *
 * <p><strong>When you'll need this:</strong>
 * <ul>
 *   <li>Background jobs triggered from workspace-scoped endpoints (e.g., bulk sync operations)</li>
 *   <li>Workspace-specific scheduled tasks that process data per-workspace</li>
 *   <li>NATS consumers that need to maintain workspace context across message handling</li>
 *   <li>Long-running operations offloaded to thread pools (e.g., report generation)</li>
 *   <li>Spring {@code @Async} methods called from workspace-scoped controllers</li>
 * </ul>
 *
 * <p><strong>Current status:</strong> Not actively used yet, but infrastructure is in place
 * for when async workspace-scoped operations are needed as the application scales.
 *
 * <p>Usage examples:
 * <pre>
 * // With CompletableFuture
 * CompletableFuture.runAsync(WorkspaceContextExecutor.wrap(() -> {
 *     // Workspace context available here
 *     syncService.performHeavyOperation();
 * }));
 *
 * // With @Async methods
 * {@literal @}Async
 * public void processInBackground() {
 *     // Without wrapper, context would be lost
 * }
 *
 * // Wrap the caller instead:
 * executor.submit(WorkspaceContextExecutor.wrap(() -> {
 *     asyncService.processInBackground(); // Context propagated
 * }));
 *
 * // With custom executor
 * ExecutorService executor = Executors.newFixedThreadPool(10);
 * executor.submit(WorkspaceContextExecutor.wrap(() -> {
 *     return computeWorkspaceReport();
 * }));
 * </pre>
 */
public final class WorkspaceContextExecutor {

    private WorkspaceContextExecutor() {
        // Utility class
    }

    /**
     * Wraps a Runnable to propagate workspace context to async execution.
     * Captures current ThreadLocal context and MDC at wrap time, then restores
     * them when the runnable executes.
     *
     * @param runnable Runnable to wrap
     * @return Wrapped runnable that propagates context
     * @throws IllegalArgumentException if runnable is null
     */
    public static Runnable wrap(Runnable runnable) {
        if (runnable == null) {
            throw new IllegalArgumentException("Runnable must not be null");
        }

        // Capture context from calling thread
        WorkspaceContext capturedContext = WorkspaceContextHolder.getContext();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();

        return () -> {
            // Restore context in execution thread
            WorkspaceContextHolder.setContext(capturedContext);
            if (capturedMdc != null) {
                MDC.setContextMap(capturedMdc);
            }

            try {
                runnable.run();
            } finally {
                // Clean up after execution
                WorkspaceContextHolder.clearContext();
            }
        };
    }

    /**
     * Wraps a Callable to propagate workspace context to async execution.
     * Captures current ThreadLocal context and MDC at wrap time, then restores
     * them when the callable executes.
     *
     * @param callable Callable to wrap
     * @param <T> Return type of the callable
     * @return Wrapped callable that propagates context
     * @throws IllegalArgumentException if callable is null
     */
    public static <T> Callable<T> wrap(Callable<T> callable) {
        if (callable == null) {
            throw new IllegalArgumentException("Callable must not be null");
        }

        // Capture context from calling thread
        WorkspaceContext capturedContext = WorkspaceContextHolder.getContext();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();

        return () -> {
            // Restore context in execution thread
            WorkspaceContextHolder.setContext(capturedContext);
            if (capturedMdc != null) {
                MDC.setContextMap(capturedMdc);
            }

            try {
                return callable.call();
            } finally {
                // Clean up after execution
                WorkspaceContextHolder.clearContext();
            }
        };
    }
}
