package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentDTO;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryDTO;
import de.tum.in.www1.hephaestus.codereview.user.UserDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestDTO(String title, String url, GHIssueState state, String createdAt, String updatedAt,
                String mergedAt, UserDTO author, Set<IssueCommentDTO> comments, RepositoryDTO repository) {
        public PullRequestDTO(String title, String url, GHIssueState state, String createdAt, String updatedAt,
                        String mergedAt) {
                this(title, url, state, createdAt, updatedAt, mergedAt, null, null, null);
        }
}
