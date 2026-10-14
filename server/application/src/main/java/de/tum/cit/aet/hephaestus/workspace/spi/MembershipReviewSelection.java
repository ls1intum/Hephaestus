package de.tum.cit.aet.hephaestus.workspace.spi;

import java.util.Collection;

public interface MembershipReviewSelection {
    void replaceSelection(long workspaceId, Collection<Long> accountIds);
}
