package de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto;

import java.util.Comparator;

import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.label.dto.LabelDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.label.dto.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.repository.dto.RepositoryDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserInfoDTO;

@Component
public class PullRequestDTOConverter {

    private final LabelDTOConverter labelDTOConverter;
    private final UserDTOConverter userDTOConverter;
    private final RepositoryDTOConverter repositoryDTOConverter;

    public PullRequestDTOConverter(
            LabelDTOConverter labelDTOConverter,
            UserDTOConverter userDTOConverter,
            RepositoryDTOConverter repositoryDTOConverter) {
        this.labelDTOConverter = labelDTOConverter;
        this.userDTOConverter = userDTOConverter;
        this.repositoryDTOConverter = repositoryDTOConverter;
    }

    public PullRequestInfoDTO convertToDTO(PullRequest pullRequest) {
        return new PullRequestInfoDTO(
                pullRequest.getId(),
                pullRequest.getNumber(),
                pullRequest.getTitle(),
                pullRequest.getState(),
                pullRequest.isDraft(),
                pullRequest.isMerged(),
                pullRequest.getCommentsCount(),
                userDTOConverter.convertToDTO(pullRequest.getAuthor()),
                pullRequest.getLabels()
                        .stream()
                        .map(labelDTOConverter::convertToDTO)
                        .sorted(Comparator.comparing(LabelInfoDTO::name))
                        .toList(),
                pullRequest.getAssignees()
                        .stream()
                        .map(userDTOConverter::convertToDTO)
                        .sorted(Comparator.comparing(UserInfoDTO::login))
                        .toList(),
                repositoryDTOConverter.convertToDTO(pullRequest.getRepository()),
                pullRequest.getAdditions(),
                pullRequest.getDeletions(),
                pullRequest.getMergedAt(),
                pullRequest.getClosedAt(),
                pullRequest.getHtmlUrl(),
                pullRequest.getCreatedAt(),
                pullRequest.getUpdatedAt());
    }

    public PullRequestBaseInfoDTO convertToBaseDTO(PullRequest pullRequest) {
        return new PullRequestBaseInfoDTO(
                pullRequest.getId(),
                pullRequest.getNumber(),
                pullRequest.getTitle(),
                pullRequest.getState(),
                pullRequest.isDraft(),
                pullRequest.isMerged(),
                repositoryDTOConverter.convertToDTO(pullRequest.getRepository()),
                pullRequest.getHtmlUrl());
    }

}
