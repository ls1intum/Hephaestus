package de.tum.cit.aet.hephaestus.practices.result;

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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ResultHistoryQueryService implements ResultHistoryQuery {
    private final ResultRepository results;
    private final ResultReadQuery read;
    private final ProblemRepository problems;
    private final ResultMarkRepository marks;
    private final PracticeVersionRepository versions;
    private final ReviewRepository reviews;
    private final ProductItemQuery items;
    private final ResultSourceAuthorization authorization;
    private final JdbcClient jdbc;
    private final ReviewAssignmentQuery assignments;

    @Override
    @Transactional(readOnly = true)
    public List<ResultHistoryEntry> forDeveloper(long workspaceId, long developerAccountId, Instant since, int limit) {
        limit(limit);
        if (!member(workspaceId, developerAccountId)) return List.of();
        return history(
                workspaceId,
                "r.developer_id=:developer AND r.recorded_at>=:since",
                Map.of("developer", developerAccountId, "since", Timestamp.from(since)),
                SourceUsePurpose.CONVERSATIONAL_MENTORING,
                limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultHistoryEntry> forItem(long workspaceId, long itemId, long practiceId, int limit) {
        limit(limit);
        if (items.find(workspaceId, itemId).isEmpty()) return List.of();
        return history(
                workspaceId,
                "r.item_id=:item AND r.practice_id=:practice",
                Map.of("item", itemId, "practice", practiceId),
                SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW,
                limit);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResultHistoryEntry> find(
            long workspaceId, UUID resultId, long viewerAccountId, SourceUsePurpose purpose) {
        if (!member(workspaceId, viewerAccountId)) return Optional.empty();
        return results.findByIdAndWorkspaceId(resultId, workspaceId)
                .filter(r -> Objects.equals(r.getDeveloperId(), viewerAccountId)
                        || (purpose == SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW
                                && admin(workspaceId, viewerAccountId)))
                .flatMap(r -> entry(r, purpose))
                .filter(ResultHistoryEntry::copyAuthorityComplete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultHistoryEntry> forAssignment(long workspaceId, ReviewAssignmentId id, int limit) {
        limit(limit);
        var assignment = assignments.find(workspaceId, id);
        if (assignment.isEmpty()) return List.of();
        var params = new LinkedHashMap<String, @Nullable Object>();
        params.put("item", id.itemId());
        params.put("practice", id.practiceId());
        params.put("developer", assignment.orElseThrow().developerId());
        return history(
                workspaceId,
                "r.item_id=:item AND r.practice_id=:practice AND r.developer_id IS NOT DISTINCT FROM :developer",
                params,
                SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW,
                limit);
    }

    private List<ResultHistoryEntry> history(
            long workspaceId,
            String selection,
            Map<String, ? extends @Nullable Object> scoped,
            SourceUsePurpose purpose,
            int limit) {
        var params = new LinkedHashMap<String, @Nullable Object>(scoped);
        params.put("ws", workspaceId);
        params.put("limit", limit);
        var ids = jdbc.sql("SELECT r.id FROM result r JOIN practice_version v ON v.id=r.practice_version_id AND"
                        + " v.workspace_id=r.workspace_id JOIN item i ON i.id=r.item_id AND"
                        + " i.workspace_id=r.workspace_id JOIN review q ON q.id=r.review_id AND"
                        + " q.workspace_id=r.workspace_id WHERE r.workspace_id=:ws AND r.item_version IS NOT NULL"
                        + " AND " + selection
                        + " ORDER BY r.recorded_at DESC,r.id::text DESC LIMIT :limit")
                .params(params)
                .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                .list();
        return read.ordered(workspaceId, ids).stream()
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

    private boolean member(long workspaceId, long accountId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM membership m JOIN account a ON a.id=m.account_id WHERE"
                        + " m.workspace_id=:ws AND m.account_id=:id AND m.left_at IS NULL AND NOT m.hidden AND"
                        + " a.status IN ('ACTIVE','UNCLAIMED'))")
                .param("ws", workspaceId)
                .param("id", accountId)
                .query(Boolean.class)
                .single();
    }

    private boolean admin(long workspaceId, long accountId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM membership WHERE workspace_id=:ws AND account_id=:id AND left_at"
                        + " IS NULL AND role IN ('ADMIN','OWNER'))")
                .param("ws", workspaceId)
                .param("id", accountId)
                .query(Boolean.class)
                .single();
    }

    private static void limit(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Use a history limit from 1 to 1000.");
    }
}
