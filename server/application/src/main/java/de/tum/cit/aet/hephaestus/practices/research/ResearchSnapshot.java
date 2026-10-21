package de.tum.cit.aet.hephaestus.practices.research;

import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchParticipationQuery.ResearchQuestion;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewUsageSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Rows read from one REPEATABLE READ snapshot, with database identifiers. These identifiers never leave
 * the server: {@link ResearchDatasetAssembler} replaces every one of them with a per-export key.
 *
 * @param grants account id → the exact consent decision that authorised it at the snapshot
 * @param usage canonical whole-review usage, read only for reviews whose every subject is an
 *     authorised participant; an absent key means no retained ledger rows, not zero usage
 */
record ResearchSnapshot(
        long workspaceId,
        Instant cutoff,
        ResearchQuestion question,
        Map<Long, Long> grants,
        List<ResultRow> results,
        List<ProblemRow> problems,
        List<ReviewRow> reviews,
        List<ReviewSubjectRow> reviewSubjects,
        List<ReviewPracticeRow> reviewPractices,
        Map<UUID, ReviewUsageSnapshot> usage,
        List<ItemRow> items,
        List<PracticeRow> practices,
        List<PracticeVersionRow> practiceVersions,
        List<FeedbackRow> feedback,
        List<BasisRow> basis,
        List<DecisionRow> decisions,
        List<ResponseRow> responses) {

    ResearchSnapshot {
        grants = Map.copyOf(grants);
        results = List.copyOf(results);
        problems = List.copyOf(problems);
        reviews = List.copyOf(reviews);
        reviewSubjects = List.copyOf(reviewSubjects);
        reviewPractices = List.copyOf(reviewPractices);
        usage = Map.copyOf(usage);
        items = List.copyOf(items);
        practices = List.copyOf(practices);
        practiceVersions = List.copyOf(practiceVersions);
        feedback = List.copyOf(feedback);
        basis = List.copyOf(basis);
        decisions = List.copyOf(decisions);
        responses = List.copyOf(responses);
    }

    /**
     * {@code evidenceVersion} 0 is a migrated result: the capture contract was not established for it.
     * Whatever version or end marker it does retain is kept as retained, never cleared or invented.
     */
    record ResultRow(
            UUID id,
            UUID reviewId,
            long practiceId,
            @Nullable Long practiceVersionId,
            long itemId,
            long developerId,
            String outcome,
            @Nullable String severity,
            short evidenceVersion,
            boolean itemVersionRecorded,
            boolean coverageRecorded,
            Instant recordedAt,
            @Nullable Instant endedAt,
            @Nullable String endReason,
            @Nullable String activeMark) {}

    record ProblemRow(UUID id, UUID resultId, int position, String severity) {}

    record ReviewRow(
            UUID id,
            String kind,
            String origin,
            String status,
            Instant orderedAt,
            @Nullable Instant completedAt) {}

    /**
     * Who a whole review is about, from the existing assignment and result facts: its recorded
     * assignments, the ones with no result (and so no attributed subject), results with no developer,
     * and the accounts its results name. The accounts stay on the server.
     */
    record ReviewSubjectRow(
            UUID reviewId,
            int assignments,
            int unattributedAssignments,
            int unattributedResults,
            Set<Long> developers) {

        ReviewSubjectRow {
            developers = Set.copyOf(developers);
        }
    }

    /** One practice run of a review as the review owner recorded it, with its assignment counts by state. */
    record ReviewPracticeRow(
            UUID reviewId,
            long practiceId,
            @Nullable Long practiceVersionId,
            String status,
            @Nullable String reason,
            @Nullable Integer itemsListed,
            @Nullable Boolean listingComplete,
            int assignments,
            int listed,
            int judged,
            int reused,
            int withheld) {}

    record ItemRow(long id, String kind) {}

    record PracticeRow(long id, @Nullable String catalogSlug) {}

    record PracticeVersionRow(long id, long practiceId, int number, String reviewDigest) {}

    record FeedbackRow(
            UUID id,
            UUID reviewId,
            @Nullable Long itemId,
            long developerId,
            String destination,
            String state,
            @Nullable String stopReason,
            Instant createdAt,
            @Nullable Instant deliveredAt,
            @Nullable UUID replacesId) {}

    record BasisRow(
            UUID feedbackId, UUID resultId, @Nullable UUID problemId, int ordinal) {}

    record DecisionRow(UUID feedbackId, String action, Instant decidedAt) {}

    record ResponseRow(
            UUID feedbackId,
            @Nullable String resolution,
            @Nullable String usefulness,
            Instant createdAt) {}
}
