package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.List;

public interface AccountMembershipQuery {
    List<MembershipView> membershipsForAccount(long accountId);

    List<Long> administratorAccountIds(long workspaceId);

    boolean isAdministrator(long workspaceId, long accountId);

    record MembershipView(Long workspaceId, String workspaceSlug, String workspaceName, String role) {}
}
