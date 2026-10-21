package de.tum.cit.aet.hephaestus.practices.research;

import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchParticipationQuery;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.BasisRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.DecisionRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.FeedbackRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.ItemRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.PracticeRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.PracticeVersionRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.ProblemRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.ResponseRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.ResultRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.ReviewPracticeRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.ReviewRow;
import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.ReviewSubjectRow;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewUsageSnapshot;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads one workspace's research rows for the authorised participants from a single snapshot. Every
 * statement and every nested subquery binds {@code :workspaceId}, joins stay on the
 * workspace-composite keys, and the number of statements does not depend on the number of
 * participants, reviews or rows.
 *
 * <p>Results are not filtered by {@code evidence_version}: migrated version-0 history is part of the
 * dataset and keeps its own honest markers.
 */
@Component
@ConditionalOnServerRole
class ResearchDatasetSource {

    static final int MAX_RESULTS = 200_000;
    static final int MAX_FEEDBACK = 100_000;

    private final ResearchDatasetRepository rows;
    private final ResearchParticipationQuery consent;
    private final ReviewRuntimeQuery runtime;

    ResearchDatasetSource(
            ResearchDatasetRepository rows, ResearchParticipationQuery consent, ReviewRuntimeQuery runtime) {
        this.rows = rows;
        this.consent = consent;
        this.runtime = runtime;
    }

    /**
     * One read-only snapshot. Consent and usage are read inside it by their owners, so the grants, the
     * rows they authorise and the usage of those reviews come from the same moment. The window is
     * half-open, {@code [from, to)}.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 300)
    public ResearchSnapshot read(long workspaceId, Instant from, Instant to) {
        var question = consent.currentQuestion()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "This instance does not run a research program."));
        Instant cutoff = rows.cutoff();
        if (to.isAfter(cutoff.plusSeconds(86_400))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The window cannot end after today.");
        }
        Map<Long, Long> grants = consent.currentGrants(rows.members(workspaceId));
        Long[] accounts = grants.keySet().toArray(Long[]::new);
        requireWithin(rows.countResults(workspaceId, accounts, from, to), MAX_RESULTS);
        requireWithin(rows.countFeedback(workspaceId, accounts, from, to), MAX_FEEDBACK);

        var results = rows.results(workspaceId, accounts, from, to).stream()
                .map(row -> new ResultRow(
                        row.getId(),
                        row.getReviewId(),
                        row.getPracticeId(),
                        row.getPracticeVersionId(),
                        row.getItemId(),
                        row.getDeveloperId(),
                        row.getOutcome(),
                        row.getSeverity(),
                        row.getEvidenceVersion(),
                        row.getItemVersionRecorded(),
                        row.getCoverageRecorded(),
                        row.getRecordedAt(),
                        row.getEndedAt(),
                        row.getEndReason(),
                        row.getActiveMark()))
                .toList();
        var problems = rows.problems(workspaceId, accounts, from, to).stream()
                .map(row -> new ProblemRow(row.getId(), row.getResultId(), row.getPosition(), row.getSeverity()))
                .toList();
        var feedback = rows.feedback(workspaceId, accounts, from, to).stream()
                .map(row -> new FeedbackRow(
                        row.getId(),
                        row.getReviewId(),
                        row.getItemId(),
                        row.getDeveloperId(),
                        row.getDestination(),
                        row.getState(),
                        row.getStopReason(),
                        row.getCreatedAt(),
                        row.getDeliveredAt(),
                        row.getReplacesId()))
                .toList();
        var basis = rows.basis(workspaceId, accounts, from, to).stream()
                .map(row -> new BasisRow(row.getFeedbackId(), row.getResultId(), row.getProblemId(), row.getOrdinal()))
                .toList();
        var decisions = rows.decisions(workspaceId, accounts, from, to).stream()
                .map(row -> new DecisionRow(row.getFeedbackId(), row.getAction(), row.getDecidedAt()))
                .toList();
        var responses = rows.responses(workspaceId, accounts, from, to).stream()
                .map(row -> new ResponseRow(
                        row.getFeedbackId(), row.getResolution(), row.getUsefulness(), row.getCreatedAt()))
                .toList();
        var reviews = rows.reviews(workspaceId, accounts, from, to).stream()
                .map(row -> new ReviewRow(
                        row.getId(),
                        row.getKind(),
                        row.getOrigin(),
                        row.getStatus(),
                        row.getOrderedAt(),
                        row.getCompletedAt()))
                .toList();
        var reviewSubjects = rows.reviewSubjects(workspaceId, accounts, from, to).stream()
                .map(row -> new ReviewSubjectRow(
                        row.getReviewId(),
                        row.getAssignments(),
                        row.getUnattributedAssignments(),
                        row.getUnattributedResults(),
                        Arrays.stream(row.getDevelopers()).boxed().collect(Collectors.toSet())))
                .toList();
        var reviewPractices = rows.reviewPractices(workspaceId, accounts, from, to).stream()
                .map(row -> new ReviewPracticeRow(
                        row.getReviewId(),
                        row.getPracticeId(),
                        row.getPracticeVersionId(),
                        row.getStatus(),
                        row.getReason(),
                        row.getItemsListed(),
                        row.getListingComplete(),
                        row.getAssignments(),
                        row.getListed(),
                        row.getJudged(),
                        row.getReused(),
                        row.getWithheld()))
                .toList();
        var items = rows.items(workspaceId, accounts, from, to).stream()
                .map(row -> new ItemRow(row.getId(), row.getKind()))
                .toList();
        var practices = rows.practices(workspaceId, accounts, from, to).stream()
                .map(row -> new PracticeRow(row.getId(), row.getCatalogSlug()))
                .toList();
        var versions = rows.versions(workspaceId, accounts, from, to).stream()
                .map(row -> new PracticeVersionRow(
                        row.getId(), row.getPracticeId(), row.getNumber(), row.getReviewDigest()))
                .toList();

        // One bulk read from the accounting owner, and only for reviews whose every subject consented.
        Set<UUID> attributable = reviewSubjects.stream()
                .filter(row ->
                        ReviewSubjectScope.of(row, grants.keySet()) == ReviewSubjectScope.ALL_SUBJECTS_PARTICIPATING)
                .map(ReviewSubjectRow::reviewId)
                .collect(Collectors.toUnmodifiableSet());
        Map<UUID, ReviewUsageSnapshot> usage =
                attributable.isEmpty() ? Map.of() : runtime.usageByReview(workspaceId, attributable);
        return new ResearchSnapshot(
                workspaceId,
                cutoff,
                question,
                grants,
                results,
                problems,
                reviews,
                reviewSubjects,
                reviewPractices,
                usage,
                items,
                practices,
                versions,
                feedback,
                basis,
                decisions,
                responses);
    }

    private static void requireWithin(long count, int limit) {
        if (count > limit) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "The window holds too many records. Choose a shorter window.");
        }
    }
}
