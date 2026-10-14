package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountMembershipQuery;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountMembershipQueryAdapter implements AccountMembershipQuery {
    private final MembershipRepository memberships;
    private final AccountDirectory accounts;

    @Override
    @Transactional(readOnly = true)
    public List<MembershipView> membershipsForAccount(long accountId) {
        return memberships.findByAccount_Id(accountId).stream()
                .filter(member -> member.getLeftAt() == null)
                .map(member -> new MembershipView(
                        member.getWorkspace().getId(),
                        member.getWorkspace().getWorkspaceSlug(),
                        member.getWorkspace().getDisplayName(),
                        member.getRole().name(),
                        accountId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> administratorAccountIds(long workspaceId) {
        return memberships.findByWorkspace_Id(workspaceId).stream()
                .filter(member -> member.getLeftAt() == null)
                .filter(member -> member.getRole().isAtLeast(Membership.WorkspaceRole.ADMIN))
                .map(member -> Objects.requireNonNull(member.getAccountId()))
                .filter(id -> accounts.find(id)
                        .map(account -> account.status() == Account.Status.ACTIVE)
                        .orElse(false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAdministrator(long workspaceId, long accountId) {
        return administratorAccountIds(workspaceId).contains(accountId);
    }
}
