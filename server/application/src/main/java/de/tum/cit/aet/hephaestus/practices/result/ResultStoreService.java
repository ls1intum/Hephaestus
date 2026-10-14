package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.model.Problem;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.practices.review.ReviewInvocationFence;
import de.tum.cit.aet.hephaestus.practices.review.ReviewItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewOrder;
import de.tum.cit.aet.hephaestus.practices.review.ReviewPracticeRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.AdmissionReceipt;
import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.practices.spi.CitationVerificationStatus;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustodyQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ProblemCandidate;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCandidate;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ResultStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewItemState;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import de.tum.cit.aet.hephaestus.practices.spi.WithheldItem;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ResultStoreService implements ResultStore {
    private final ReviewRepository reviews;
    private final ReviewItemRepository items;
    private final ReviewPracticeRepository practices;
    private final ReviewAssignmentQuery assignments;
    private final ReviewInvocationFence fence;
    private final ResultRepository results;
    private final ProblemRepository problems;
    private final ResultMarkRepository marks;
    private final ObjectProvider<ResultMarkService> marking;
    private final ResultProofMapper proof;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;
    private final PlatformTransactionManager transactions;
    private final WorkspaceRepository workspaces;
    private final PracticeReviewCoverageService scope;
    private final HistoryCustodyQuery custody;

    @Override
    public AdmissionReceipt admit(
            long workspaceId, UUID reviewId, UUID jobId, int attempt, List<ResultCandidate> candidates) {
        var frozen = List.copyOf(candidates);
        if (TransactionSynchronizationManager.isActualTransactionActive())
            return admitLocked(workspaceId, reviewId, jobId, attempt, frozen);
        var retry = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(3)
                .predicate(ResultStoreService::retryable)
                .build());
        return retry.invoke(() -> Objects.requireNonNull(new TransactionTemplate(transactions)
                .execute(status -> admitLocked(workspaceId, reviewId, jobId, attempt, frozen))));
    }

    private AdmissionReceipt admitLocked(
            long ws, UUID reviewId, UUID jobId, int attempt, List<ResultCandidate> candidates) {
        var review = fence.lock(ws, reviewId, jobId, attempt);
        practices.lockAssignments(ws, reviewId);
        var workspace = workspaces.findById(ws).orElseThrow();
        String scopeReason = workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE
                ? "WORKSPACE_INACTIVE"
                : workspace.getReviewSettings().getRolloutRevision() != review.getRolloutRevision()
                        ? "STALE_ROLLOUT_REVISION"
                        : null;
        var eligible = scope.eligibleAccountIds(ws);
        var copyAuthority = custody.find(ws, List.of(jobId));
        var admitted = new ArrayList<UUID>();
        var withheld = new ArrayList<WithheldItem>();
        var seen = new HashSet<ReviewAssignmentId>();
        for (var c : candidates.stream()
                .sorted(Comparator.comparingLong(ResultCandidate::practiceId)
                        .thenComparingLong(ResultCandidate::itemId))
                .toList()) {
            var id = new ReviewAssignmentId(reviewId, c.practiceId(), c.itemId());
            if (!seen.add(id)) throw conflict("An assignment occurs twice in this request.");
            var member = items.lockAssignment(ws, reviewId, c.practiceId(), c.itemId())
                    .orElseThrow(() -> conflict("The candidate has no frozen assignment."));
            var assignment = assignments
                    .find(ws, id)
                    .orElseThrow(() -> conflict("The assignment has no verifiable pinned facts."));
            if (!jobId.equals(member.getJobId())
                    || c.practiceVersionId() != assignment.practice().practiceVersionId()
                    || !c.itemVersion().equals(assignment.version().value())
                    || !Objects.equals(c.developerId(), assignment.developerId()))
                throw conflict("The candidate does not match its frozen assignment.");
            String currentReason =
                    !copyAuthority.complete() || !copyAuthority.sourceJobIds().contains(jobId)
                            ? "COPY_AUTHORITY_UNAVAILABLE"
                            : scopeReason != null
                                    ? scopeReason
                                    : c.developerId() != null && !eligible.contains(c.developerId())
                                            ? "ROLE_UNAVAILABLE"
                                            : null;
            if (currentReason != null) {
                if (member.getState() != ReviewItemState.LISTED)
                    throw conflict("Current processing policy no longer permits this proof.");
                member.setState(ReviewItemState.WITHHELD);
                member.setReason(currentReason);
                member.setAttemptedAt(clock.instant());
                withheld.add(new WithheldItem(c.practiceId(), c.itemId(), currentReason));
                continue;
            }
            if (member.getState() == ReviewItemState.JUDGED) {
                if (member.getDetail() == null
                        || !member.getDetail().has("admissionAttempt")
                        || member.getDetail().path("admissionAttempt").asInt() != attempt)
                    throw conflict("Another attempt admitted this assignment.");
                var prior = results.findByIdAndWorkspaceId(Objects.requireNonNull(member.getResultId()), ws)
                        .orElseThrow();
                if (!json.valueToTree(proof.snapshot(prior).candidate()).equals(json.valueToTree(c)))
                    throw conflict("The admitted body cannot change.");
                admitted.add(prior.getId());
                continue;
            }
            if (member.getState() == ReviewItemState.WITHHELD) {
                withheld.add(new WithheldItem(c.practiceId(), c.itemId(), Objects.requireNonNull(member.getReason())));
                continue;
            }
            if (member.getState() != ReviewItemState.LISTED) throw conflict("This assignment is already terminal.");
            var refusal = refusal(c);
            if (refusal != null) {
                member.setState(ReviewItemState.WITHHELD);
                member.setReason(refusal.wireValue());
                member.setAttemptedAt(clock.instant());
                withheld.add(new WithheldItem(c.practiceId(), c.itemId(), refusal.wireValue()));
                continue;
            }
            jdbc.sql("SELECT id FROM item WHERE workspace_id=:ws AND id=:id FOR UPDATE")
                    .param("ws", ws)
                    .param("id", c.itemId())
                    .query((rs, n) -> rs.getLong(1))
                    .single();
            var at = clock.instant();
            if (review.getAdmittedAt() == null) review.setAdmittedAt(at);
            var result = Result.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(ws)
                    .reviewId(reviewId)
                    .practiceId(c.practiceId())
                    .practiceVersionId(c.practiceVersionId())
                    .itemId(c.itemId())
                    .itemVersion(c.itemVersion())
                    .developerId(c.developerId())
                    .outcome(c.outcome())
                    .summary(c.summary())
                    .explanation(c.explanation())
                    .severity(c.problems().stream()
                            .map(ProblemCandidate::severity)
                            .min(Comparator.comparingInt(Severity::rank))
                            .orElse(null))
                    .evidence(json.valueToTree(c.evidence()))
                    .example(c.example() == null ? null : json.valueToTree(c.example()))
                    .coverage(json.valueToTree(c.coverage()))
                    .recordedAt(at)
                    .build();
            var current = results.findCurrentClaim(ws, c.practiceId(), c.itemId(), c.developerId());
            if (current.isPresent()) {
                var old = results.lockByIdAndWorkspaceId(current.orElseThrow().getId(), ws)
                        .orElseThrow();
                var previous =
                        reviews.findByIdAndWorkspaceId(old.getReviewId(), ws).orElseThrow();
                if (new ReviewOrder(review.getOrderedAt(), at, reviewId)
                                .compareTo(
                                        new ReviewOrder(previous.getOrderedAt(), old.getRecordedAt(), previous.getId()))
                        <= 0) result.end(ResultEndReason.OUTRANKED, at);
                else {
                    old.end(ResultEndReason.REPLACED, at.isBefore(old.getRecordedAt()) ? old.getRecordedAt() : at);
                    results.flush();
                }
            }
            results.saveAndFlush(result);
            for (var child : c.problems())
                problems.save(Problem.builder()
                        .id(UUID.randomUUID())
                        .workspaceId(ws)
                        .resultId(result.getId())
                        .position(child.position())
                        .key(child.key())
                        .severity(child.severity())
                        .summary(child.summary())
                        .explanation(child.explanation())
                        .citations(json.valueToTree(child.citations()))
                        .basis(json.valueToTree(child.basis()))
                        .nextStep(child.nextStep())
                        .build());
            member.setState(ReviewItemState.JUDGED);
            member.setResultId(result.getId());
            member.setAttemptedAt(at);
            ObjectNode detail = member.getDetail() == null
                    ? json.createObjectNode()
                    : (ObjectNode) member.getDetail().deepCopy();
            detail.put("admissionAttempt", attempt);
            detail.set("custody", json.valueToTree(copyAuthority));
            member.setDetail(detail);
            admitted.add(result.getId());
        }
        return new AdmissionReceipt(admitted, withheld);
    }

    private static @Nullable CandidateRefusal refusal(ResultCandidate c) {
        if (c.summary().isBlank()
                || c.summary().length() > 160
                || c.explanation().isBlank()
                || c.explanation().length() > 10000) return CandidateRefusal.MALFORMED_CANDIDATE;
        if ((c.outcome() == ResultOutcome.NOT_MET) != !c.problems().isEmpty())
            return CandidateRefusal.OUTCOME_PROBLEM_MISMATCH;
        var keys = new HashSet<String>();
        var positions = new HashSet<Integer>();
        for (var p : c.problems()) {
            if (!positions.add(p.position())
                    || p.position() < 0
                    || p.summary().isBlank()
                    || p.summary().length() > 160
                    || p.explanation().isBlank()
                    || p.explanation().length() > 4000
                    || (p.nextStep() != null && p.nextStep().length() > 1000))
                return CandidateRefusal.MALFORMED_CANDIDATE;
            if (p.key() != null && !keys.add(p.key())) return CandidateRefusal.DUPLICATE_PROBLEM_KEY;
            if (p.citations().isEmpty()) return CandidateRefusal.RESULT_CITATIONS_REQUIRED;
            if (p.citations().stream()
                    .anyMatch(x -> x.verification() == null
                            || x.verification().status() != CitationVerificationStatus.VERIFIED))
                return CandidateRefusal.QUOTE_UNVERIFIED;
        }
        if (c.evidence().citations().stream()
                .anyMatch(x ->
                        x.verification() == null || x.verification().status() != CitationVerificationStatus.VERIFIED))
            return CandidateRefusal.QUOTE_UNVERIFIED;
        return null;
    }

    private static boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof SQLException sql
                    && ("40001".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState()))) return true;
        return false;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    @Override
    public int endCurrent(long workspaceId, long itemId, ResultEndReason reason, Instant at) {
        return Objects.requireNonNull(new TransactionTemplate(transactions).execute(status -> {
            jdbc.sql("SELECT id FROM item WHERE workspace_id=:ws AND id=:id FOR UPDATE")
                    .param("ws", workspaceId)
                    .param("id", itemId)
                    .query((rs, n) -> rs.getLong(1))
                    .single();
            return results.endCurrent(workspaceId, itemId, reason, at);
        }));
    }

    @Override
    public UUID mark(long workspaceId, UUID resultId, ResultMarkKind kind, long actorId, String reason) {
        return marking.getObject()
                .create(workspaceId, resultId, kind, actorId, reason)
                .getId();
    }

    @Override
    public boolean removeMark(long workspaceId, UUID markId, long actorId, String reason) {
        return Objects.requireNonNull(new TransactionTemplate(transactions).execute(status -> {
            var mark = marks.findById(markId)
                    .filter(m -> m.getWorkspaceId() == workspaceId)
                    .orElseThrow();
            var active = mark.getRemovedAt() == null;
            marking.getObject().remove(workspaceId, mark.getResultId(), markId, actorId, reason);
            return active;
        }));
    }
}
