package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestReviewDTO(Long id, OffsetDateTime submittedAt, PullRequestReview.State state) {
}
