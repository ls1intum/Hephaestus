package de.tum.in.www1.hephaestus.gitprovider.user;

import java.util.Set;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserDTO(
        @NonNull Long id,
        @NonNull String login,
        @NonNull String avatarUrl,
        @NonNull String name,
        @NonNull String htmlUrl,
        Set<PullRequestDTO> pullRequests,
        Set<IssueCommentDTO> comments) {

    public UserDTO(
            @NonNull Long id,
            @NonNull String login,
            @NonNull String avatarUrl,
            @NonNull String name,
            @NonNull String htmlUrl) {
        this(id, login, avatarUrl, name, htmlUrl, null, null);

}
