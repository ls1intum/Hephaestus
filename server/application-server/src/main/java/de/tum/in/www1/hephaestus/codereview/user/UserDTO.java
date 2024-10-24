package de.tum.in.www1.hephaestus.codereview.user;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentDTO;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserDTO(Long id, String login, String email, String name, String url, Set<PullRequestDTO> pullRequests,
        Set<IssueCommentDTO> comments) {
    public UserDTO(Long id, String login, String email, String name, String url) {
        this(id, login, email, name, url, null, null);
    }
}
