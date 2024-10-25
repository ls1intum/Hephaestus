package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.time.OffsetDateTime;
import java.util.Set;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentDTO;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryDTO;
import de.tum.in.www1.hephaestus.codereview.user.UserDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestDTO(@NonNull Long id, @NonNull String title, @NonNull Integer number, @NonNull String url,
                @NonNull IssueState state, @NonNull Integer additions, @NonNull Integer deletions,
                @NonNull OffsetDateTime createdAt, @NonNull OffsetDateTime updatedAt, OffsetDateTime mergedAt,
                UserDTO author, Set<IssueCommentDTO> comments, Set<PullRequestLabel> labels, RepositoryDTO repository) {
        public PullRequestDTO(@NonNull Long id, @NonNull String title, @NonNull Integer number, @NonNull String url,
                        @NonNull IssueState state, @NonNull Integer additions, @NonNull Integer deletions,
                        @NonNull OffsetDateTime createdAt, @NonNull OffsetDateTime updatedAt, OffsetDateTime mergedAt) {
                this(id, title, number, url, state, additions, deletions, createdAt, updatedAt, mergedAt, null, null,
                                null,
                                null);
        }

        public PullRequestDTO(@NonNull Long id, @NonNull String title, @NonNull Integer number, @NonNull String url,
                        @NonNull IssueState state, @NonNull Integer additions,
                        @NonNull Integer deletions, @NonNull OffsetDateTime createdAt,
                        @NonNull OffsetDateTime updatedAt, OffsetDateTime mergedAt,
                        @NonNull Set<PullRequestLabel> labels,
                        @NonNull RepositoryDTO repository) {
                this(id, title, number, url, state, additions, deletions, createdAt, updatedAt, mergedAt, null, null,
                                labels, repository);
        }
}
