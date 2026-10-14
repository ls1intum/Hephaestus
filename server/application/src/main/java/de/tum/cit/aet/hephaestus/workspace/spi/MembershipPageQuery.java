package de.tum.cit.aet.hephaestus.workspace.spi;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MembershipPageQuery {
    Page<Long> viewableAccountIds(long workspaceId, Pageable pageable);

    boolean isCurrentMember(long workspaceId, long accountId);
}
