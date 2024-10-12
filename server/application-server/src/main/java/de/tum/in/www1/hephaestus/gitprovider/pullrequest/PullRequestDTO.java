package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.comment.IssueCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestDTO(Long id, String title, String url, IssueState state, String createdAt, String updatedAt,
                String mergedAt, UserDTO author, Set<IssueCommentDTO> comments, RepositoryDTO repository) {
        public PullRequestDTO(Long id, String title, String url, IssueState state, String createdAt, String updatedAt,
                        String mergedAt) {
                this(id, title, url, state, createdAt, updatedAt, mergedAt, null, null, null);
        }
}
