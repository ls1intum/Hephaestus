package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Event published when bad practices are detected in a pull request.
 * This event contains immutable DTO snapshots, safe for async handling.
 */
public record BadPracticesDetectedEvent(
    @NonNull UserData user,
    @NonNull PullRequestData pullRequest,
    @NonNull List<BadPracticeData> badPractices,
    @Nullable String workspaceSlug
) {
    /**
     * Immutable snapshot of a User for event handling.
     */
    public record UserData(@NonNull String login, @Nullable String name, boolean notificationsEnabled) {
        public static UserData from(User user) {
            return new UserData(user.getLogin(), user.getName(), user.isNotificationsEnabled());
        }
    }

    /**
     * Immutable snapshot of a PullRequest for event handling.
     */
    public record PullRequestData(
        @NonNull Long id,
        int number,
        @NonNull String title,
        @NonNull String htmlUrl,
        @NonNull String repositoryName
    ) {
        public static PullRequestData from(PullRequest pr) {
            return new PullRequestData(
                pr.getId(),
                pr.getNumber(),
                pr.getTitle(),
                pr.getHtmlUrl(),
                pr.getRepository().getName()
            );
        }
    }

    /**
     * Immutable snapshot of a PullRequestBadPractice for event handling.
     */
    public record BadPracticeData(@NonNull String title, @NonNull String description, @NonNull String stateValue) {
        public static BadPracticeData from(PullRequestBadPractice badPractice) {
            return new BadPracticeData(
                badPractice.getTitle(),
                badPractice.getDescription(),
                badPractice.getState().getValue()
            );
        }
    }

    /**
     * Factory method to create the event from domain entities.
     */
    public static BadPracticesDetectedEvent create(
        User user,
        PullRequest pullRequest,
        List<PullRequestBadPractice> badPractices,
        String workspaceSlug
    ) {
        return new BadPracticesDetectedEvent(
            UserData.from(user),
            PullRequestData.from(pullRequest),
            badPractices.stream().map(BadPracticeData::from).collect(Collectors.toList()),
            workspaceSlug
        );
    }
}
