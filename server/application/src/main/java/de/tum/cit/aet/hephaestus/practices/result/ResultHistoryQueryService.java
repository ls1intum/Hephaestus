package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountMembershipQuery;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ProblemHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentQuery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ResultHistoryQueryService implements ResultHistoryQuery {
    private final ResultRepository results;
    private final ProblemRepository problems;
    private final ResultMarkRepository marks;
    private final PracticeVersionRepository versions;
    private final ReviewRepository reviews;
    private final ProductItemQuery items;
    private final ResultSourceAuthorization authorization;
    private final ResultHistoryRepository historyRows;
    private final ReviewAssignmentQuery assignments;
    private final AccountMembershipQuery memberships;

    @Override
    @Transactional(readOnly = true)
    public List<ResultHistoryEntry> forDeveloper(long workspaceId, long developerAccountId, Instant since, int limit) {
        limit(limit);
        if (!memberships.isActiveVisibleMember(workspaceId, developerAccountId)) return List.of();
        return history(
                historyRows.forDeveloper(workspaceId, developerAccountId, since, PageRequest.of(0, limit)),
                SourceUsePurpose.CONVERSATIONAL_MENTORING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultHistoryEntry> forItem(long workspaceId, long itemId, long practiceId, int limit) {
        limit(limit);
        if (items.find(workspaceId, itemId).isEmpty()) return List.of();
        return history(
                historyRows.forItem(workspaceId, itemId, practiceId, PageRequest.of(0, limit)),
                SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResultHistoryEntry> find(
            long workspaceId, UUID resultId, long viewerAccountId, SourceUsePurpose purpose) {
        if (!memberships.isActiveVisibleMember(workspaceId, viewerAccountId)) return Optional.empty();
        return results.findByIdAndWorkspaceId(resultId, workspaceId)
                .filter(r -> Objects.equals(r.getDeveloperId(), viewerAccountId)
                        || (purpose == SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW
                                && memberships.isAdministrator(workspaceId, viewerAccountId)))
                .flatMap(r -> entry(r, purpose))
                .filter(ResultHistoryEntry::copyAuthorityComplete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultHistoryEntry> forAssignment(long workspaceId, ReviewAssignmentId id, int limit) {
        limit(limit);
        var assignment = assignments.find(workspaceId, id);
        if (assignment.isEmpty()) return List.of();
        return history(
                historyRows.forAssignment(
                        workspaceId,
                        id.itemId(),
                        id.practiceId(),
                        assignment.orElseThrow().developerId(),
                        PageRequest.of(0, limit)),
                SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW);
    }

    private List<ResultHistoryEntry> history(List<Result> rows, SourceUsePurpose purpose) {
        return rows.stream()
                .map(r -> entry(r, purpose))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<ResultHistoryEntry> entry(Result result, SourceUsePurpose purpose) {
        if (result.getPracticeVersionId() == null || result.getItemVersion() == null) return Optional.empty();
        var version = versions.findByIdAndWorkspaceId(result.getPracticeVersionId(), result.getWorkspaceId());
        var item = items.find(result.getWorkspaceId(), result.getItemId());
        var review = reviews.findByIdAndWorkspaceId(result.getReviewId(), result.getWorkspaceId());
        if (version.isEmpty() || item.isEmpty() || review.isEmpty()) return Optional.empty();
        var facts = authorization.custody(result);
        boolean permitted = authorization.authorized(result, facts, purpose);
        var children = permitted
                ? problems
                        .findByWorkspaceIdAndResultIdOrderByPositionAsc(result.getWorkspaceId(), result.getId())
                        .stream()
                        .map(p -> {
                            var citation = p.getCitations().isArray()
                                            && !p.getCitations().isEmpty()
                                    ? p.getCitations().get(0)
                                    : null;
                            return new ProblemHistoryEntry(
                                    p.getId(),
                                    p.getSeverity(),
                                    p.getSummary(),
                                    citation == null || !citation.path("path").isString()
                                            ? null
                                            : citation.path("path").asString(),
                                    citation == null
                                                    || !citation.path("startLine")
                                                            .isInt()
                                            ? null
                                            : citation.path("startLine").intValue());
                        })
                        .toList()
                : List.<ProblemHistoryEntry>of();
        var questions = new ArrayList<String>();
        if (permitted && result.getEvidence() != null)
            for (var question : result.getEvidence().path("openQuestions"))
                if (question.path("question").isString())
                    questions.add(question.path("question").asString());
        var criteria = version.orElseThrow().getDefinition().get("criteria");
        return Optional.of(new ResultHistoryEntry(
                result.getId(),
                result.getReviewId(),
                result.getPracticeId(),
                version.orElseThrow().getPractice().getSlug(),
                version.orElseThrow().getNumber(),
                result.getItemId(),
                item.orElseThrow().kind().value(),
                result.getItemVersion(),
                result.getDeveloperId(),
                result.getOutcome(),
                result.getSeverity(),
                permitted ? result.getSummary() : null,
                result.getRecordedAt(),
                result.getEndedAt(),
                marks
                        .findByWorkspaceIdAndResultIdOrderByCreatedAtDescIdDesc(result.getWorkspaceId(), result.getId())
                        .stream()
                        .filter(m -> m.getRemovedAt() == null)
                        .map(m -> m.getKind())
                        .distinct()
                        .toList(),
                children,
                questions,
                item.orElseThrow(),
                review.orElseThrow().getOrigin(),
                permitted ? result.getExplanation() : null,
                permitted ? result.getEvidence() : null,
                permitted ? result.getExample() : null,
                permitted ? result.getCoverage() : null,
                permitted ? criteria : null,
                result.getEvidenceVersion(),
                facts.sourceJobIds(),
                facts.copiedIdentities(),
                facts.repositoryIds(),
                permitted));
    }

    private static void limit(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Use a history limit from 1 to 1000.");
    }
}
