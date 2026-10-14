package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembershipService {
    private final MembershipRepository memberships;
    private final WorkspaceRepository workspaces;
    private final ConfigAuditPort configAudit;
    private final WorkspaceAccessService access;
    private final ObjectProvider<PeopleRegistry> people;
    private final AccountIdentityQuery identities;
    private final WorkspaceActorSelector actorSelector;
    private final Clock clock;
    private final AccountDirectory accounts;
    private final GitProviderRegistry providers;

    @Transactional
    public long accountIdForUser(long scmUserId) {
        var actor = providers
                .actor(scmUserId)
                .filter(GitProviderRegistry.ActorProfile::human)
                .orElseThrow(() -> new IllegalArgumentException("A human provider account is required."));
        return people.getObject()
                .ensureHuman(
                        new PersonIdentity(actor.providerId(), actor.providerUserKey(), null), actor.displayName());
    }

    @Transactional
    public int ensureMemberships(Workspace workspace, Set<Long> scmUserIds) {
        int inserted = 0;
        for (long scmUserId : scmUserIds) {
            if (providers
                    .actor(scmUserId)
                    .filter(GitProviderRegistry.ActorProfile::human)
                    .isEmpty()) continue;
            inserted += memberships.insertIfAbsent(
                    workspace.getId(), accountIdForUser(scmUserId), Membership.WorkspaceRole.MEMBER.name(), false);
        }
        return inserted;
    }

    @Transactional
    public void syncWorkspaceMembers(Workspace workspace, Map<Long, Membership.WorkspaceRole> providerRoles) {
        Map<Long, Membership.WorkspaceRole> desired = new HashMap<>();
        for (var entry : providerRoles.entrySet()) {
            if (providers
                    .actor(entry.getKey())
                    .filter(GitProviderRegistry.ActorProfile::human)
                    .isEmpty()) continue;
            desired.merge(
                    accountIdForUser(entry.getKey()),
                    entry.getValue(),
                    (first, next) -> first.isAtLeast(next) ? first : next);
        }
        for (var entry : desired.entrySet()) {
            var existing = memberships.findByWorkspace_IdAndAccount_Id(workspace.getId(), entry.getKey());
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

    @Transactional
    public void applyProviderGrant(Workspace workspace, Long scmUserId, Membership.@Nullable WorkspaceRole role) {
        long accountId = accountIdForUser(scmUserId);
        var existing = memberships.findByWorkspace_IdAndAccount_Id(workspace.getId(), accountId);
        if (existing.isPresent() && existing.get().getRole() == Membership.WorkspaceRole.OWNER) return;
        if (role == null) existing.ifPresent(member -> member.setLeftAt(clock.instant()));
        else if (existing.isPresent()) {
            existing.get().setRole(role);
            existing.get().setLeftAt(null);
        } else createMembership(workspace, accountId, role);
    }

    @Transactional
    public Membership createMembership(Workspace workspace, Long accountId, Membership.WorkspaceRole role) {
        var existing = memberships.findByWorkspace_IdAndAccount_Id(workspace.getId(), accountId);
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

    @Transactional
    public Membership assignRole(Long workspaceId, Long accountId, Membership.WorkspaceRole role) {
        var workspace = lockForMembershipChange(workspaceId);
        requireCanManageRole(workspace, role);
        var existing = memberships.findByWorkspace_IdAndAccount_Id(workspaceId, accountId);
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

    @Transactional
    public void removeMembership(Long workspaceId, Long accountId) {
        var workspace = lockForMembershipChange(workspaceId);
        var member = getMembership(workspaceId, accountId);
        requireCanManageRole(workspace, member.getRole());
        requireNotLastOwner(workspace, member);
        if (member.getLeftAt() != null) return;
        member.setLeftAt(clock.instant());
        configAudit.record(ConfigAuditEntry.deleted(
                ConfigAuditEntityType.WORKSPACE_ROLE, accountId, workspaceId, snapshot(member)));
    }

    @Transactional
    public Membership updateMemberVisibility(Long workspaceId, Long accountId, boolean hidden) {
        var member = getMembership(workspaceId, accountId);
        var before = snapshot(member);
        member.setHidden(hidden);
        configAudit.record(ConfigAuditEntry.updated(
                ConfigAuditEntityType.WORKSPACE_ROLE, accountId, workspaceId, before, snapshot(member)));
        return memberships.save(member);
    }

    @Transactional(readOnly = true)
    public Set<Long> getHiddenMemberIds(Long workspaceId) {
        return memberships.findHiddenAccountIdsByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public Membership getMembership(Long workspaceId, Long accountId) {
        return findMembership(workspaceId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Membership", accountId));
    }

    @Transactional(readOnly = true)
    public Optional<Membership> findMembership(Long workspaceId, Long accountId) {
        return memberships.findByWorkspace_IdAndAccount_Id(workspaceId, accountId);
    }

    @Transactional(readOnly = true)
    public Page<Membership> listMembers(Long workspaceId, Pageable pageable) {
        return memberships.findAllByWorkspace_Id(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public Set<Long> practiceReviewEligibleAccountIds(Long workspaceId) {
        return memberships.findByWorkspace_Id(workspaceId).stream()
                .filter(member -> isPracticeReviewEligible(workspaceId, Objects.requireNonNull(member.getAccountId())))
                .map(Membership::getAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean isPracticeReviewEligible(Long workspaceId, Long accountId) {
        return findMembership(workspaceId, accountId)
                .filter(member -> member.getLeftAt() == null)
                .filter(member -> accounts.find(accountId)
                        .map(account -> account.status() == Account.Status.ACTIVE
                                || account.status() == Account.Status.UNCLAIMED)
                        .orElse(false))
                .filter(member -> actorSelector
                        .connectedProviderId(workspaceId)
                        .map(provider -> identities.activeLinksForAccount(accountId).stream()
                                .anyMatch(identity -> provider.equals(identity.providerId())))
                        .orElse(true))
                .isPresent();
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
