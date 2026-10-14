package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountMembershipQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountMembershipQueryAdapter implements AccountMembershipQuery {
    private final MembershipRepository memberships;

    @Override
    public List<MembershipView> membershipsForAccount(long accountId) {
        return memberships.findById_AccountId(accountId).stream()
                .filter(member -> member.getLeftAt() == null)
                .map(member -> new MembershipView(
                        member.getWorkspace().getId(),
                        member.getWorkspace().getWorkspaceSlug(),
                        member.getWorkspace().getDisplayName(),
                        switch (member.getRole()) {
                            case OWNER -> Role.OWNER;
                            case ADMIN -> Role.ADMIN;
                            case MEMBER -> Role.MEMBER;
                        }))
                .toList();
    }

    @Override
    public List<Long> administratorAccountIds(long workspaceId) {
        return memberships.findAdministratorAccountIds(workspaceId);
    }

    @Override
    public boolean isAdministrator(long workspaceId, long accountId) {
        return memberships.isAdministrator(workspaceId, accountId);
    }
}
