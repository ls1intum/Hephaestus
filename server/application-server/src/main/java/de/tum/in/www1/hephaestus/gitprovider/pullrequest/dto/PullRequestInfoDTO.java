package de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto;

import java.util.List;
import java.time.OffsetDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State;
import de.tum.in.www1.hephaestus.gitprovider.label.dto.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.dto.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestInfoDTO(
        @NonNull Long id,
        @NonNull Integer number,
        @NonNull String title,
        @NonNull State state,
        @NonNull Boolean isDraft,
        @NonNull Boolean isMerged,
        @NonNull Integer commentsCount,
        UserInfoDTO author,
        List<LabelInfoDTO> labels,
        List<UserInfoDTO> assignees,
        RepositoryInfoDTO repository,
        @NonNull Integer additions,
        @NonNull Integer deletions,
        OffsetDateTime mergedAt,
        OffsetDateTime closedAt,
        @NonNull String htmlUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

}
