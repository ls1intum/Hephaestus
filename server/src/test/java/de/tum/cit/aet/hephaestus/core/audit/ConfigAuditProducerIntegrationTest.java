package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditAction;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditFilter;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipService;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceSettingsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * One test per producer wired on this branch, asserting a row actually lands.
 *
 * <p>Not redundant with the endpoint tests: {@code ConfigAuditRecorder} throws when it runs outside a
 * writable transaction, so a producer wired into a read-only or non-transactional path takes its
 * endpoint down with a 500 rather than merely failing to record. Only executing each path proves it.
 * The recorder also drops an UPDATE whose diff is empty — correct for a no-op, and silent when the
 * snapshot was built so that it never differs, which is exactly how the token producer shipped broken.
 */
class ConfigAuditProducerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WorkspaceMembershipService membershipService;

    @Autowired
    private WorkspaceSettingsService settingsService;

    @Autowired
    private ConfigAuditEventRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void grantingAndThenChangingARoleIsRecorded() {
        Workspace workspace = workspace("audit-producer-role");
        User member = persistUser("audit-producer-role-member");

        membershipService.assignRole(workspace.getId(), member.getId(), WorkspaceMembership.WorkspaceRole.MEMBER);
        membershipService.assignRole(workspace.getId(), member.getId(), WorkspaceMembership.WorkspaceRole.ADMIN);

        assertThat(actionsFor(workspace, ConfigAuditEntityType.WORKSPACE_ROLE)).containsExactly(
            ConfigAuditAction.CREATED,
            ConfigAuditAction.UPDATED
        );
    }

    @Test
    @Transactional
    void revokingAMembershipIsRecorded() {
        Workspace workspace = workspace("audit-producer-revoke");
        User member = persistUser("audit-producer-revoke-member");
        membershipService.assignRole(workspace.getId(), member.getId(), WorkspaceMembership.WorkspaceRole.ADMIN);

        membershipService.removeMembership(workspace.getId(), member.getId());

        assertThat(actionsFor(workspace, ConfigAuditEntityType.WORKSPACE_ROLE)).containsExactly(
            ConfigAuditAction.CREATED,
            ConfigAuditAction.DELETED
        );
    }

    @Test
    @Transactional
    void hidingAMemberIsRecordedBecauseItOutlivesOrgRemoval() {
        Workspace workspace = workspace("audit-producer-hidden");
        User member = persistUser("audit-producer-hidden-member");
        membershipService.assignRole(workspace.getId(), member.getId(), WorkspaceMembership.WorkspaceRole.MEMBER);

        membershipService.updateMemberVisibility(workspace.getId(), member.getId(), true);

        assertThat(snapshotsFor(workspace, ConfigAuditEntityType.WORKSPACE_ROLE))
            .as("the hidden flag decides whether access survives leaving the org, so it must be legible")
            .anyMatch(newValue -> newValue.contains("\"hidden\":true"));
    }

    @Test
    @Transactional
    void aRoleChangedByOrgSyncIsRecorded() {
        Workspace workspace = workspace("audit-producer-sync");
        User member = persistUser("audit-producer-sync-member");
        membershipService.assignRole(workspace.getId(), member.getId(), WorkspaceMembership.WorkspaceRole.MEMBER);

        membershipService.syncWorkspaceMembers(
            workspace,
            Map.of(member.getId(), WorkspaceMembership.WorkspaceRole.ADMIN)
        );

        assertThat(actionsFor(workspace, ConfigAuditEntityType.WORKSPACE_ROLE))
            .as("'when did X become ADMIN' must not answer from admin-initiated rows alone")
            .contains(ConfigAuditAction.UPDATED);
    }

    @Test
    @Transactional
    void togglingPublicVisibilityIsRecorded() {
        Workspace workspace = workspace("audit-producer-visibility");

        settingsService.updatePublicVisibility(workspace.getId(), true);

        assertThat(actionsFor(workspace, ConfigAuditEntityType.WORKSPACE_VISIBILITY)).containsExactly(
            ConfigAuditAction.UPDATED
        );
    }

    @Test
    @Transactional
    void aNoOpVisibilityUpdateIsNotRecorded() {
        Workspace workspace = workspace("audit-producer-noop");
        settingsService.updatePublicVisibility(workspace.getId(), true);

        settingsService.updatePublicVisibility(workspace.getId(), true);

        assertThat(actionsFor(workspace, ConfigAuditEntityType.WORKSPACE_VISIBILITY))
            .as("re-submitting a settings form unchanged should not add a row that says nothing")
            .containsExactly(ConfigAuditAction.UPDATED);
    }

    private Workspace workspace(String slug) {
        Workspace workspace = createWorkspace(
            slug,
            "Audit Workspace",
            slug + "-org",
            AccountType.ORG,
            persistUser(slug + "-owner")
        );
        ensureAdminMembership(workspace);
        return workspace;
    }

    private List<ConfigAuditAction> actionsFor(Workspace workspace, ConfigAuditEntityType entityType) {
        return rowsFor(workspace, entityType).stream().map(ConfigAuditEvent::getAction).toList();
    }

    private List<String> snapshotsFor(Workspace workspace, ConfigAuditEntityType entityType) {
        return rowsFor(workspace, entityType)
            .stream()
            .map(row -> row.getNewValue() == null ? "" : row.getNewValue())
            .toList();
    }

    /**
     * Read through the workspace-scoped query the application itself uses. {@code findAll()} returns
     * nothing here: the table is registered workspace-scoped, so the tenancy filter empties an
     * unscoped read when no workspace context is bound — which reads as "the producer wrote nothing".
     */
    private List<ConfigAuditEvent> rowsFor(Workspace workspace, ConfigAuditEntityType entityType) {
        entityManager.flush();
        var filter = new ConfigAuditFilter(List.of(entityType), null, null, null, null, null, null);
        return repository
            .findForWorkspace(workspace.getId(), filter, PageRequest.of(0, 50))
            .getContent()
            .stream()
            .sorted(java.util.Comparator.comparing(ConfigAuditEvent::getId))
            .toList();
    }
}
