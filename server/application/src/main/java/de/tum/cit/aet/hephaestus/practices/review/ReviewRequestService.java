package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.RoleHolder;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewRequestOutcomeDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequest;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.workspace.authorization.WorkspaceAccessService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewRequestService {
    private final ProductItemQuery items;
    private final AccountIdentityQuery identities;
    private final WorkspaceAccessService access;
    private final ReviewService reviews;
    private final ReviewRequestPolicy policy;

    public boolean mayRequest(long workspaceId, ItemSnapshot snapshot, long accountId) {
        if (snapshot.item().workspaceId() != workspaceId) return false;
        return access.isAdmin()
                || snapshot.roles().stream()
                        .filter(RoleHolder::human)
                        .filter(r -> r.role() == AboutRole.AUTHOR || r.role() == AboutRole.ASSIGNEE)
                        .anyMatch(r -> identities
                                .resolveActiveAccountId(
                                        r.identity().providerId(),
                                        r.identity().subject(),
                                        r.identity().teamId())
                                .filter(id -> id == accountId)
                                .isPresent());
    }

    @Transactional
    public ReviewRequestOutcomeDTO request(long workspaceId, long itemId, long accountId) {
        var item = items.require(workspaceId, itemId);
        var pinned = items.pin(item, null);
        if (!mayRequest(workspaceId, pinned, accountId))
            throw new AccessForbiddenException(
                    "Only the author or an assignee, or a workspace admin, can request this review.");
        var refusal = policy.refusal(workspaceId, itemId, accountId);
        if (refusal.isPresent())
            return new ReviewRequestOutcomeDTO(ReviewRequestStatus.REFUSED, null, refusal.orElseThrow());
        var id = reviews.request(ReviewRequest.builder()
                .workspaceId(workspaceId)
                .itemId(itemId)
                .kind(ReviewKind.ITEM)
                .version(pinned.version())
                .origin(ReviewOrigin.REQUEST)
                .requestedById(accountId)
                .practiceIds(List.of())
                .build());
        return new ReviewRequestOutcomeDTO(ReviewRequestStatus.SUBMITTED, id, null);
    }
}
