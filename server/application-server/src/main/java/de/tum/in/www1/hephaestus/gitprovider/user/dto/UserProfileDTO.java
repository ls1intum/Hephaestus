package de.tum.in.www1.hephaestus.gitprovider.user.dto;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.dto.PullRequestReviewInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.dto.RepositoryInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserProfileDTO(
        @NonNull UserInfoDTO userInfo,
        @NonNull OffsetDateTime firstContribution,
        @NonNull List<RepositoryInfoDTO> contributedRepositories,
        List<PullRequestReviewInfoDTO> reviewActivity,
        List<PullRequestInfoDTO> openPullRequests) {
}
