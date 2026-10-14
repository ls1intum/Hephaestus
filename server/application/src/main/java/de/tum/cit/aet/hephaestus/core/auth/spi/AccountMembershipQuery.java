package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.List;

/** Account-owned workspace membership. */
public interface AccountMembershipQuery {
    /** Current (not left) memberships of the account. */
    List<MembershipView> membershipsForAccount(long accountId);

    List<Long> administratorAccountIds(long workspaceId);

    boolean isAdministrator(long workspaceId, long accountId);

    /** One active account membership, with no provider profile data. */
    record MembershipView(Long workspaceId, String workspaceSlug, String workspaceName, String role) {}
}
