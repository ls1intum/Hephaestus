package de.tum.in.www1.hephaestus.gitprovider.issuecomment;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IssueCommentDTO(
    @NonNull Long id,
    @NonNull String body, 
    @NonNull String createdAt, 
    @NonNull String updatedAt, 
    UserDTO author,
    PullRequestDTO pullRequest) {
    
    public IssueCommentDTO(
        @NonNull Long id, 
        @NonNull String body, 
        @NonNull String createdAt, 
        @NonNull String updatedAt) {
        this(id, body, createdAt, updatedAt, null, null);
    }
}
