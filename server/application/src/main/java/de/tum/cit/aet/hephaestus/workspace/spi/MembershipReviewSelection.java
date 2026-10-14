package de.tum.cit.aet.hephaestus.workspace.spi;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface MembershipReviewSelection {
    Set<Long> eligibleAccountIds(long workspaceId);

    List<Long> selectedAccountIds(long workspaceId);

    void replaceSelection(long workspaceId, Collection<Long> accountIds);
}
