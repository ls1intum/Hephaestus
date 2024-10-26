package de.tum.in.www1.hephaestus.gitprovider.issue.dto;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State;
import de.tum.in.www1.hephaestus.gitprovider.label.dto.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserInfoDTO;

public record IssueInfoDTO(
    @NonNull Long id,
    @NonNull Integer number,
    @NonNull String title,
    @NonNull State state,
    @NonNull Integer commentsCount,
    UserInfoDTO author,
    List<LabelInfoDTO> labels,
    List<UserInfoDTO> assignees,
    String repositoryNameWithOwner,
    @NonNull String htmlUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}