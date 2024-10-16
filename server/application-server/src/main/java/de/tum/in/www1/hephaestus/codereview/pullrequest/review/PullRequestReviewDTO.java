package de.tum.in.www1.hephaestus.codereview.pullrequest.review;

import java.time.OffsetDateTime;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestReviewDTO(@NonNull Long id, @NonNull OffsetDateTime createdAt,
                @NonNull OffsetDateTime updatedAt, @NonNull OffsetDateTime submittedAt,
                @NonNull PullRequestReviewState state, String url, PullRequestDTO pullRequest) {
        public PullRequestReviewDTO(@NonNull Long id, @NonNull OffsetDateTime createdAt,
                        @NonNull OffsetDateTime updatedAt, @NonNull OffsetDateTime submittedAt,
                        @NonNull PullRequestReviewState state) {
                this(id, createdAt, updatedAt, submittedAt, state, null, null);
        }

        public PullRequestReviewDTO(@NonNull Long id, @NonNull OffsetDateTime createdAt,
                        @NonNull OffsetDateTime updatedAt, @NonNull OffsetDateTime submittedAt,
                        @NonNull PullRequestReviewState state, String url) {
                this(id, createdAt, updatedAt, submittedAt, state, url, null);
        }
}
