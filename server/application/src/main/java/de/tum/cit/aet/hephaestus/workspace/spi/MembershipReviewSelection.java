package de.tum.cit.aet.hephaestus.workspace.spi;

import java.util.Collection;

/** Replaces the selected members for a workspace's review scope. */
public interface MembershipReviewSelection {
    void replaceSelection(long workspaceId, Collection<Long> accountIds);
}
