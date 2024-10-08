package de.tum.in.www1.hephaestus.codereview.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestDTO;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReviewDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserProfileDTO(Long id, String login, String avatarUrl, OffsetDateTime firstContribution,
        Set<String> repositories, Set<PullRequestReviewDTO> activity, Set<PullRequestDTO> pullRequests) {
    public UserProfileDTO(Long id, String login, String avatarUrl, OffsetDateTime firstContribution,
            List<String> repositories) {
        this(id, login, avatarUrl, firstContribution, repositories.stream().collect(Collectors.toSet()), null, null);
    }
}
