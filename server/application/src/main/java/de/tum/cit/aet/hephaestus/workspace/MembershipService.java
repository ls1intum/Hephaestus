package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.core.auth.spi.PeopleRegistry;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.workspace.audit.WorkspaceAuditSnapshots;
import de.tum.cit.aet.hephaestus.workspace.authorization.WorkspaceAccessService;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextHolder;
import de.tum.cit.aet.hephaestus.workspace.exception.InsufficientWorkspacePermissionsException;
import de.tum.cit.aet.hephaestus.workspace.exception.LastOwnerRemovalException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.core.PropertyPath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MembershipService {
    private final MembershipRepository memberships;
    private final WorkspaceRepository workspaces;
    private final ConfigAuditPort configAudit;
    private final WorkspaceAccessService access;
    private final ObjectProvider<PeopleRegistry> people;
    private final Clock clock;
    private final AccountDirectory accounts;
    private final GitProviderRegistry providers;
    private final MembershipSearch memberSearch;
    private final WorkspaceActorSelector actors;

    public long accountIdForUser(long scmUserId) {
        var actor = providers
                .actor(scmUserId)
                .filter(GitProviderRegistry.ActorProfile::human)
                .orElseThrow(() -> new IllegalArgumentException("A human provider account is required."));
        return accountIdFor(actor);
    }

    private long accountIdFor(GitProviderRegistry.ActorProfile actor) {
        return people.getObject()
                .ensureHuman(
                        new PersonIdentity(actor.providerId(), actor.providerUserKey(), null), actor.displayName());
    }

    public int ensureMemberships(Workspace workspace, Set<Long> scmUserIds) {
        int inserted = 0;
        for (long scmUserId : scmUserIds) {
            var actor = providers.actor(scmUserId).filter(GitProviderRegistry.ActorProfile::human);
            if (actor.isEmpty()) continue;
            inserted += memberships.insertIfAbsent(
                    workspace.getId(), accountIdFor(actor.get()), Membership.WorkspaceRole.MEMBER.name(), false);
        }
        return inserted;
    }

    public void syncWorkspaceMembers(Workspace workspace, Map<Long, Membership.WorkspaceRole> providerRoles) {
        Map<Long, Membership.WorkspaceRole> desired = new HashMap<>();
        for (var entry : providerRoles.entrySet()) {
            var actor = providers.actor(entry.getKey()).filter(GitProviderRegistry.ActorProfile::human);
            if (actor.isEmpty()) continue;
            desired.merge(accountIdFor(actor.get()), entry.getValue(), Membership.WorkspaceRole::highest);
        }
        for (var entry : desired.entrySet()) {
            var existing = memberships.findByWorkspace_IdAndId_AccountId(workspace.getId(), entry.getKey());
            if (existing.isEmpty()) createMembership(workspace, entry.getKey(), entry.getValue());
            else {
                var member = existing.get();
                if (member.getRole() != Membership.WorkspaceRole.OWNER) member.setRole(entry.getValue());
                member.setLeftAt(null);
            }
        }
        for (var member : memberships.findByWorkspace_Id(workspace.getId())) {
            if (member.getRole() != Membership.WorkspaceRole.OWNER
                    && !desired.containsKey(member.getAccountId())
                    && member.getLeftAt() == null) member.setLeftAt(clock.instant());
        }
    }

    public void applyProviderGrant(Workspace workspace, Long scmUserId, Membership.@Nullable WorkspaceRole role) {
        long accountId = accountIdForUser(scmUserId);
        var existing = memberships.findByWorkspace_IdAndId_AccountId(workspace.getId(), accountId);
        if (existing.isPresent() && existing.get().getRole() == Membership.WorkspaceRole.OWNER) return;
        if (role == null) existing.ifPresent(member -> member.setLeftAt(clock.instant()));
        else if (existing.isPresent()) {
            existing.get().setRole(role);
            existing.get().setLeftAt(null);
        } else createMembership(workspace, accountId, role);
    }

    public Membership createMembership(Workspace workspace, Long accountId, Membership.WorkspaceRole role) {
        var existing = memberships.findByWorkspace_IdAndId_AccountId(workspace.getId(), accountId);
        if (existing.isPresent()) {
            var member = existing.get();
            if (member.getLeftAt() == null) throw new IllegalArgumentException("This account is already a member.");
            member.setLeftAt(null);
            member.setRole(role);
            return memberships.save(member);
        }
        var account = accounts.find(accountId);
        if (account.isEmpty()) throw new EntityNotFoundException("Account", accountId);
        var member = new Membership();
        member.setWorkspace(workspace);
        member.setId(new Membership.Id(workspace.getId(), accountId));
        member.setRole(role);
        return memberships.save(member);
    }

    public Membership assignRole(Long workspaceId, Long accountId, Membership.WorkspaceRole role) {
        var workspace = lockForMembershipChange(workspaceId);
        requireCanManageRole(workspace, role);
        var existing = memberships.findByWorkspace_IdAndId_AccountId(workspaceId, accountId);
        if (existing.isEmpty()) {
            var member = createMembership(workspace, accountId, role);
            configAudit.record(ConfigAuditEntry.created(
                    ConfigAuditEntityType.WORKSPACE_ROLE, accountId, workspaceId, snapshot(member)));
            return member;
        }
        var member = existing.get();
        requireCanManageRole(workspace, member.getRole());
        if (role != Membership.WorkspaceRole.OWNER) requireNotLastOwner(workspace, member);
        var before = snapshot(member);
        member.setRole(role);
        member.setLeftAt(null);
        configAudit.record(ConfigAuditEntry.updated(
                ConfigAuditEntityType.WORKSPACE_ROLE, accountId, workspaceId, before, snapshot(member)));
        return memberships.save(member);
    }

    public void removeMembership(Long workspaceId, Long accountId) {
        var workspace = lockForMembershipChange(workspaceId);
        var member = requireMembership(workspaceId, accountId);
        requireCanManageRole(workspace, member.getRole());
        requireNotLastOwner(workspace, member);
        if (member.getLeftAt() != null) return;
        member.setLeftAt(clock.instant());
        configAudit.record(ConfigAuditEntry.deleted(
                ConfigAuditEntityType.WORKSPACE_ROLE, accountId, workspaceId, snapshot(member)));
    }

    public Membership updateMemberVisibility(Long workspaceId, Long accountId, boolean hidden) {
        var member = requireMembership(workspaceId, accountId);
        var before = snapshot(member);
        member.setHidden(hidden);
        configAudit.record(ConfigAuditEntry.updated(
                ConfigAuditEntityType.WORKSPACE_ROLE, accountId, workspaceId, before, snapshot(member)));
        return memberships.save(member);
    }

    @Transactional(readOnly = true)
    public Membership getMembership(Long workspaceId, Long accountId) {
        return requireMembership(workspaceId, accountId);
    }

    private Membership requireMembership(Long workspaceId, Long accountId) {
        return memberships
                .findByWorkspace_IdAndId_AccountId(workspaceId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Membership", accountId));
    }

    @Transactional(readOnly = true)
    public Page<Membership> listMembers(
            long workspaceId, @Nullable String query, @Nullable Long teamId, Pageable pageable) {
        var orders = pageable.getSort().stream()
                .map(order -> order.withProperty(
                        switch (order.getProperty()) {
                            case "name" -> "a.displayName";
                            case "username" -> "u.login";
                            default ->
                                PropertyPath.from(order.getProperty(), Membership.class)
                                        .toDotPath();
                        }))
                .toList();
        var page = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(orders).and(Sort.by("id.accountId")));
        return memberSearch.findMembers(
                workspaceId,
                actors.connectedProviderId(workspaceId).orElse(null),
                query == null || query.isBlank() ? null : query.strip(),
                teamId,
                page);
    }

    private Workspace lockForMembershipChange(Long workspaceId) {
        return workspaces
                .findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId));
    }

    private void requireCanManageRole(Workspace workspace, Membership.WorkspaceRole role) {
        var context = WorkspaceContextHolder.getContext();
        if (context == null || !workspace.getId().equals(context.id()) || !access.canManageRole(role))
            throw new InsufficientWorkspacePermissionsException(
                    workspace.getWorkspaceSlug(), "You cannot manage this role in this workspace.");
    }

    private void requireNotLastOwner(Workspace workspace, Membership member) {
        if (member.getLeftAt() == null
                && member.getRole() == Membership.WorkspaceRole.OWNER
                && memberships.countByWorkspace_IdAndRole(workspace.getId(), Membership.WorkspaceRole.OWNER) <= 1)
            throw new LastOwnerRemovalException(workspace.getWorkspaceSlug());
    }

    private static WorkspaceAuditSnapshots.RoleSnapshot snapshot(Membership member) {
        return new WorkspaceAuditSnapshots.RoleSnapshot(member.getRole().name(), member.isHidden());
    }
}
