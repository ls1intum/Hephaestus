package de.tum.in.www1.hephaestus.workspace.context;

import static org.junit.jupiter.api.Assertions.*;

import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

@Tag("unit")
@DisplayName("WorkspaceContextExecutor Unit Tests")
class WorkspaceContextExecutorTest {

    @AfterEach
    void cleanup() {
        WorkspaceContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("Should propagate context to wrapped Runnable")
    void shouldPropagateContextToRunnable() throws Exception {
        // Arrange
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test-workspace",
            "Test",
            AccountType.ORG,
            100L,
            Set.of(WorkspaceRole.OWNER)
        );
        WorkspaceContextHolder.setContext(context);

        AtomicReference<WorkspaceContext> capturedContext = new AtomicReference<>();

        // Act
        Runnable wrapped = WorkspaceContextExecutor.wrap(() -> capturedContext.set(WorkspaceContextHolder.getContext())
        );

        // Clear context in main thread to simulate async boundary
        WorkspaceContextHolder.clearContext();

        // Execute in different thread
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(wrapped);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert
        WorkspaceContext captured = capturedContext.get();
        assertNotNull(captured);
        assertEquals("test-workspace", captured.slug());
        assertEquals(1L, captured.id());
    }

    @Test
    @DisplayName("Should propagate context to wrapped Callable")
    void shouldPropagateContextToCallable() throws Exception {
        // Arrange
        WorkspaceContext workspaceContext = new WorkspaceContext(
            42L,
            "callable-test",
            "Test",
            AccountType.USER,
            null,
            Set.of()
        );
        WorkspaceContextHolder.setContext(workspaceContext);

        // Act
        Callable<String> wrapped = WorkspaceContextExecutor.wrap(() -> {
            WorkspaceContext wrappedWorkspaceContext = WorkspaceContextHolder.getContext();
            return wrappedWorkspaceContext != null ? wrappedWorkspaceContext.slug() : "null";
        });

        // Clear context in main thread
        WorkspaceContextHolder.clearContext();

        // Execute in different thread
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(wrapped);
        String result = future.get();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert
        assertEquals("callable-test", result);
    }

    @Test
    @DisplayName("Should propagate MDC to wrapped Runnable")
    void shouldPropagateMDCToRunnable() throws Exception {
        // Arrange
        WorkspaceContext context = new WorkspaceContext(99L, "mdc-test", "Test", AccountType.ORG, 777L, Set.of());
        WorkspaceContextHolder.setContext(context);

        AtomicReference<String> capturedWorkspaceId = new AtomicReference<>();
        AtomicReference<String> capturedSlug = new AtomicReference<>();

        // Act
        Runnable wrapped = WorkspaceContextExecutor.wrap(() -> {
            capturedWorkspaceId.set(MDC.get("workspace_id"));
            capturedSlug.set(MDC.get("workspace_slug"));
        });

        WorkspaceContextHolder.clearContext();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(wrapped);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert
        assertEquals("99", capturedWorkspaceId.get());
        assertEquals("mdc-test", capturedSlug.get());
    }

