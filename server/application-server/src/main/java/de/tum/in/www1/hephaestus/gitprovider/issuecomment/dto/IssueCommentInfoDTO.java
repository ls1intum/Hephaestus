package de.tum.in.www1.hephaestus.gitprovider.issuecomment.dto;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.issue.dto.IssueInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IssueCommentInfoDTO(
    @NonNull Long id,
    @NonNull AuthorAssociation authorAssociation,
    UserInfoDTO author,
    IssueInfoDTO issue,
    @NonNull String htmlUrl,
    @NonNull String createdAt, 
    @NonNull String updatedAt) {

}
