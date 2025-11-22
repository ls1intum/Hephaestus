package de.tum.in.www1.hephaestus.workspace.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class WorkspaceAccessEvaluatorTest {

    private WorkspaceAccessEvaluator accessEvaluator;

    @BeforeEach
    void setUp() {
        accessEvaluator = new WorkspaceAccessEvaluator();
    }

    @AfterEach
    void tearDown() {
        WorkspaceContextHolder.clearContext();
    }

    @Test
    void hasRole_WithNoContext_ReturnsFalse() {
        // Given: No workspace context is set
        // When & Then
        assertThat(accessEvaluator.hasRole(WorkspaceRole.MEMBER)).isFalse();
    }

    @Test
    void hasRole_WithEmptyRoles_ReturnsFalse() {
        // Given: Context with no roles
        WorkspaceContext context = new WorkspaceContext(1L, "test", "Test", AccountType.ORG, 123L, Set.of());
        WorkspaceContextHolder.setContext(context);

        // When & Then
        assertThat(accessEvaluator.hasRole(WorkspaceRole.MEMBER)).isFalse();
    }

    @Test
    void hasRole_WithMemberRole_AllowsMemberAccess() {
        // Given: User has MEMBER role
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.MEMBER)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then
        assertThat(accessEvaluator.hasRole(WorkspaceRole.MEMBER)).isTrue();
        assertThat(accessEvaluator.hasRole(WorkspaceRole.ADMIN)).isFalse();
        assertThat(accessEvaluator.hasRole(WorkspaceRole.OWNER)).isFalse();
    }

    @Test
    void hasRole_WithAdminRole_AllowsAdminAndMemberAccess() {
        // Given: User has ADMIN role
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.ADMIN)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then: Admin satisfies MEMBER and ADMIN requirements
        assertThat(accessEvaluator.hasRole(WorkspaceRole.MEMBER)).isTrue();
        assertThat(accessEvaluator.hasRole(WorkspaceRole.ADMIN)).isTrue();
        assertThat(accessEvaluator.hasRole(WorkspaceRole.OWNER)).isFalse();
    }

    @Test
    void hasRole_WithOwnerRole_AllowsAllAccess() {
        // Given: User has OWNER role
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.OWNER)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then: Owner satisfies all role requirements
        assertThat(accessEvaluator.hasRole(WorkspaceRole.MEMBER)).isTrue();
        assertThat(accessEvaluator.hasRole(WorkspaceRole.ADMIN)).isTrue();
        assertThat(accessEvaluator.hasRole(WorkspaceRole.OWNER)).isTrue();
    }

    @Test
    void isOwner_WithOwnerRole_ReturnsTrue() {
        // Given
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.OWNER)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then
        assertThat(accessEvaluator.isOwner()).isTrue();
    }

    @Test
    void isOwner_WithAdminRole_ReturnsFalse() {
        // Given
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.ADMIN)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then
        assertThat(accessEvaluator.isOwner()).isFalse();
    }

    @Test
    void isAdmin_WithAdminRole_ReturnsTrue() {
        // Given
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.ADMIN)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then
        assertThat(accessEvaluator.isAdmin()).isTrue();
    }

    @Test
    void isAdmin_WithOwnerRole_ReturnsTrue() {
        // Given: Owner should also satisfy admin checks
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.OWNER)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then
        assertThat(accessEvaluator.isAdmin()).isTrue();
    }

    @Test
    void isMember_WithAnyRole_ReturnsTrue() {
        // Given: Any role should satisfy member check
        WorkspaceContext memberContext = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.MEMBER)
        );

        // When & Then: MEMBER
        WorkspaceContextHolder.setContext(memberContext);
        assertThat(accessEvaluator.isMember()).isTrue();

        // Given: ADMIN
        WorkspaceContext adminContext = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.ADMIN)
        );
        WorkspaceContextHolder.setContext(adminContext);
        assertThat(accessEvaluator.isMember()).isTrue();

        // Given: OWNER
        WorkspaceContext ownerContext = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.OWNER)
        );
        WorkspaceContextHolder.setContext(ownerContext);
        assertThat(accessEvaluator.isMember()).isTrue();
    }

    @Test
    void canManageRole_AsOwner_CanManageAllRoles() {
        // Given: User is OWNER
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.OWNER)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then: OWNER can manage all roles
        assertThat(accessEvaluator.canManageRole(WorkspaceRole.MEMBER)).isTrue();
        assertThat(accessEvaluator.canManageRole(WorkspaceRole.ADMIN)).isTrue();
        assertThat(accessEvaluator.canManageRole(WorkspaceRole.OWNER)).isTrue();
    }

    @Test
    void canManageRole_AsAdmin_CannotManageOwner() {
        // Given: User is ADMIN
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.ADMIN)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then: ADMIN can manage MEMBER and ADMIN, but not OWNER
        assertThat(accessEvaluator.canManageRole(WorkspaceRole.MEMBER)).isTrue();
        assertThat(accessEvaluator.canManageRole(WorkspaceRole.ADMIN)).isTrue();
        assertThat(accessEvaluator.canManageRole(WorkspaceRole.OWNER)).isFalse();
    }

    @Test
    void canManageRole_AsMember_CannotManageAnyRole() {
        // Given: User is MEMBER
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.MEMBER)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then: MEMBER cannot manage any roles
        assertThat(accessEvaluator.canManageRole(WorkspaceRole.MEMBER)).isFalse();
        assertThat(accessEvaluator.canManageRole(WorkspaceRole.ADMIN)).isFalse();
        assertThat(accessEvaluator.canManageRole(WorkspaceRole.OWNER)).isFalse();
    }

    @Test
    void canManageRole_WithNoContext_ReturnsFalse() {
        // Given: No context
        // When & Then
        assertThat(accessEvaluator.canManageRole(WorkspaceRole.MEMBER)).isFalse();
    }

    @Test
    void hasPermission_IsAliasForHasRole() {
        // Given
        WorkspaceContext context = new WorkspaceContext(
            1L,
            "test",
            "Test",
            AccountType.ORG,
            123L,
            Set.of(WorkspaceRole.ADMIN)
        );
        WorkspaceContextHolder.setContext(context);

        // When & Then: hasPermission behaves same as hasRole
        assertThat(accessEvaluator.hasPermission(WorkspaceRole.MEMBER)).isEqualTo(
            accessEvaluator.hasRole(WorkspaceRole.MEMBER)
        );
        assertThat(accessEvaluator.hasPermission(WorkspaceRole.ADMIN)).isEqualTo(
            accessEvaluator.hasRole(WorkspaceRole.ADMIN)
        );
        assertThat(accessEvaluator.hasPermission(WorkspaceRole.OWNER)).isEqualTo(
            accessEvaluator.hasRole(WorkspaceRole.OWNER)
        );
    }
}
