package de.tum.cit.aet.hephaestus.workspace.spi;

/** Adds human accounts discovered by a workspace's connected source. */
public interface MembershipRoster {
    boolean ensureMember(long workspaceId, long accountId);
}
