package de.tum.in.www1.hephaestus.gitprovider.issuecomment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IssueCommentInfoDTO(
    @NonNull Long id,
    @NonNull AuthorAssociation authorAssociation,
    UserInfoDTO author,
    IssueInfoDTO issue,
    @NonNull String htmlUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
