package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.workspace.spi.MembershipPageQuery;
import de.tum.cit.aet.hephaestus.workspace.spi.MembershipRoster;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class MembershipRosterService implements MembershipRoster, MembershipPageQuery {
    private final WorkspaceRepository workspaces;
    private final MembershipRepository memberships;
    private final AccountDirectory accounts;

    @Override
    @Transactional(readOnly = true)
    public Page<Long> viewableAccountIds(long workspaceId, Pageable pageable) {
        return memberships.findViewableAccountIds(workspaceId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCurrentMember(long workspaceId, long accountId) {
        return memberships.existsByWorkspace_IdAndId_AccountIdAndLeftAtIsNull(workspaceId, accountId);
    }

    @Override
    @Transactional
    public boolean ensureMember(long workspaceId, long accountId) {
        var workspace = workspaces
                .findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId));
        if (workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE) return false;
        var existing = memberships.findByWorkspace_IdAndId_AccountId(workspaceId, accountId);
        if (existing.isPresent()) return existing.get().getLeftAt() == null;
        var account = accounts.find(accountId).orElseThrow(() -> new EntityNotFoundException("Account", accountId));
        if (account.status() != Account.Status.UNCLAIMED && account.status() != Account.Status.ACTIVE) return false;
        memberships.insertIfAbsent(workspaceId, accountId, Membership.WorkspaceRole.MEMBER.name(), false);
        return memberships
                .findByWorkspace_IdAndId_AccountId(workspaceId, accountId)
                .map(member -> member.getLeftAt() == null)
                .orElse(false);
    }
}
