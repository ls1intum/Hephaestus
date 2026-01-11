package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Detailed information about a pull request")
public record PullRequestInfoDTO(
    @NonNull @Schema(description = "Unique identifier of the pull request") Long id,
    @NonNull @Schema(description = "Pull request number within the repository", example = "42") Integer number,
    @NonNull @Schema(description = "Title of the pull request") String title,
    @NonNull @Schema(description = "Current state of the pull request (OPEN, CLOSED)") State state,
    @NonNull @Schema(description = "Whether the pull request is in draft mode") Boolean isDraft,
    @NonNull @Schema(description = "Whether the pull request has been merged") Boolean isMerged,
    @NonNull @Schema(description = "Number of comments on the pull request", example = "5") Integer commentsCount,
    @Schema(description = "Author of the pull request") UserInfoDTO author,
    @Schema(description = "Labels applied to the pull request") List<LabelInfoDTO> labels,
    @Schema(description = "Users assigned to the pull request") List<UserInfoDTO> assignees,
    @Schema(description = "Repository the pull request belongs to") RepositoryInfoDTO repository,
    @NonNull @Schema(description = "Number of lines added", example = "150") Integer additions,
    @NonNull @Schema(description = "Number of lines deleted", example = "50") Integer deletions,
    @Schema(description = "Timestamp when the pull request was merged") Instant mergedAt,
    @Schema(description = "Timestamp when the pull request was closed") Instant closedAt,
    @NonNull @Schema(description = "URL to the pull request on the git provider") String htmlUrl,
    @Schema(description = "Timestamp when the pull request was created") Instant createdAt,
    @Schema(description = "Timestamp when the pull request was last updated") Instant updatedAt
) {
    public static PullRequestInfoDTO fromPullRequest(PullRequest pullRequest) {
        return new PullRequestInfoDTO(
            pullRequest.getId(),
            pullRequest.getNumber(),
            pullRequest.getTitle(),
            pullRequest.getState(),
            pullRequest.isDraft(),
            pullRequest.isMerged(),
            pullRequest.getCommentsCount(),
            UserInfoDTO.fromUser(pullRequest.getAuthor()),
            pullRequest
                .getLabels()
                .stream()
                .map(LabelInfoDTO::fromLabel)
                .sorted(Comparator.comparing(LabelInfoDTO::name))
                .toList(),
            pullRequest
                .getAssignees()
                .stream()
                .map(UserInfoDTO::fromUser)
                .sorted(Comparator.comparing(UserInfoDTO::login))
                .toList(),
            RepositoryInfoDTO.fromRepository(pullRequest.getRepository()),
            pullRequest.getAdditions(),
            pullRequest.getDeletions(),
            pullRequest.getMergedAt(),
            pullRequest.getClosedAt(),
            pullRequest.getHtmlUrl(),
            pullRequest.getCreatedAt(),
            pullRequest.getUpdatedAt()
        );
    }
}
