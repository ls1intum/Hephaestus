package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import java.util.Set;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestDTO(
        @NonNull Long id,
        @NonNull String title,
        @NonNull String htmlUrl,
        @NonNull Issue.State state,
        @NonNull String createdAt,
        @NonNull String updatedAt,
        @NonNull String mergedAt,
        UserDTO author,
        Set<IssueCommentDTO> comments, 
        RepositoryDTO repository) {

    public PullRequestDTO(
            @NonNull Long id,
            @NonNull String title,
            @NonNull String htmlUrl,
            @NonNull Issue.State state,
            @NonNull String createdAt,
            @NonNull String updatedAt,
            @NonNull String mergedAt) {
        this(id, title, htmlUrl, state, createdAt, updatedAt, mergedAt, null, null, null);
    }
}
