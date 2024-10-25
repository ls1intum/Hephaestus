package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import java.time.OffsetDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestReviewDTO(
    @NonNull Long id,
    @NonNull OffsetDateTime submittedAt,
    @NonNull PullRequestReview.State state) {
}
