package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewWorkFactsRepository.WorkFactsRow;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewWorkFactsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewWorkFactsDTO.WorkCheckState;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewWorkFactsDTO.WorkState;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewWorkFactsDTO.WorkStateReason;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.StoredEnums;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The provider's facts about one pull request, merge request or issue of the workspace. Conversations and documents
 * have none here and read as not found. Work in a repository the workspace does not monitor, or work the provider
 * deleted, has nothing stored here, which is an answer rather than an error: it reads as empty.
 */
@Service
@RequiredArgsConstructor
class ReviewWorkFactsQueryService {

    private final ReviewWorkFactsRepository repository;
    private final ReviewSubjectResolver subjectResolver;

    @Transactional(readOnly = true)
    public Optional<ReviewWorkFactsDTO> get(Long workspaceId, ArtifactKind artifactKind, Long artifactId) {
        String issueType;
        if (ArtifactKinds.PULL_REQUEST.equals(artifactKind)) {
            issueType = "PULL_REQUEST";
        } else if (ArtifactKinds.ISSUE.equals(artifactKind)) {
            issueType = "ISSUE";
        } else {
            throw new EntityNotFoundException("Reviewed work", artifactId);
        }
        return repository
                .findFacts(workspaceId, issueType, artifactId)
                .map(row -> facts(row, artifactKind, artifactId));
    }

    private ReviewWorkFactsDTO facts(WorkFactsRow row, ArtifactKind artifactKind, Long artifactId) {
        Long authorId = row.getAuthorId();
        return new ReviewWorkFactsDTO(
                artifactKind,
                artifactId,
                authorId == null
                        ? null
                        : subjectResolver.resolve(List.of(authorId)).get(authorId),
                state(row),
                StoredEnums.parse(row.getStateReason(), WorkStateReason.class),
                row.getDraft(),
                row.getSourceBranch(),
                row.getTargetBranch(),
                row.getHeadRevision(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getMergedAt(),
                row.getClosedAt(),
                row.getAdditions(),
                row.getDeletions(),
                row.getChangedFiles(),
                row.getCommits(),
                StoredEnums.parse(row.getCheckState(), WorkCheckState.class),
                row.getCheckObservedAt(),
                row.getLastSyncAt());
    }

    /** A merged pull request reads MERGED whatever state the provider's sync left on the row. */
    private static WorkState state(WorkFactsRow row) {
        if (row.getMergedAt() != null || Boolean.TRUE.equals(row.getMerged())) {
            return WorkState.MERGED;
        }
        WorkState stored = StoredEnums.parse(row.getState(), WorkState.class);
        return stored == null ? WorkState.OPEN : stored;
    }
}
