package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.workspace.spi.MembershipRoster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class MembershipRosterService implements MembershipRoster {
    private final WorkspaceRepository workspaces;
    private final MembershipRepository memberships;
    private final AccountDirectory accounts;

    @Override
    @Transactional
    public boolean ensureMember(long workspaceId, long accountId) {
        var workspace = workspaces.findByIdForUpdate(workspaceId).orElseThrow();
        if (workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE) return false;
        var existing = memberships.findByWorkspace_IdAndAccount_Id(workspaceId, accountId);
        if (existing.isPresent()) return existing.get().getLeftAt() == null;
        var account = accounts.find(accountId).orElseThrow();
        if (account.status() != Account.Status.UNCLAIMED && account.status() != Account.Status.ACTIVE) return false;
        memberships.insertIfAbsent(workspaceId, accountId, Membership.WorkspaceRole.MEMBER.name(), false);
        return memberships
                .findByWorkspace_IdAndAccount_Id(workspaceId, accountId)
                .map(member -> member.getLeftAt() == null)
                .orElse(false);
    }
}
