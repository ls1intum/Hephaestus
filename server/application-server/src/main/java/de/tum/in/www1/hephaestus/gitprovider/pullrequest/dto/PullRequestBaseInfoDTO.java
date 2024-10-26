package de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State;
import de.tum.in.www1.hephaestus.gitprovider.repository.dto.RepositoryInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestBaseInfoDTO(
        @NonNull Long id,
        @NonNull Integer number,
        @NonNull String title,
        @NonNull State state,
        RepositoryInfoDTO repository,
        @NonNull String htmlUrl) {

}