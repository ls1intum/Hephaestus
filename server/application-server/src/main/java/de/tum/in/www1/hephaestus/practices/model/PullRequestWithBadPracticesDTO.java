package de.tum.in.www1.hephaestus.practices.model;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.lang.NonNull;

/**
 * Data transfer object for a pull request with its associated bad practices.
 *
 * <p>Contains full PR metadata (labels, repository, additions/deletions)
 * plus bad practice detection results.
 */
@Schema(description = "Pull request with associated bad practice detection results")
public record PullRequestWithBadPracticesDTO(
    @NonNull @Schema(description = "Unique identifier of the pull request") Long id,
    @NonNull @Schema(description = "Pull request number within the repository", example = "42") Integer number,
    @NonNull @Schema(description = "Title of the pull request") String title,
    @NonNull @Schema(description = "Current state of the pull request (OPEN, CLOSED)") Issue.State state,
    @NonNull @Schema(description = "Whether the pull request is in draft mode") Boolean isDraft,
    @NonNull @Schema(description = "Whether the pull request has been merged") Boolean isMerged,
    @NonNull @Schema(description = "Labels applied to the pull request") List<LabelInfoDTO> labels,
    @NonNull @Schema(description = "Repository the pull request belongs to") RepositoryInfoDTO repository,
    @NonNull @Schema(description = "Number of lines added", example = "150") Integer additions,
    @NonNull @Schema(description = "Number of lines deleted", example = "50") Integer deletions,
    @NonNull @Schema(description = "URL to the pull request on the git provider") String htmlUrl,
    @NonNull @Schema(description = "Timestamp when the pull request was created") Instant createdAt,
    @NonNull @Schema(description = "Timestamp when the pull request was last updated") Instant updatedAt,
    @NonNull @Schema(description = "AI-generated summary of detected bad practices") String badPracticeSummary,
    @NonNull @Schema(description = "Currently active bad practices") List<PullRequestBadPracticeDTO> badPractices,
    @NonNull
    @Schema(description = "Previously resolved or dismissed bad practices")
    List<PullRequestBadPracticeDTO> oldBadPractices
) {
    /**
     * Creates a DTO from a PullRequest entity with bad practice information.
     */
    public static PullRequestWithBadPracticesDTO fromPullRequest(
        PullRequest pullRequest,
        String summary,
        List<PullRequestBadPracticeDTO> badPractices,
        List<PullRequestBadPracticeDTO> oldBadPractices
    ) {
        return new PullRequestWithBadPracticesDTO(
            pullRequest.getId(),
            pullRequest.getNumber(),
            pullRequest.getTitle(),
            pullRequest.getState(),
            pullRequest.isDraft(),
            pullRequest.isMerged(),
            pullRequest
                .getLabels()
                .stream()
                .map(LabelInfoDTO::fromLabel)
                .sorted(Comparator.comparing(LabelInfoDTO::name))
                .toList(),
            RepositoryInfoDTO.fromRepository(pullRequest.getRepository()),
            pullRequest.getAdditions(),
            pullRequest.getDeletions(),
            pullRequest.getHtmlUrl(),
            pullRequest.getCreatedAt(),
            pullRequest.getUpdatedAt(),
            summary != null ? summary : "",
            badPractices,
            oldBadPractices
        );
    }
}
