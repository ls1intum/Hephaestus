package de.tum.cit.aet.hephaestus.workspace.spi;

public interface MembershipRoster {
    boolean ensureMember(long workspaceId, long accountId);
}
