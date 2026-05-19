package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMemberRole;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembership;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapts organization membership events from gitprovider to workspace member syncing.
 * <p>
 * This implements the {@link OrganizationMembershipListener} SPI defined by gitprovider,
 * allowing the workspace module to react to organization membership changes without
 * gitprovider needing to know about workspace concepts.
 */
@Component
public class WorkspaceOrganizationMembershipAdapter implements OrganizationMembershipListener {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceOrganizationMembershipAdapter.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final OrganizationMembershipRepository organizationMembershipRepository;

    public WorkspaceOrganizationMembershipAdapter(
        WorkspaceRepository workspaceRepository,
        WorkspaceMembershipRepository workspaceMembershipRepository,
        WorkspaceMembershipService workspaceMembershipService,
        OrganizationMembershipRepository organizationMembershipRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceMembershipService = workspaceMembershipService;
        this.organizationMembershipRepository = organizationMembershipRepository;
    }

    @Override
    @Transactional
    public void onMemberAdded(MembershipChangedEvent event) {
        syncWorkspaceFromOrganization(event, "added");
    }

    @Override
    @Transactional
    public void onMemberRemoved(MembershipChangedEvent event) {
        syncWorkspaceFromOrganization(event, "removed");
    }

    @Override
    @Transactional
    public void onOrganizationMembershipsSynced(OrganizationSyncedEvent event) {
        Optional<Workspace> workspaceOpt = findWorkspaceByOrgLogin(event.organizationLogin());

        if (workspaceOpt.isEmpty()) {
            log.debug("Skipped member sync: reason=noWorkspaceForOrg, orgLogin={}", event.organizationLogin());
            return;
        }

        Workspace workspace = workspaceOpt.get();

        try {
            int synced = syncWorkspaceMembersFromOrganization(workspace, event.organizationId());
            log.info(
                "Synced workspace members after scheduled org sync: workspaceId={}, orgLogin={}, memberCount={}",
                workspace.getId(),
                event.organizationLogin(),
                synced
            );
        } catch (Exception e) {
            log.error(
                "Failed to sync workspace members after scheduled sync: workspaceId={}, orgLogin={}",
                workspace.getId(),
                event.organizationLogin(),
                e
            );
        }
    }

    private void syncWorkspaceFromOrganization(MembershipChangedEvent event, String action) {
        Optional<Workspace> workspaceOpt = findWorkspaceByOrgLogin(event.organizationLogin());

        if (workspaceOpt.isEmpty()) {
            log.debug(
                "Skipped member sync: reason=noWorkspaceForOrg, orgLogin={}, action={}",
                event.organizationLogin(),
                action
            );
            return;
        }

        Workspace workspace = workspaceOpt.get();

        try {
            int synced = syncWorkspaceMembersFromOrganization(workspace, event.organizationId());
            log.info(
                "Synced workspace members after member change: workspaceId={}, orgLogin={}, action={}, userLogin={}, memberCount={}",
                workspace.getId(),
                event.organizationLogin(),
                action,
                event.userLogin(),
                synced
            );
        } catch (Exception e) {
            log.error(
                "Failed to sync workspace members after member change: workspaceId={}, orgLogin={}, action={}",
                workspace.getId(),
                event.organizationLogin(),
                action,
                e
            );
            // Don't rethrow - org membership change succeeded, workspace sync is secondary
        }
    }

    private Optional<Workspace> findWorkspaceByOrgLogin(String organizationLogin) {
        return workspaceRepository
            .findByOrganization_Login(organizationLogin)
            .or(() -> workspaceRepository.findByAccountLoginIgnoreCase(organizationLogin));
    }

    /**
     * Synchronizes workspace members from organization members.
     * <p>
     * Maps organization membership roles to workspace membership roles:
     * <ul>
     *   <li>ADMIN -> ADMIN</li>
     *   <li>MEMBER -> MEMBER</li>
     * </ul>
     * Existing OWNER roles are preserved and not downgraded.
     *
     * @param workspace      the workspace to sync
     * @param organizationId the organization ID to sync members from
     * @return the number of members synced
     */
    private int syncWorkspaceMembersFromOrganization(Workspace workspace, Long organizationId) {
        Long workspaceId = workspace.getId();

        // Get current owners to preserve their role
        Set<Long> currentOwnerIds = workspaceMembershipRepository
            .findByWorkspace_Id(workspaceId)
            .stream()
            .filter(m -> m.getRole() == WorkspaceMembership.WorkspaceRole.OWNER)
            .filter(m -> m.getUser() != null)
            .map(m -> m.getUser().getId())
            .collect(Collectors.toSet());

        // Get organization memberships and map to workspace roles
        List<OrganizationMembership> orgMemberships = organizationMembershipRepository.findByOrganizationId(
            organizationId
        );

        if (orgMemberships.isEmpty()) {
            log.debug(
                "Skipped workspace member sync: reason=noOrgMembersFound, workspaceId={}, organizationId={}",
                workspaceId,
                organizationId
            );
            return 0;
        }

        // Build desired workspace memberships map, preserving existing owners
        Map<Long, WorkspaceMembership.WorkspaceRole> desiredRoles = new HashMap<>();
        for (OrganizationMembership orgMembership : orgMemberships) {
            Long userId = orgMembership.getUserId();

            // Preserve existing OWNER role - don't downgrade them
            if (currentOwnerIds.contains(userId)) {
                desiredRoles.put(userId, WorkspaceMembership.WorkspaceRole.OWNER);
            } else {
                // Map organization role to workspace role
                WorkspaceMembership.WorkspaceRole workspaceRole = mapOrgRoleToWorkspaceRole(orgMembership.getRole());
                desiredRoles.put(userId, workspaceRole);
            }
        }

        // Ensure we don't remove existing owners even if they're not in the org anymore
        for (Long ownerId : currentOwnerIds) {
            desiredRoles.putIfAbsent(ownerId, WorkspaceMembership.WorkspaceRole.OWNER);
        }

        // Sync workspace members
        workspaceMembershipService.syncWorkspaceMembers(workspace, desiredRoles);

        // Update sync timestamp
        workspace.setMembersSyncedAt(Instant.now());
        workspaceRepository.save(workspace);

        return desiredRoles.size();
    }

    private WorkspaceMembership.WorkspaceRole mapOrgRoleToWorkspaceRole(OrganizationMemberRole orgRole) {
        if (orgRole == OrganizationMemberRole.ADMIN) {
            return WorkspaceMembership.WorkspaceRole.ADMIN;
        }
        return WorkspaceMembership.WorkspaceRole.MEMBER;
    }
}
