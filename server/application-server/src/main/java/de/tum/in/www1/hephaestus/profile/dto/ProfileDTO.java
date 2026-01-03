package de.tum.in.www1.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.NonNull;

/**
 * Profile-specific DTO representing a user's complete profile view.
 *
 * <p>This DTO lives in the profile module because it composes:
 * <ul>
 *   <li>User info (from gitprovider)</li>
 *   <li>Repository contributions (from gitprovider)</li>
 *   <li>Review activity WITH XP scores (profile-specific composition)</li>
 *   <li>Open pull requests (from gitprovider)</li>
 * </ul>
 *
 * <p>The gitprovider module has NO knowledge of XP or scoring.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProfileDTO(
    @NonNull UserInfoDTO userInfo,
    Instant firstContribution,
    @NonNull List<RepositoryInfoDTO> contributedRepositories,
    List<ProfileReviewActivityDTO> reviewActivity,
    List<PullRequestInfoDTO> openPullRequests
) {}
