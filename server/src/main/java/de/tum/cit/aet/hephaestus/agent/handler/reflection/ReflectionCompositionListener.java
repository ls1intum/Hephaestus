package de.tum.cit.aet.hephaestus.agent.handler.reflection;

import de.tum.cit.aet.hephaestus.agent.handler.FeedbackLedgerRecorder;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.PracticeDetectionDeliveredEvent;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a finished review's composed process-level messages into REFLECTION feedback units.
 *
 * <p>Best-effort: a failure here is logged and the feedback the developer already received is unaffected.
 *
 * <p>Writes none of the words. The composer names a practice; the server — not the model — resolves
 * which of that person's measurements stand behind it, because evidence a model asserts about itself is
 * not evidence.
 */
@Component
public class ReflectionCompositionListener {

    private static final Logger log = LoggerFactory.getLogger(ReflectionCompositionListener.class);

    /**
     * Ceiling on how many of a person's measurements of one practice are read to weigh a pattern. The
     * router only needs to count distinct artifacts and check provenance, so the whole window is never
     * required and an unbounded read would grow with how much work somebody did.
     */
    private static final int MAX_EVIDENCE_PER_PRACTICE = 50;

    private final AgentJobRepository agentJobRepository;
    private final ObservationRepository observationRepository;
    private final FeedbackRepository feedbackRepository;
    private final ObservationVisibilityPolicy visibilityPolicy;
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;
    private final ReflectionCompositionResultParser resultParser;
    private final ReflectionFeedbackPreparer preparer;

    public ReflectionCompositionListener(
        AgentJobRepository agentJobRepository,
        ObservationRepository observationRepository,
        FeedbackRepository feedbackRepository,
        ObservationVisibilityPolicy visibilityPolicy,
        WorkspaceReviewDefaultsProvider workspaceDefaults,
        ReflectionCompositionResultParser resultParser,
        ReflectionFeedbackPreparer preparer
    ) {
        this.agentJobRepository = agentJobRepository;
        this.observationRepository = observationRepository;
        this.feedbackRepository = feedbackRepository;
        this.visibilityPolicy = visibilityPolicy;
        this.workspaceDefaults = workspaceDefaults;
        this.resultParser = resultParser;
        this.preparer = preparer;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPracticeDetectionDelivered(PracticeDetectionDeliveredEvent event) {
        try {
            AgentJob job = agentJobRepository.findById(event.agentJobId()).orElse(null);
            if (job == null) {
                return;
            }
            List<ComposedReflectionMessage> messages = resultParser.parse(job.getOutput());
            if (messages.isEmpty()) {
                return;
            }
            // One recipient in practice — a review job files its observations against one person — but
            // read rather than assumed, so a kind that ever files against several does not silently
            // deliver all of their patterns to whoever happened to be first.
            List<Long> recipients = observationRepository.findSubjectUserIdsByAgentJobId(event.agentJobId());
            // Every recipient's units share this job's id, and (agent_job_id, position) is unique, so each
            // recipient gets its own slice of the band. The query orders by user id, so a re-run assigns
            // the same slices and the idempotency guard still recognises what it already wrote.
            int positionBase = FeedbackLedgerRecorder.REFLECTION_UNIT_ORDINAL_BASE;
            for (Long recipient : recipients) {
                if (recipient == null) {
                    continue;
                }
                prepareFor(event.agentJobId(), event.workspaceId(), recipient, messages, positionBase);
                positionBase += messages.size();
            }
        } catch (RuntimeException e) {
            log.warn(
                "Reflection composition routing failed (delivery unaffected): jobId={}, error={}",
                event.agentJobId(),
                e.toString()
            );
        }
    }

    private int prepareFor(
        UUID agentJobId,
        Long workspaceId,
        Long recipientUserId,
        List<ComposedReflectionMessage> messages,
        int positionBase
    ) {
        PracticeReviewTier workspaceDefault = workspaceDefaults.forWorkspace(workspaceId).defaultTier();
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(ReflectionFeedbackRouter.PATTERN_WINDOW_DAYS));
        List<ReflectionFeedbackPreparer.RoutedMessage> routed = new ArrayList<>(messages.size());
        for (ComposedReflectionMessage message : messages) {
            List<Observation> evidence = visibleEvidence(workspaceId, recipientUserId, message.practiceSlug(), since);
            ReflectionRoutingDecision decision = ReflectionFeedbackRouter.route(
                message,
                evidence,
                effectiveTier(evidence, workspaceDefault),
                subjectRole(evidence),
                feedbackRepository
                    .lastReflectionSurfacedAt(workspaceId, recipientUserId, message.practiceSlug())
                    .orElse(null),
                now
            );
            if (decision != ReflectionRoutingDecision.ADMIT) {
                log.debug(
                    "Reflection message withheld: reason={}, practice={}, jobId={}",
                    decision,
                    message.practiceSlug(),
                    agentJobId
                );
            }
            // Only the problems are bound, not the whole window the router read: the card renders these
            // rows as "the pieces of work this habit was observed on", so a piece of work where the
            // practice went WELL must never appear among them.
            routed.add(
                new ReflectionFeedbackPreparer.RoutedMessage(
                    message,
                    decision,
                    ReflectionFeedbackRouter.problemsIn(evidence)
                )
            );
        }
        return preparer.prepare(agentJobId, workspaceId, recipientUserId, List.copyOf(routed), positionBase);
    }

    /**
     * The recipient's own measurements of one practice, narrowed to what may be shown at all.
     *
     * <p>The visibility gate runs here and again at read: a claim measured under review rules the
     * practice has since replaced, or one whose evidence source lost its authorization, must stop being
     * cited. Composition freezes text; it must not freeze permission.
     */
    private List<Observation> visibleEvidence(
        Long workspaceId,
        Long recipientUserId,
        String practiceSlug,
        Instant since
    ) {
        List<Observation> candidates = observationRepository.findRecentForSubjectAndPractice(
            workspaceId,
            recipientUserId,
            practiceSlug,
            since,
            PageRequest.of(0, MAX_EVIDENCE_PER_PRACTICE)
        );
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<UUID> visible = visibilityPolicy.permitsAll(
            workspaceId,
            candidates,
            SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY
        );
        return candidates
            .stream()
            .filter(o -> visible.contains(o.getId()))
            .toList();
    }

    /**
     * The practice's effective tier, resolved through the practice → area → workspace chain from a
     * projection rather than by walking associations — the same reason {@code FeedbackChannelRouter}
     * projects it: the routing rule must not depend on whether the caller holds a session.
     */
    private @Nullable PracticeReviewTier effectiveTier(
        List<Observation> evidence,
        PracticeReviewTier workspaceDefault
    ) {
        List<UUID> ids = evidence.stream().map(Observation::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return null;
        }
        return observationRepository
            .practiceReviewTiersFor(ids)
            .stream()
            .findFirst()
            .map(row ->
                ReviewTierResolver.resolvePractice(row.getPracticeTier(), row.getAreaTier(), workspaceDefault).tier()
            )
            .orElse(null);
    }

    /**
     * Whose conduct the practice behind this evidence judges, read off its occasion. Asked with no
     * signal, so every occasion the practice declares is considered: the question here is whether this
     * practice can be about somebody other than the person we are about to show it to, not which run
     * produced it.
     */
    private ActorRole subjectRole(List<Observation> evidence) {
        return evidence
            .stream()
            .map(Observation::getPractice)
            .filter(Objects::nonNull)
            .findFirst()
            .map(practice -> PracticeBinding.subjectRoleOf(practice.getBindings(), null))
            .orElse(ActorRole.AUTHOR);
    }
}
