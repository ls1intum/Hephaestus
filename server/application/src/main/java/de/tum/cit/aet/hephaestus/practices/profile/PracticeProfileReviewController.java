package de.tum.cit.aet.hephaestus.practices.profile;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.web.PageResponseDTO;
import de.tum.cit.aet.hephaestus.practices.profile.dto.ProfileReviewDTO;
import de.tum.cit.aet.hephaestus.practices.profile.dto.ProfileReviewDetailDTO;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ConditionalOnServerRole
@WorkspaceScopedController
@RequestMapping("/practice-profile/reviews")
@PreAuthorize("@workspaceSecure.isMember()")
@RequiredArgsConstructor
public class PracticeProfileReviewController {
    private final PracticeProfileReviewService reviews;

    @GetMapping
    @Operation(operationId = "listOwnReviews", summary = "List reviews with results about you")
    public PageResponseDTO<ProfileReviewDTO> list(
            WorkspaceContext ws,
            @RequestParam(required = false) @Nullable String itemKind,
            @RequestParam(required = false) @Nullable Instant since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponseDTO.from(reviews.list(ws, itemKind, null, null, since, PageRequest.of(page, size)));
    }

    @GetMapping("/{reviewId}")
    @Operation(operationId = "getOwnReview", summary = "Get your results from a review")
    public ProfileReviewDetailDTO get(WorkspaceContext ws, @PathVariable UUID reviewId) {
        return reviews.get(ws, reviewId);
    }
}
