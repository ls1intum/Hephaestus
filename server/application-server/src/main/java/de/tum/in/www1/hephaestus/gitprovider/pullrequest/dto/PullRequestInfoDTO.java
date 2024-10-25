package de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto;

import java.util.List;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State;
import de.tum.in.www1.hephaestus.gitprovider.label.dto.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestInfoDTO(
        @NonNull Long id,
        @NonNull Integer number,
        @NonNull String title,
        @NonNull State state,
        @NonNull Integer commentsCount,
        UserInfoDTO author,
        List<LabelInfoDTO> labels,
        List<UserInfoDTO> assignees,
        @NonNull String repositoryNameWithOwner,
        @NonNull Integer additions,
        @NonNull Integer deletions,
        @NonNull String mergedAt,
        @NonNull String htmlUrl,
        @NonNull String createdAt,
        @NonNull String updatedAt) {
}
