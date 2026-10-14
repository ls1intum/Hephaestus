package de.tum.cit.aet.hephaestus.workspace;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Provider adapters join account membership with their current profile and team mirrors. */
public interface MembershipSearch {
    Page<Membership> findMembers(
            long workspaceId,
            @Nullable Long providerId,
            @Nullable String query,
            @Nullable Long teamId,
            Pageable pageable);
}
