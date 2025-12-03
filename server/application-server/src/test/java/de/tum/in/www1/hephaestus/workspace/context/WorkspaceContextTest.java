package de.tum.in.www1.hephaestus.workspace.context;

import static org.junit.jupiter.api.Assertions.*;

import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("WorkspaceContext Unit Tests")
class WorkspaceContextTest {

    @Test
    @DisplayName("Should create context from workspace entity")
    void shouldCreateContextFromWorkspaceEntity() {
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(42L);
        workspace.setWorkspaceSlug("test-workspace");
        workspace.setDisplayName("Test Workspace");
        workspace.setAccountType(AccountType.ORG);
        workspace.setInstallationId(123L);

        Set<WorkspaceRole> roles = Set.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN);

        // Act
        WorkspaceContext context = WorkspaceContext.fromWorkspace(workspace, roles);

        // Assert
        assertEquals(42L, context.id());
        assertEquals("test-workspace", context.slug());
        assertEquals("Test Workspace", context.displayName());
        assertEquals(AccountType.ORG, context.accountType());
        assertEquals(123L, context.installationId());
        assertEquals(2, context.roles().size());
        assertTrue(context.hasRole(WorkspaceRole.OWNER));
        assertTrue(context.hasRole(WorkspaceRole.ADMIN));
    }

    @Test
    @DisplayName("Should handle null roles as empty set")
    void shouldHandleNullRolesAsEmptySet() {
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("test");
        workspace.setDisplayName("Test");
        workspace.setAccountType(AccountType.USER);

        // Act
        WorkspaceContext context = WorkspaceContext.fromWorkspace(workspace, null);

        // Assert
        assertNotNull(context.roles());
        assertTrue(context.roles().isEmpty());
        assertFalse(context.hasMembership());
    }

    @Test
    @DisplayName("Should check if user has specific role")
    void shouldCheckIfUserHasSpecificRole() {
        // Arrange
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            null,
            false,
            Set.of(WorkspaceRole.MEMBER)
        );

        // Assert
        assertTrue(context.hasRole(WorkspaceRole.MEMBER));
        assertFalse(context.hasRole(WorkspaceRole.OWNER));
        assertFalse(context.hasRole(WorkspaceRole.ADMIN));
    }

    @Test
    @DisplayName("Should check if user has any membership")
    void shouldCheckIfUserHasAnyMembership() {
        // Arrange
        WorkspaceContext withRoles = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            null,
            false,
            Set.of(WorkspaceRole.MEMBER)
        );

        WorkspaceContext withoutRoles = new WorkspaceContext(
            2L,
            "test2",
            "Test2",
            AccountType.USER,
            null,
            false,
            Set.of()
        );

        // Assert
        assertTrue(withRoles.hasMembership());
        assertFalse(withoutRoles.hasMembership());
    }

    @Test
    @DisplayName("Should handle null installation ID")
    void shouldHandleNullInstallationId() {
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("test");
        workspace.setDisplayName("Test");
        workspace.setAccountType(AccountType.USER);
        workspace.setInstallationId(null);

        // Act
        WorkspaceContext context = WorkspaceContext.fromWorkspace(workspace, Set.of());

        // Assert
        assertNull(context.installationId());
    }

    @Test
    @DisplayName("Should support multiple roles")
    void shouldSupportMultipleRoles() {
        // Arrange
        Set<WorkspaceRole> roles = Set.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.MEMBER);

        // Act
        WorkspaceContext context = new WorkspaceContext(1L, "test", "Test", AccountType.ORG, null, false, roles);

        // Assert
        assertTrue(context.hasRole(WorkspaceRole.OWNER));
        assertTrue(context.hasRole(WorkspaceRole.ADMIN));
        assertTrue(context.hasRole(WorkspaceRole.MEMBER));
        assertTrue(context.hasMembership());
    }

    @Test
    @DisplayName("Should be immutable record")
    void shouldBeImmutableRecord() {
        // Arrange
        Set<WorkspaceRole> roles = Set.of(WorkspaceRole.OWNER);
        WorkspaceContext context = new WorkspaceContext(1L, "test", "Test", AccountType.ORG, 100L, false, roles);

        // Act - Try to get roles and verify they're the same set
        Set<WorkspaceRole> retrievedRoles = context.roles();

        // Assert - Record properties should be accessible
        assertEquals(1L, context.id());
        assertEquals("test", context.slug());
        assertEquals("Test", context.displayName());
        assertEquals(AccountType.ORG, context.accountType());
        assertEquals(100L, context.installationId());
        assertSame(roles, retrievedRoles);
    }
}
