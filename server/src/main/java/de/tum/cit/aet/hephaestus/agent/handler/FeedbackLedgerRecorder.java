package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackContinuityKey;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackFindingDisplayRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackFindingRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackOrigin;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSurface;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementAnchorKind;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementAnchorSide;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementPostedState;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementSurface;
import de.tum.cit.aet.hephaestus.practices.feedback.PolicyFloorSelector;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the delivered-feedback LEDGER (ADR 0021 C6): after the hardened delivery path posts the MR/issue
 * summary + inline notes, this persists ONE {@link Feedback} unit (surface IN_CONTEXT) describing what was
 * actually delivered, the {@link de.tum.cit.aet.hephaestus.practices.feedback.FeedbackFinding}s it fused,
 * and a {@link FeedbackPlacement} per posted comment (SUMMARY + one per inline note).
 *
 * <p><b>Non-regressing by construction.</b> This is a pure write-through side-effect invoked AFTER the
 * existing post, in its OWN {@link Propagation#REQUIRES_NEW} transaction, and callers wrap the call in a
 * try/catch that only logs — a ledger failure can therefore never roll back or alter the delivery the
 * student already received. Delete this recorder and delivery is byte-identical.
 *
 * <p>Idempotent: a job retry that re-delivers finds the unit already recorded ({@code (agent_job_id,
 * unit_ordinal)} guard) and does nothing.
 */
@Component
public class FeedbackLedgerRecorder {

    private static final Logger log = LoggerFactory.getLogger(FeedbackLedgerRecorder.class);

    private static final int IN_CONTEXT_UNIT_ORDINAL = 0;

    /** SUPPRESSED units (B2) start here so they never collide with the live IN_CONTEXT unit (ordinal 0). */
    private static final int SUPPRESSED_UNIT_ORDINAL_BASE = 1000;

    /** Policy-floor SUPPRESSED units (C3) start here — clear of the live unit (0) and the B2 base (1000). */
    private static final int POLICY_FLOOR_UNIT_ORDINAL_BASE = 2000;

    private final PracticeFindingRepository practiceFindingRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackFindingRepository feedbackFindingRepository;
    private final FeedbackPlacementRepository feedbackPlacementRepository;
    private final PracticeReviewProperties reviewProperties;

    FeedbackLedgerRecorder(
        PracticeFindingRepository practiceFindingRepository,
        FeedbackRepository feedbackRepository,
        FeedbackFindingRepository feedbackFindingRepository,
        FeedbackPlacementRepository feedbackPlacementRepository,
        PracticeReviewProperties reviewProperties
    ) {
        this.practiceFindingRepository = practiceFindingRepository;
        this.feedbackRepository = feedbackRepository;
        this.feedbackFindingRepository = feedbackFindingRepository;
        this.feedbackPlacementRepository = feedbackPlacementRepository;
        this.reviewProperties = reviewProperties;
    }

    /**
     * Record the in-context feedback ledger for a just-delivered review. Best-effort: callers must wrap
     * this in try/catch — it runs REQUIRES_NEW so its own failure never poisons the delivery transaction.
     *
     * @param job the delivered job ({@code deliveryCommentId} holds the posted summary id)
     * @param delivery the composed content that was posted (summary body + inline notes)
     * @param artifact PR vs ISSUE (issues have no inline placements)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AgentJob job, DeliveryContent delivery, WorkArtifact artifact) {
        if (delivery == null) {
            return;
        }
        List<PracticeFinding> findings = practiceFindingRepository.findByAgentJobId(job.getId());
        if (findings.isEmpty()) {
            return;
        }
        if (feedbackRepository.existsByAgentJobIdAndUnitOrdinal(job.getId(), IN_CONTEXT_UNIT_ORDINAL)) {
            return; // already recorded (job retry)
        }

        PracticeFinding any = findings.get(0);
        long recipientUserId = any.getContributor().getId();
        WorkArtifact targetType = any.getTargetType();
        Long targetId = any.getTargetId();
        String continuityKey = continuityKeyFor(any);

        // Re-review supersession (ADR 0021 re-review UX): the prior live unit on this continuity line is the
        // one whose SUMMARY comment the delivery just edited in place. Flip it to SUPERSEDED and point this
        // new row's supersedes_id at it, preserving the temporal record of what the student saw each run.
        UUID supersedesId = feedbackRepository
            .findFirstByContinuityKeyAndStateOrderByCreatedAtDesc(continuityKey, FeedbackState.DELIVERED)
            .map(Feedback::getId)
            .orElse(null);

        Instant now = Instant.now();
        Feedback feedback = feedbackRepository.save(
            Feedback.builder()
                .idempotencyKey(job.getId() + ":" + IN_CONTEXT_UNIT_ORDINAL)
                .agentJobId(job.getId())
                .workspaceId(job.getWorkspace().getId())
                .targetType(targetType)
                .targetId(targetId)
                .recipientUserId(recipientUserId)
                .subjectUserId(any.getSubjectUserId())
                .surface(FeedbackSurface.IN_CONTEXT)
                .unitOrdinal(IN_CONTEXT_UNIT_ORDINAL)
                .state(FeedbackState.DELIVERED)
                .renderedBody(delivery.mrNote())
                .origin(FeedbackOrigin.AGENT)
                .modelId(job.getLlmModel())
                .composerVersion(DeliveryComposer.COMPOSER_VERSION)
                .continuityKey(continuityKey)
                .supersedesId(supersedesId)
                .createdAt(now)
                .deliveredAt(now)
                .build()
        );

        // Flip the prior unit AFTER the new DELIVERED row lands, so there is never a window with zero live
        // units on this line (a concurrent reader always sees exactly one). Native update — @Immutable forbids
        // an ORM-level state mutation.
        if (supersedesId != null) {
            feedbackRepository.updateState(supersedesId, FeedbackState.SUPERSEDED.name());
        }

        // The policy floor (C3) caps the volume surfaced this run; the dropped tail is NOT part of the
        // DELIVERED unit — it is recorded as SUPPRESSED below. Compute it once so the DELIVERED binding
        // excludes it (else a dropped finding is bound to BOTH units and an eval double-counts it as delivered).
        List<PracticeFinding> policyDropped = reviewProperties.policyFloor()
            ? PolicyFloorSelector.partition(
                  findings
                      .stream()
                      .filter(f -> f.getVerdict() == Verdict.NOT_OBSERVED)
                      .toList(),
                  DeliveryComposer.MAX_IMPROVEMENT_SUGGESTIONS
              ).dropped()
            : List.of();
        Set<UUID> droppedIds = policyDropped.stream().map(PracticeFinding::getId).collect(Collectors.toSet());

        // Bind every DELIVERED finding: NOT_OBSERVED (the problems surfaced) lead as PRIMARY, OBSERVED
        // strengths as SUPPORTING; NOT_APPLICABLE abstentions and policy-dropped problems are excluded.
        List<PracticeFinding> assessed = findings
            .stream()
            .filter(f -> f.getVerdict() != Verdict.NOT_APPLICABLE)
            .filter(f -> !droppedIds.contains(f.getId()))
            .sorted(Comparator.comparingInt(f -> f.getSeverity().ordinal()))
            .toList();
        int ordinal = 0;
        for (PracticeFinding f : assessed) {
            FeedbackFindingDisplayRole role =
                f.getVerdict() == Verdict.NOT_OBSERVED
                    ? FeedbackFindingDisplayRole.PRIMARY
                    : FeedbackFindingDisplayRole.SUPPORTING;
            feedbackFindingRepository.insertIfAbsent(feedback.getId(), f.getId(), role.name(), ordinal++);
        }

        // SUMMARY placement — fully recoverable: external_ref is the posted summary comment id.
        feedbackPlacementRepository.save(
            FeedbackPlacement.builder()
                .feedback(feedback)
                .placement(PlacementSurface.SUMMARY)
                .externalRef(job.getDeliveryCommentId())
                .postedState(
                    job.getDeliveryCommentId() != null ? PlacementPostedState.POSTED : PlacementPostedState.FAILED
                )
                .createdAt(now)
                .build()
        );

        // INLINE placements (PR only) — the ANCHOR is recoverable; the per-note vendor id is NOT returned
        // by the inline channel today, so external_ref stays null (recovering it is a later SPI change).
        if (artifact == WorkArtifact.PULL_REQUEST) {
            for (DiffNote note : delivery.diffNotes()) {
                feedbackPlacementRepository.save(
                    FeedbackPlacement.builder()
                        .feedback(feedback)
                        .placement(PlacementSurface.INLINE)
                        .anchorKind(note.endLine() != null ? PlacementAnchorKind.RANGE : PlacementAnchorKind.LINE)
                        .anchorPath(note.filePath())
                        .anchorStartLine(note.startLine())
                        .anchorEndLine(note.endLine())
                        .anchorSide(PlacementAnchorSide.NEW)
                        .postedState(PlacementPostedState.POSTED)
                        .createdAt(now)
                        .build()
                );
            }
        }

        // C3 policy floor: record the volume-capped tail as SUPPRESSED units so an eval excludes them (they
        // were model-correct, just policy-withheld) — best-effort, never affects the DELIVERED unit.
        if (!policyDropped.isEmpty()) {
            try {
                recordPolicyFloor(job, policyDropped);
            } catch (RuntimeException e) {
                log.warn("Policy-floor ledger write failed (delivery unaffected): jobId={}", job.getId(), e);
            }
        }

        log.info(
            "Feedback ledger recorded: jobId={}, unit={}, findings={}, inlinePlacements={}, continuityKey={}",
            job.getId(),
            feedback.getId(),
            assessed.size(),
            artifact == WorkArtifact.PULL_REQUEST ? delivery.diffNotes().size() : 0,
            continuityKey
        );
    }

    /**
     * Record the volume-cap tail (C3): each policy-DROPPED problem is written as a SUPPRESSED /
     * POLICY_FLOOR_DROP unit so an eval can exclude it rather than score a model-correct-but-policy-withheld
     * finding as a miss.
     */
    private void recordPolicyFloor(AgentJob job, List<PracticeFinding> dropped) {
        Instant now = Instant.now();
        int index = 0;
        for (PracticeFinding droppedFinding : dropped) {
            int unitOrdinal = POLICY_FLOOR_UNIT_ORDINAL_BASE + index++;
            if (feedbackRepository.existsByAgentJobIdAndUnitOrdinal(job.getId(), unitOrdinal)) {
                continue;
            }
            Feedback unit = feedbackRepository.save(
                Feedback.builder()
                    .idempotencyKey(job.getId() + ":" + unitOrdinal)
                    .agentJobId(job.getId())
                    .workspaceId(job.getWorkspace().getId())
                    .targetType(droppedFinding.getTargetType())
                    .targetId(droppedFinding.getTargetId())
                    .recipientUserId(droppedFinding.getContributor().getId())
                    .subjectUserId(droppedFinding.getSubjectUserId())
                    .surface(FeedbackSurface.IN_CONTEXT)
                    .unitOrdinal(unitOrdinal)
                    .state(FeedbackState.SUPPRESSED)
                    .suppressionReason(FeedbackSuppressionReason.POLICY_FLOOR_DROP)
                    .origin(FeedbackOrigin.AGENT)
                    .modelId(job.getLlmModel())
                    .composerVersion(DeliveryComposer.COMPOSER_VERSION)
                    .createdAt(now)
                    .build()
            );
            feedbackFindingRepository.insertIfAbsent(
                unit.getId(),
                droppedFinding.getId(),
                FeedbackFindingDisplayRole.PRIMARY.name(),
                0
            );
        }
        log.info("Policy-floor: jobId={}, dropped(suppressed)={}", job.getId(), dropped.size());
    }

    /**
     * The external comment id of the CURRENT live in-context summary for this job's continuity line, if any —
     * the comment a re-review should EDIT IN PLACE rather than post anew (ADR 0021 re-review UX). Derived from
     * the job's own findings so this read key is computed identically to the write key in {@link #record},
     * with no reliance on PR↔finding id coupling. Empty when this is the first delivery on the line, the prior
     * unit is already superseded/failed, or it had no recoverable SUMMARY placement (e.g. the post had failed).
     *
     * <p>Best-effort and side-effect free: runs in its own read-only transaction; callers treat any failure as
     * "no prior summary" and post fresh.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<String> priorLiveSummaryRef(AgentJob job) {
        List<PracticeFinding> findings = practiceFindingRepository.findByAgentJobId(job.getId());
        if (findings.isEmpty()) {
            return Optional.empty();
        }
        String continuityKey = continuityKeyFor(findings.get(0));
        return feedbackRepository
            .findFirstByContinuityKeyAndStateOrderByCreatedAtDesc(continuityKey, FeedbackState.DELIVERED)
            .flatMap(prior ->
                feedbackPlacementRepository
                    .findByFeedbackId(prior.getId())
                    .stream()
                    .filter(p -> p.getPlacement() == PlacementSurface.SUMMARY)
                    .map(FeedbackPlacement::getExternalRef)
                    .filter(ref -> ref != null && !ref.isBlank())
                    .findFirst()
            );
    }

    /**
     * Record a SUPPRESSED ledger unit for a locus withheld by reaction-aware suppression (ADR 0021, B2) — the
     * student already DISPUTED / marked NOT_APPLICABLE / DISMISSED this concern, so it was NOT re-delivered.
     * Writing it (rather than silently dropping) means an eval sees the finding was deliberately withheld, not
     * a model miss. Uses a high {@code unit_ordinal} ({@value #SUPPRESSED_UNIT_ORDINAL_BASE}+) so it never
     * collides with the live IN_CONTEXT unit (ordinal 0) on the {@code (agent_job_id, unit_ordinal)} guard.
     * Best-effort: REQUIRES_NEW, callers wrap in try/catch — a ledger failure never affects delivery.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuppressed(AgentJob job, PracticeFinding finding, FeedbackSuppressionReason reason, int index) {
        int unitOrdinal = SUPPRESSED_UNIT_ORDINAL_BASE + index;
        if (feedbackRepository.existsByAgentJobIdAndUnitOrdinal(job.getId(), unitOrdinal)) {
            return; // already recorded (job retry)
        }
        Instant now = Instant.now();
        Feedback feedback = feedbackRepository.save(
            Feedback.builder()
                .idempotencyKey(job.getId() + ":" + unitOrdinal)
                .agentJobId(job.getId())
                .workspaceId(job.getWorkspace().getId())
                .targetType(finding.getTargetType())
                .targetId(finding.getTargetId())
                .recipientUserId(finding.getContributor().getId())
                .subjectUserId(finding.getSubjectUserId())
                .surface(FeedbackSurface.IN_CONTEXT)
                .unitOrdinal(unitOrdinal)
                .state(FeedbackState.SUPPRESSED)
                .suppressionReason(reason)
                .origin(FeedbackOrigin.AGENT)
                .modelId(job.getLlmModel())
                .composerVersion(DeliveryComposer.COMPOSER_VERSION)
                .continuityKey(continuityKeyFor(finding))
                .createdAt(now)
                .build()
        );
        feedbackFindingRepository.insertIfAbsent(
            feedback.getId(),
            finding.getId(),
            FeedbackFindingDisplayRole.PRIMARY.name(),
            0
        );
        log.info(
            "Feedback suppressed (reaction-aware): jobId={}, unit={}, reason={}, correlationKey={}",
            job.getId(),
            feedback.getId(),
            reason,
            finding.getCorrelationKey()
        );
    }

    /** The stable continuity line for a finding: (target, recipient, in-context surface). */
    private static String continuityKeyFor(PracticeFinding any) {
        return FeedbackContinuityKey.compute(
            any.getTargetType().name(),
            any.getTargetId(),
            any.getContributor().getId(),
            FeedbackSurface.IN_CONTEXT
        );
    }
}
