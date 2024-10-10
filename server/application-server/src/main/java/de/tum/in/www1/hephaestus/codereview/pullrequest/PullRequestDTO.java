package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.time.OffsetDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentDTO;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryDTO;
import de.tum.in.www1.hephaestus.codereview.user.UserDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestDTO(Long id, String title, int number, String url, IssueState state, int additions,
                int deletions, OffsetDateTime createdAt,
                OffsetDateTime updatedAt,
                OffsetDateTime mergedAt, UserDTO author, Set<IssueCommentDTO> comments, Set<PullRequestLabel> labels,
                RepositoryDTO repository) {
        public PullRequestDTO(Long id, String title, int number, String url, IssueState state, int additions,
                        int deletions, OffsetDateTime createdAt,
                        OffsetDateTime updatedAt,
                        OffsetDateTime mergedAt) {
                this(id, title, number, url, state, additions, deletions, createdAt, updatedAt, mergedAt, null, null,
                                null,
                                null);
        }

        public PullRequestDTO(Long id, String title, int number, String url, IssueState state, int additions,
                        int deletions, OffsetDateTime createdAt,
                        OffsetDateTime updatedAt,
                        OffsetDateTime mergedAt, Set<PullRequestLabel> labels, RepositoryDTO repository) {
                this(id, title, number, url, state, additions, deletions, createdAt, updatedAt, mergedAt, null, null,
                                labels, repository);
        }
}
