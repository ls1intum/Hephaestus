package de.tum.in.www1.hephaestus.codereview.pullrequest.review;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestReviewDTO(Long id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                OffsetDateTime submittedAt, PullRequestReviewState state, PullRequestDTO pullRequest) {
        public PullRequestReviewDTO(Long id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                        OffsetDateTime submittedAt,
                        PullRequestReviewState state) {
                this(id, createdAt, updatedAt, submittedAt, state, null);
        }
}
