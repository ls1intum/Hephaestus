package de.tum.cit.aet.hephaestus.integration.scm.workspace.adapter;

import de.tum.cit.aet.hephaestus.integration.core.spi.OrganizationMembershipListener;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationMemberRole;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationMembership;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationMembershipRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.membership.TeamMembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipService;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceActorSelector;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkspaceOrganizationMembershipAdapter implements OrganizationMembershipListener {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceOrganizationMembershipAdapter.class);

    private final WorkspaceRepository workspaceRepository;
    private final MembershipService workspaceMembershipService;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final WorkspaceActorSelector actorSelector;

    public WorkspaceOrganizationMembershipAdapter(
            WorkspaceRepository workspaceRepository,
            MembershipService workspaceMembershipService,
            OrganizationMembershipRepository organizationMembershipRepository,
            OrganizationRepository organizationRepository,
            TeamMembershipRepository teamMembershipRepository,
            WorkspaceActorSelector actorSelector) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMembershipService = workspaceMembershipService;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.organizationRepository = organizationRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.actorSelector = actorSelector;
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
        Optional<Workspace> workspaceOpt = findWorkspace(event.organizationId(), event.organizationLogin());

        if (workspaceOpt.isEmpty()) {
            log.debug("Skipped member sync: reason=noWorkspaceForOrg, orgLogin={}", event.organizationLogin());
            return;
        }

        Workspace workspace = workspaceOpt.get();

        try {
            int synced = reconcileWorkspaceMembers(workspace, event.organizationId(), event.rosterComplete());
            log.info(
                    "Synced workspace members after scheduled org sync: workspaceId={},"
                            + " orgLogin={}, memberCount={}",
                    workspace.getId(),
                    event.organizationLogin(),
                    synced);
        } catch (Exception e) {
            log.error(
                    "Failed to sync workspace members after scheduled sync: workspaceId={}," + " orgLogin={}",
                    workspace.getId(),
                    event.organizationLogin(),
                    e);
        }
    }

    // A one-person roster event must not remove other workspace members.
    private void syncWorkspaceFromOrganization(MembershipChangedEvent event, String action) {
        Optional<Workspace> workspaceOpt = findWorkspace(event.organizationId(), event.organizationLogin());
        Optional<Organization> organization = organizationRepository.findById(event.organizationId());

        if (workspaceOpt.isEmpty() || organization.isEmpty()) {
            log.debug(
                    "Skipped member sync: reason=noWorkspaceForOrg, orgLogin={}, action={}",
                    event.organizationLogin(),
                    action);
            return;
        }

        Workspace workspace = workspaceOpt.get();

        try {
            Membership.@Nullable WorkspaceRole role = currentGrant(organization.get(), event.userId());
            workspaceMembershipService.applyProviderGrant(workspace, event.userId(), role);
            log.info(
                    "Synced workspace member after member change: workspaceId={}, orgLogin={},"
                            + " action={}, userLogin={}, role={}",
                    workspace.getId(),
                    event.organizationLogin(),
                    action,
                    event.userLogin(),
                    role);
        } catch (Exception e) {
            log.error(
                    "Failed to sync workspace member after member change: workspaceId={}," + " orgLogin={}, action={}",
                    workspace.getId(),
                    event.organizationLogin(),
                    action,
                    e);
            // Don't rethrow - org membership change succeeded, workspace sync is secondary
        }
    }

    private Membership.@Nullable WorkspaceRole currentGrant(Organization organization, Long userId) {
        Optional<Membership.WorkspaceRole> rosterRole = organizationMembershipRepository
                .findByOrganizationIdAndUserId(organization.getId(), userId)
                .map(membership -> mapOrgRoleToWorkspaceRole(membership.getRole()));
        if (rosterRole.isPresent()) {
            return rosterRole.get();
        }
        return teamMembershipRepository
                        .findDistinctUserIdsOfTeams(
                                organization.getLogin(),
                                Objects.requireNonNull(
                                        organization.getProvider().getId()))
                        .contains(userId)
                ? Membership.WorkspaceRole.MEMBER
                : null;
    }

    // Disconnected or re-bound organization events cannot grant workspace membership.
    private Optional<Workspace> findWorkspace(Long organizationId, String organizationLogin) {
        Optional<Long> organizationProvider = organizationRepository
                .findById(organizationId)
                .map(organization ->
                        Objects.requireNonNull(organization.getProvider().getId()));
        if (organizationProvider.isEmpty()) {
            return Optional.empty();
        }
        Optional<Workspace> linked = workspaceRepository.findByOrganizationId(organizationId);
        if (linked.isPresent()) {
            return linked.filter(
                    workspace -> organizationProvider.equals(actorSelector.connectedProviderId(workspace.getId())));
        }
        List<Workspace> unlinked = workspaceRepository.findAllByAccountLoginIgnoreCase(organizationLogin).stream()
                .filter(workspace -> workspace.getOrganizationId() == null)
                .filter(workspace -> organizationProvider.equals(actorSelector.connectedProviderId(workspace.getId())))
                .toList();
        return unlinked.size() == 1 ? Optional.of(unlinked.getFirst()) : Optional.empty();
    }

    /**
     * Sets the workspace's members to what the provider currently grants: the organization roster's
     * roles, and MEMBER for anyone only a team of the organization lists, since a team grants no
     * administration. No role is carried over from the workspace itself except OWNER, which is kept
     * for anyone who holds it.
     *
     * @param workspace the workspace to sync
     * @param organizationId the organization ID to sync members from
     * @param rosterComplete whether an empty roster is a real answer; otherwise it is taken as a
     *     shortfall and nobody is removed
     * @return the number of members synced
     */
    int reconcileWorkspaceMembers(Workspace workspace, Long organizationId, boolean rosterComplete) {
        Long workspaceId = workspace.getId();

        List<OrganizationMembership> orgMemberships =
                organizationMembershipRepository.findByOrganizationId(organizationId);

        if (orgMemberships.isEmpty() && !rosterComplete) {
            log.debug(
                    "Skipped workspace member sync: reason=noOrgMembersFound, workspaceId={}," + " organizationId={}",
                    workspaceId,
                    organizationId);
            return 0;
        }

        Map<Long, Membership.WorkspaceRole> desiredRoles = new HashMap<>();
        for (OrganizationMembership orgMembership : orgMemberships) {
            desiredRoles.put(orgMembership.getUserId(), mapOrgRoleToWorkspaceRole(orgMembership.getRole()));
        }
        organizationRepository.findById(organizationId).ifPresent(organization -> {
            for (Long userId : teamMembershipRepository.findDistinctUserIdsOfTeams(
                    organization.getLogin(),
                    Objects.requireNonNull(organization.getProvider().getId()))) {
                desiredRoles.putIfAbsent(userId, Membership.WorkspaceRole.MEMBER);
            }
        });
        // An owner stays owner whether or not the provider still lists them.
        workspaceMembershipService.syncWorkspaceMembers(workspace, desiredRoles);

        workspace.setMembersSyncedAt(Instant.now());
        workspaceRepository.save(workspace);

        return desiredRoles.size();
    }

    private Membership.WorkspaceRole mapOrgRoleToWorkspaceRole(OrganizationMemberRole orgRole) {
        if (orgRole == OrganizationMemberRole.ADMIN) {
            return Membership.WorkspaceRole.ADMIN;
        }
        return Membership.WorkspaceRole.MEMBER;
    }
}