    @Test
    @DisplayName("Should clean up context after Runnable execution")
    void shouldCleanupContextAfterRunnableExecution() throws Exception {
        // Arrange
        WorkspaceContext context = new WorkspaceContext(1L, "cleanup-test", "Test", AccountType.ORG, null, Set.of());
        WorkspaceContextHolder.setContext(context);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WorkspaceContext> contextAfterExecution = new AtomicReference<>();

        // Act
        Runnable wrapped = WorkspaceContextExecutor.wrap(() -> {
            // Do some work
            assertNotNull(WorkspaceContextHolder.getContext());
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            wrapped.run();
            // Check context after wrapped runnable completes
            contextAfterExecution.set(WorkspaceContextHolder.getContext());
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - Context should be cleaned up
        assertNull(contextAfterExecution.get());
    }

    @Test
    @DisplayName("Should handle null context gracefully")
    void shouldHandleNullContextGracefully() throws Exception {
        // Arrange - No context set
        AtomicReference<WorkspaceContext> capturedContext = new AtomicReference<>();

        // Act
        Runnable wrapped = WorkspaceContextExecutor.wrap(() -> {
            capturedContext.set(WorkspaceContextHolder.getContext());
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(wrapped);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert
        assertNull(capturedContext.get());
    }

    @Test
    @DisplayName("Should throw exception when wrapping null Runnable")
    void shouldThrowExceptionForNullRunnable() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            WorkspaceContextExecutor.wrap((Runnable) null);
        });
    }

    @Test
    @DisplayName("Should throw exception when wrapping null Callable")
    void shouldThrowExceptionForNullCallable() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            WorkspaceContextExecutor.wrap((Callable<?>) null);
        });
    }

    @Test
    @DisplayName("Should not leak context between multiple wrapped executions")
    void shouldNotLeakContextBetweenExecutions() throws Exception {
        // Arrange
        WorkspaceContext context1 = new WorkspaceContext(1L, "ws1", "WS1", AccountType.ORG, null, Set.of());
        WorkspaceContext context2 = new WorkspaceContext(2L, "ws2", "WS2", AccountType.USER, null, Set.of());

        AtomicReference<String> capturedSlug1 = new AtomicReference<>();
        AtomicReference<String> capturedSlug2 = new AtomicReference<>();

        // Act - Wrap two different contexts
        WorkspaceContextHolder.setContext(context1);
        Runnable wrapped1 = WorkspaceContextExecutor.wrap(() -> {
            capturedSlug1.set(WorkspaceContextHolder.getContext().slug());
        });

        WorkspaceContextHolder.setContext(context2);
        Runnable wrapped2 = WorkspaceContextExecutor.wrap(() -> {
            capturedSlug2.set(WorkspaceContextHolder.getContext().slug());
        });

        WorkspaceContextHolder.clearContext();

        // Execute both in same thread pool
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(wrapped1);
        executor.submit(wrapped2);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - Each should capture its own context
        assertEquals("ws1", capturedSlug1.get());
        assertEquals("ws2", capturedSlug2.get());
    }

    @Test
    @DisplayName("Should restore previous context and MDC state after execution")
    void shouldRestorePreviousContextAndMdcAfterExecution() throws Exception {
        WorkspaceContext previousContext = new WorkspaceContext(10L, "base", "Base", AccountType.ORG, null, Set.of());
        WorkspaceContext wrappedContext = new WorkspaceContext(
            11L,
            "wrapped",
            "Wrapped",
            AccountType.USER,
            null,
            Set.of()
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WorkspaceContext> contextAfter = new AtomicReference<>();
        AtomicReference<String> preservedValue = new AtomicReference<>();
        AtomicReference<String> wrappedOnlyValue = new AtomicReference<>();

        executor.submit(() -> {
            WorkspaceContextHolder.setContext(previousContext);
            MDC.put("request-id", "abc-123");
        });

        WorkspaceContextHolder.setContext(wrappedContext);
        Runnable wrapped = WorkspaceContextExecutor.wrap(() -> {
            assertEquals("wrapped", WorkspaceContextHolder.getContext().slug());
            MDC.put("wrapped-only", "temp");
        });
        WorkspaceContextHolder.clearContext();

        executor.submit(wrapped);

        executor.submit(() -> {
            contextAfter.set(WorkspaceContextHolder.getContext());
            preservedValue.set(MDC.get("request-id"));
            wrappedOnlyValue.set(MDC.get("wrapped-only"));
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        WorkspaceContext restored = contextAfter.get();
        assertNotNull(restored);
        assertEquals("base", restored.slug());
        assertEquals("abc-123", preservedValue.get());
        assertNull(wrappedOnlyValue.get());
    }
}
