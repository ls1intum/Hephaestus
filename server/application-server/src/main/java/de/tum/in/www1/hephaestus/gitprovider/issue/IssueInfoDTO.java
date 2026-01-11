package de.tum.in.www1.hephaestus.gitprovider.issue;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.NonNull;

@Schema(description = "Information about an issue from a repository")
public record IssueInfoDTO(
    @NonNull @Schema(description = "Unique identifier of the issue") Long id,
    @NonNull @Schema(description = "Issue number within the repository", example = "123") Integer number,
    @NonNull @Schema(description = "Title of the issue") String title,
    @NonNull @Schema(description = "Current state of the issue (OPEN, CLOSED)") State state,
    @NonNull @Schema(description = "Number of comments on the issue", example = "5") Integer commentsCount,
    @Schema(description = "Author of the issue") UserInfoDTO author,
    @Schema(description = "Labels applied to the issue") List<LabelInfoDTO> labels,
    @Schema(description = "Users assigned to the issue") List<UserInfoDTO> assignees,
    @Schema(description = "Full name of the repository (e.g., 'owner/repo')") String repositoryNameWithOwner,
    @NonNull @Schema(description = "URL to the issue on the git provider") String htmlUrl,
    @Schema(description = "Timestamp when the issue was created") Instant createdAt,
    @Schema(description = "Timestamp when the issue was last updated") Instant updatedAt
) {}
