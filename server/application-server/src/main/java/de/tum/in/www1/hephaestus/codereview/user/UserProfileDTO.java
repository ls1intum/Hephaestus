package de.tum.in.www1.hephaestus.codereview.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestDTO;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReviewDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserProfileDTO(@NonNull Long id, @NonNull String login, @NonNull String avatarUrl,
        @NonNull OffsetDateTime firstContribution, @NonNull Set<String> repositories,
        Set<PullRequestReviewDTO> activity, Set<PullRequestDTO> pullRequests) {
    public UserProfileDTO(@NonNull Long id, @NonNull String login, @NonNull String avatarUrl,
            @NonNull OffsetDateTime firstContribution, @NonNull List<String> repositories) {
        this(id, login, avatarUrl, firstContribution, repositories.stream().collect(Collectors.toSet()), null, null);
    }
}
