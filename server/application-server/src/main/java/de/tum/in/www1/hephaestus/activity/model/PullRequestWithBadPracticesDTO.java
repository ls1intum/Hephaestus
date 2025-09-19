package de.tum.in.www1.hephaestus.activity.model;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.lang.NonNull;

public record PullRequestWithBadPracticesDTO(
    @NonNull Long id,
    @NonNull Integer number,
    @NonNull String title,
    @NonNull Issue.State state,
    @NonNull Boolean isDraft,
    @NonNull Boolean isMerged,
    @NonNull List<LabelInfoDTO> labels,
    @NonNull RepositoryInfoDTO repository,
    @NonNull Integer additions,
    @NonNull Integer deletions,
    @NonNull String htmlUrl,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt,
    @NonNull String badPracticeSummary,
    @NonNull List<PullRequestBadPracticeDTO> badPractices,
    @NonNull List<PullRequestBadPracticeDTO> oldBadPractices
) {
    public static PullRequestWithBadPracticesDTO fromPullRequest(
        PullRequest pullRequest,
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
            pullRequest.getBadPracticeSummary(),
            badPractices,
            oldBadPractices
        );
    }
}
