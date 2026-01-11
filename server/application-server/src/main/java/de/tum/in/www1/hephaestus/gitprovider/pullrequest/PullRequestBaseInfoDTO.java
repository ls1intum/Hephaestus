package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Basic information about a pull request")
public record PullRequestBaseInfoDTO(
    @NonNull @Schema(description = "Unique identifier of the pull request") Long id,
    @NonNull @Schema(description = "Pull request number within the repository", example = "42") Integer number,
    @NonNull @Schema(description = "Title of the pull request") String title,
    @NonNull @Schema(description = "Current state of the pull request (OPEN, CLOSED)") State state,
    @NonNull @Schema(description = "Whether the pull request is in draft mode") Boolean isDraft,
    @NonNull @Schema(description = "Whether the pull request has been merged") Boolean isMerged,
    @Schema(description = "Repository the pull request belongs to") RepositoryInfoDTO repository,
    @NonNull @Schema(description = "URL to the pull request on the git provider") String htmlUrl
) {
    public static PullRequestBaseInfoDTO fromPullRequest(PullRequest pullRequest) {
        return new PullRequestBaseInfoDTO(
            pullRequest.getId(),
            pullRequest.getNumber(),
            pullRequest.getTitle(),
            pullRequest.getState(),
            pullRequest.isDraft(),
            pullRequest.isMerged(),
            RepositoryInfoDTO.fromRepository(pullRequest.getRepository()),
            pullRequest.getHtmlUrl()
        );
    }

    public static PullRequestBaseInfoDTO fromIssue(Issue issue) {
        return new PullRequestBaseInfoDTO(
            issue.getId(),
            issue.getNumber(),
            issue.getTitle(),
            issue.getState(),
            false,
            false,
            RepositoryInfoDTO.fromRepository(issue.getRepository()),
            issue.getHtmlUrl()
        );
    }
}
