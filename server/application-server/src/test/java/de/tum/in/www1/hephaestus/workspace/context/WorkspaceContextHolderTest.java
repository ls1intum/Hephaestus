package de.tum.in.www1.hephaestus.workspace.context;

import static org.junit.jupiter.api.Assertions.*;

import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

@Tag("unit")
@DisplayName("WorkspaceContextHolder Unit Tests")
class WorkspaceContextHolderTest {

    @AfterEach
    void cleanup() {
        WorkspaceContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("Should store and retrieve context from ThreadLocal")
    void shouldStoreAndRetrieveContext() {
        // Arrange
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test-workspace",
            "Test Workspace",
            AccountType.ORG,
            123L,
            false,
            Set.of(WorkspaceRole.OWNER)
        );

        // Act
        WorkspaceContextHolder.setContext(context);
        WorkspaceContext retrieved = WorkspaceContextHolder.getContext();

        // Assert
        assertNotNull(retrieved);
        assertEquals(1L, retrieved.id());
        assertEquals("test-workspace", retrieved.slug());
        assertEquals("Test Workspace", retrieved.displayName());
        assertEquals(AccountType.ORG, retrieved.accountType());
        assertEquals(123L, retrieved.installationId());
        assertTrue(retrieved.hasRole(WorkspaceRole.OWNER));
    }

    @Test
    @DisplayName("Should enrich MDC with workspace metadata")
    void shouldEnrichMDC() {
        // Arrange
        WorkspaceContext context = new WorkspaceContext(
            42L,
            "test-slug",
            "Test",
            AccountType.USER,
            999L,
            false,
            Set.of()
        );

        // Act
        WorkspaceContextHolder.setContext(context);

        // Assert
        assertEquals("42", MDC.get("workspace_id"));
        assertEquals("test-slug", MDC.get("workspace_slug"));
        assertEquals("999", MDC.get("installation_id"));
    }

    @Test
    @DisplayName("Should clear context and MDC")
    void shouldClearContextAndMDC() {
        // Arrange
        WorkspaceContext context = new WorkspaceContext(1L, "test", "Test", AccountType.ORG, 100L, false, Set.of());
        WorkspaceContextHolder.setContext(context);

        // Act
        WorkspaceContextHolder.clearContext();

        // Assert
        assertNull(WorkspaceContextHolder.getContext());
        assertNull(MDC.get("workspace_id"));
        assertNull(MDC.get("workspace_slug"));
        assertNull(MDC.get("installation_id"));
    }

    @Test
    @DisplayName("Should handle null installation ID in MDC")
    void shouldHandleNullInstallationId() {
        // Arrange
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            null, // No installation ID
            false,
            Set.of()
        );

        // Act
        WorkspaceContextHolder.setContext(context);

        // Assert
        assertEquals("1", MDC.get("workspace_id"));
        assertEquals("test", MDC.get("workspace_slug"));
        assertNull(MDC.get("installation_id"));
    }

    @Test
    @DisplayName("Should isolate context between threads")
    void shouldIsolateContextBetweenThreads() throws InterruptedException {
        // Arrange
        WorkspaceContext mainContext = new WorkspaceContext(
            1L,
            "main-workspace",
            "Main",
            AccountType.ORG,
            100L,
            false,
            Set.of(WorkspaceRole.OWNER)
        );

        WorkspaceContext otherContext = new WorkspaceContext(
            2L,
            "other-workspace",
            "Other",
            AccountType.USER,
            200L,
            false,
            Set.of(WorkspaceRole.MEMBER)
        );

        WorkspaceContextHolder.setContext(mainContext);

        // Act - Create another thread and set different context
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            WorkspaceContextHolder.setContext(otherContext);
            WorkspaceContext retrieved = WorkspaceContextHolder.getContext();

            // Assert in other thread
            assertEquals("other-workspace", retrieved.slug());
            assertEquals(2L, retrieved.id());

            WorkspaceContextHolder.clearContext();
        });

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - Main thread context should be unchanged
        WorkspaceContext mainRetrieved = WorkspaceContextHolder.getContext();
        assertNotNull(mainRetrieved);
        assertEquals("main-workspace", mainRetrieved.slug());
        assertEquals(1L, mainRetrieved.id());
    }

    @Test
    @DisplayName("Should return null when no context is set")
    void shouldReturnNullWhenNoContextSet() {
        // Act
        WorkspaceContext context = WorkspaceContextHolder.getContext();

        // Assert
        assertNull(context);
    }

    @Test
    @DisplayName("Should handle setting null context")
    void shouldHandleSettingNullContext() {
        // Arrange
        WorkspaceContext context = new WorkspaceContext(1L, "test", "Test", AccountType.ORG, 100L, false, Set.of());
        WorkspaceContextHolder.setContext(context);

        // Act
        WorkspaceContextHolder.setContext(null);

        // Assert
        assertNull(WorkspaceContextHolder.getContext());
        assertNull(MDC.get("workspace_id"));
        assertNull(MDC.get("workspace_slug"));
    }
}
