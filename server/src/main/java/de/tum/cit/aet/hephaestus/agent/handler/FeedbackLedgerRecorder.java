package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.FindingAnchor.DiffAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.DeliveredSignal;
import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackFindingRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackProvenance;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSurface;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackThreadKey;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementAnchorKind;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementAnchorSide;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementSlot;
import de.tum.cit.aet.hephaestus.practices.feedback.PolicyFloorSelector;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the delivered-feedback LEDGER (ADR 0021 C6): after the hardened delivery path posts the MR/issue
 * summary + inline notes, this persists ONE {@link Feedback} unit (surface IN_CONTEXT) describing what was
 * actually delivered, the {@link de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservation}s it fused,
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
     * @param inlineSignals the per-inline-note delivery outcomes (vendor note id, thread id, disposition)
     *     emitted by the inline channel; empty for issues and for channels that cannot reconcile per-thread
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
        AgentJob job,
        DeliveryContent delivery,
        WorkArtifact artifact,
        List<DeliveredSignal> inlineSignals
    ) {
        if (delivery == null) {
            return;
        }
        List<Observation> findings = practiceFindingRepository.findByAgentJobId(job.getId());
        if (findings.isEmpty()) {
            return;
        }
        if (feedbackRepository.existsByAgentJobIdAndPosition(job.getId(), IN_CONTEXT_UNIT_ORDINAL)) {
            return; // already recorded (job retry)
        }

        Observation any = findings.get(0);
        long recipientUserId = any.getAboutUserId();
        WorkArtifact artifactType = any.getArtifactType();
        Long artifactId = any.getArtifactId();
        String feedbackThreadKey = feedbackThreadKeyFor(any);

        // Re-review supersession (ADR 0021 re-review UX): the prior live unit on this continuity line is the
        // one whose SUMMARY comment the delivery just edited in place. Flip it to SUPERSEDED and point this
        // new row's supersedes_id at it, preserving the temporal record of what the student saw each run.
        UUID supersedesId = feedbackRepository
            .findFirstByThreadKeyAndDeliveryStateOrderByCreatedAtDesc(
                feedbackThreadKey,
                FeedbackDeliveryState.DELIVERED
            )
            .map(Feedback::getId)
            .orElse(null);

        Instant now = Instant.now();
        Feedback feedback = feedbackRepository.save(
            Feedback.builder()
                .agentJobId(job.getId())
                .workspaceId(job.getWorkspace().getId())
                .artifactType(artifactType)
                .artifactId(artifactId)
                .recipientUserId(recipientUserId)
                .subjectUserId(any.getAboutUserId())
                .channel(FeedbackSurface.IN_CONTEXT)
                .position(IN_CONTEXT_UNIT_ORDINAL)
                .deliveryState(FeedbackDeliveryState.DELIVERED)
                .body(delivery.mrNote())
                .source(FeedbackProvenance.AGENT)
                .threadKey(feedbackThreadKey)
                .replacesId(supersedesId)
                .createdAt(now)
                .deliveredAt(now)
                .build()
        );

        // Flip the prior unit AFTER the new DELIVERED row lands, so there is never a window with zero live
        // units on this line (a concurrent reader always sees exactly one). Native update — @Immutable forbids
        // an ORM-level state mutation.
        if (supersedesId != null) {
            feedbackRepository.updateState(supersedesId, FeedbackDeliveryState.SUPERSEDED.name());
        }

        // Findings already withheld earlier in the flow as SUPPRESSED (B2 reaction suppression writes its
        // REACTED_* units before this runs). Computed first so neither the DELIVERED binding NOR the policy
        // floor re-binds them — B2 does NOT delete the Observation row, so a disputed-yet-recurring locus
        // would otherwise land in the policy-dropped tail and get a SECOND (POLICY_FLOOR_DROP) SUPPRESSED unit.
        Set<UUID> alreadySuppressed = new HashSet<>(
            feedbackFindingRepository.findFindingIdsSuppressedForJob(job.getId())
        );

        // The policy floor (C3) caps the volume surfaced this run; the dropped tail is NOT part of the DELIVERED
        // unit — it is recorded as SUPPRESSED below. Exclude anything already suppressed so it is dropped once.
        List<Observation> policyDropped = reviewProperties.policyFloor()
            ? PolicyFloorSelector.partition(
                  findings
                      .stream()
                      .filter(f -> f.getAssessment() == Assessment.BAD && !alreadySuppressed.contains(f.getId()))
                      .toList(),
                  DeliveryComposer.MAX_IMPROVEMENT_SUGGESTIONS
              ).dropped()
            : List.of();
        // The DELIVERED unit binds nothing that was withheld: policy-floor-dropped this run + already-suppressed.
        Set<UUID> excludedIds = policyDropped
            .stream()
            .map(Observation::getId)
            .collect(Collectors.toCollection(HashSet::new));
        excludedIds.addAll(alreadySuppressed);

        // Bind every DELIVERED finding: BAD (the problems surfaced) lead as PRIMARY, GOOD
        // strengths as SUPPORTING; NOT_APPLICABLE abstentions and withheld findings are excluded.
        // Severity is null for a GOOD strength (ADR 0022) — sort it after any problem (least severe).
        List<Observation> assessed = findings
            .stream()
            .filter(f -> f.getPresence() != Presence.NOT_APPLICABLE)
            .filter(f -> !excludedIds.contains(f.getId()))
            .sorted(Comparator.comparingInt(FeedbackLedgerRecorder::severityOrdinal))
            .toList();
        int ordinal = 0;
        for (Observation f : assessed) {
            EvidenceRole role = f.getAssessment() == Assessment.BAD ? EvidenceRole.PRIMARY : EvidenceRole.SUPPORTING;
            feedbackFindingRepository.insertIfAbsent(feedback.getId(), f.getId(), role.name(), ordinal++);
        }

        // SUMMARY placement — fully recoverable: external_ref is the posted summary comment id.
        feedbackPlacementRepository.save(
            FeedbackPlacement.builder()
                .feedback(feedback)
                .placementType(PlacementSlot.SUMMARY)
                .postedCommentRef(job.getDeliveryCommentId())
                .createdAt(now)
                .build()
        );

        // INLINE placements (PR only) — the ANCHOR is always recoverable; the durable vendor handle
        // (external_ref) comes from the per-note DeliveredSignal the channel emitted this run. A note with no
        // matching signal (append-only GitHub, or a channel that emitted none) keeps the anchor-only fallback:
        // a null external_ref.
        if (artifact == WorkArtifact.PULL_REQUEST) {
            for (DiffNote note : delivery.diffNotes()) {
                DeliveredSignal signal = matchSignal(note, inlineSignals);
                feedbackPlacementRepository.save(
                    FeedbackPlacement.builder()
                        .feedback(feedback)
                        .placementType(PlacementSlot.INLINE)
                        .anchorKind(note.endLine() != null ? PlacementAnchorKind.RANGE : PlacementAnchorKind.LINE)
                        .anchorPath(note.filePath())
                        .anchorStartLine(note.startLine())
                        .anchorEndLine(note.endLine())
                        .anchorSide(PlacementAnchorSide.NEW)
                        .postedCommentRef(signal != null ? signal.externalRef() : null)
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
            "Feedback ledger recorded: jobId={}, unit={}, findings={}, inlinePlacements={}, feedbackThreadKey={}",
            job.getId(),
            feedback.getId(),
            assessed.size(),
            artifact == WorkArtifact.PULL_REQUEST ? delivery.diffNotes().size() : 0,
            feedbackThreadKey
        );
    }

    /**
     * Record the volume-cap tail (C3): each policy-DROPPED problem is written as a SUPPRESSED /
     * POLICY_FLOOR_DROP unit so an eval can exclude it rather than score a model-correct-but-policy-withheld
     * finding as a miss.
     */
    private void recordPolicyFloor(AgentJob job, List<Observation> dropped) {
        Instant now = Instant.now();
        int index = 0;
        for (Observation droppedFinding : dropped) {
            int unitOrdinal = POLICY_FLOOR_UNIT_ORDINAL_BASE + index++;
            if (feedbackRepository.existsByAgentJobIdAndPosition(job.getId(), unitOrdinal)) {
                continue;
            }
            Feedback unit = feedbackRepository.save(
                Feedback.builder()
                    .agentJobId(job.getId())
                    .workspaceId(job.getWorkspace().getId())
                    .artifactType(droppedFinding.getArtifactType())
                    .artifactId(droppedFinding.getArtifactId())
                    .recipientUserId(droppedFinding.getAboutUserId())
                    .subjectUserId(droppedFinding.getAboutUserId())
                    .channel(FeedbackSurface.IN_CONTEXT)
                    .position(unitOrdinal)
                    .deliveryState(FeedbackDeliveryState.SUPPRESSED)
                    .suppressionReason(FeedbackSuppressionReason.POLICY_FLOOR_DROP)
                    .source(FeedbackProvenance.AGENT)
                    .createdAt(now)
                    .build()
            );
            feedbackFindingRepository.insertIfAbsent(
                unit.getId(),
                droppedFinding.getId(),
                EvidenceRole.PRIMARY.name(),
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
        List<Observation> findings = practiceFindingRepository.findByAgentJobId(job.getId());
        if (findings.isEmpty()) {
            return Optional.empty();
        }
        String feedbackThreadKey = feedbackThreadKeyFor(findings.get(0));
        return feedbackRepository
            .findFirstByThreadKeyAndDeliveryStateOrderByCreatedAtDesc(
                feedbackThreadKey,
                FeedbackDeliveryState.DELIVERED
            )
            .flatMap(prior ->
                feedbackPlacementRepository
                    .findByFeedbackId(prior.getId())
                    .stream()
                    .filter(p -> p.getPlacementType() == PlacementSlot.SUMMARY)
                    .map(FeedbackPlacement::getPostedCommentRef)
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
    public void recordSuppressed(AgentJob job, Observation finding, FeedbackSuppressionReason reason, int index) {
        int unitOrdinal = SUPPRESSED_UNIT_ORDINAL_BASE + index;
        if (feedbackRepository.existsByAgentJobIdAndPosition(job.getId(), unitOrdinal)) {
            return; // already recorded (job retry)
        }
        Instant now = Instant.now();
        Feedback feedback = feedbackRepository.save(
            Feedback.builder()
                .agentJobId(job.getId())
                .workspaceId(job.getWorkspace().getId())
                .artifactType(finding.getArtifactType())
                .artifactId(finding.getArtifactId())
                .recipientUserId(finding.getAboutUserId())
                .subjectUserId(finding.getAboutUserId())
                .channel(FeedbackSurface.IN_CONTEXT)
                .position(unitOrdinal)
                .deliveryState(FeedbackDeliveryState.SUPPRESSED)
                .suppressionReason(reason)
                .source(FeedbackProvenance.AGENT)
                .threadKey(feedbackThreadKeyFor(finding))
                .createdAt(now)
                .build()
        );
        feedbackFindingRepository.insertIfAbsent(feedback.getId(), finding.getId(), EvidenceRole.PRIMARY.name(), 0);
        log.info(
            "Feedback suppressed (reaction-aware): jobId={}, unit={}, reason={}, findingFingerprint={}",
            job.getId(),
            feedback.getId(),
            reason,
            finding.getRecurrenceKey()
        );
    }

    /**
     * Find the delivery signal for a posted note. Primary match is the stable {@code findingFingerprint} (the
     * cross-run identity); when it is absent on either side (legacy / unkeyed notes) we fall back to the diff
     * coordinates the signal anchored at — path + the note's terminal line, which for a single-line note is its
     * start and for a range its end. Returns {@code null} when nothing matches (no signal was emitted).
     */
    private static @Nullable DeliveredSignal matchSignal(DiffNote note, List<DeliveredSignal> signals) {
        if (signals.isEmpty()) {
            return null;
        }
        if (note.findingFingerprint() != null) {
            for (DeliveredSignal s : signals) {
                if (note.findingFingerprint().equals(s.findingFingerprint())) {
                    return s;
                }
            }
        }
        int terminalLine = note.endLine() != null ? note.endLine() : note.startLine();
        for (DeliveredSignal s : signals) {
            if (
                s.anchor() instanceof DiffAnchor anchor &&
                note.filePath().equals(anchor.filePath()) &&
                anchor.newLineNumber() == terminalLine
            ) {
                return s;
            }
        }
        return null;
    }

    /** The stable continuity line for a finding: (target, recipient, in-context surface). */
    private static String feedbackThreadKeyFor(Observation any) {
        return FeedbackThreadKey.compute(
            any.getArtifactType().name(),
            any.getArtifactId(),
            any.getAboutUserId(),
            FeedbackSurface.IN_CONTEXT
        );
    }

    /**
     * Severity ordinal for sorting, treating a null severity (a GOOD strength under ADR 0022) as the
     * least severe so problems always sort ahead of strengths.
     */
    private static int severityOrdinal(Observation f) {
        return f.getSeverity() == null ? Integer.MAX_VALUE : f.getSeverity().ordinal();
    }
}
