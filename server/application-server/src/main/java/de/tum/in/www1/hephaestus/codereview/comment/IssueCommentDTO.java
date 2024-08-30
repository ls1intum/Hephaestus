package de.tum.in.www1.hephaestus.codereview.comment;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestDTO;
import de.tum.in.www1.hephaestus.codereview.user.UserDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IssueCommentDTO(Long id, String body, String createdAt, String updatedAt, UserDTO author,
        PullRequestDTO pullrequest) {
    public IssueCommentDTO(Long id, String body, String createdAt, String updatedAt) {
        this(id, body, createdAt, updatedAt, null, null);
    }
}
