package de.tum.in.www1.hephaestus.gitprovider.issue;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.NonNull;

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
    Instant createdAt,
    Instant updatedAt
) {}
