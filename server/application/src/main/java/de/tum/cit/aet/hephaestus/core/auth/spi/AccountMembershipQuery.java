package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Account-owned workspace membership, resolved through verified identities rather than usernames.
 */
public interface AccountMembershipQuery {
    /**
     * Current memberships through active identity links and their stable actor IDs; never a
     * login-name join.
     */
    List<MembershipView> membershipsForAccount(long accountId);

    List<Long> administratorAccountIds(long workspaceId);

    boolean isAdministrator(long workspaceId, long accountId);

    /** One active account membership, with no provider profile data. */
    record MembershipView(
            Long workspaceId,
            String workspaceSlug,
            String workspaceName,
            @Nullable String role,
            @Nullable Long accountId) {}
}
