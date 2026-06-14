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
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSurface;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementAnchorKind;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementAnchorSide;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementPostedState;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementSurface;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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

    /** One unit per job today; >0 reserved for future multi-recipient / reviewer-side fan-out. */
    private static final int IN_CONTEXT_UNIT_ORDINAL = 0;

    private final PracticeFindingRepository practiceFindingRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackFindingRepository feedbackFindingRepository;
    private final FeedbackPlacementRepository feedbackPlacementRepository;

    FeedbackLedgerRecorder(
        PracticeFindingRepository practiceFindingRepository,
        FeedbackRepository feedbackRepository,
        FeedbackFindingRepository feedbackFindingRepository,
        FeedbackPlacementRepository feedbackPlacementRepository
    ) {
        this.practiceFindingRepository = practiceFindingRepository;
        this.feedbackRepository = feedbackRepository;
        this.feedbackFindingRepository = feedbackFindingRepository;
        this.feedbackPlacementRepository = feedbackPlacementRepository;
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
        String continuityKey = FeedbackContinuityKey.compute(
            targetType.name(),
            targetId,
            recipientUserId,
            FeedbackSurface.IN_CONTEXT
        );

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
                .createdAt(now)
                .deliveredAt(now)
                .build()
        );

        // Bind every assessed finding: NOT_OBSERVED (the problems surfaced) lead as PRIMARY, OBSERVED
        // strengths as SUPPORTING; NOT_APPLICABLE abstentions are not part of what was delivered.
        List<PracticeFinding> assessed = findings
            .stream()
            .filter(f -> f.getVerdict() != Verdict.NOT_APPLICABLE)
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

        log.info(
            "Feedback ledger recorded: jobId={}, unit={}, findings={}, inlinePlacements={}, continuityKey={}",
            job.getId(),
            feedback.getId(),
            assessed.size(),
            artifact == WorkArtifact.PULL_REQUEST ? delivery.diffNotes().size() : 0,
            continuityKey
        );
    }
}
