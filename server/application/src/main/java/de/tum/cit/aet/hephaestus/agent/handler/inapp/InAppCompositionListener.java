package de.tum.cit.aet.hephaestus.agent.handler.inapp;

import de.tum.cit.aet.hephaestus.agent.handler.FeedbackLedgerRecorder;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeFeedbackDeliveryPolicy;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedbackUnit;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionResultParser;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.PracticeFeedbackPreparationRequestedEvent;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.config.FeedbackLaneExecutor;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicySurface;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.PreviousInAppFeedback;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;

/**
 * Prepares composed IN_APP feedback and resolves its eligible supporting results.
 *
 * <p>The asynchronous listener is the fast path. {@code FeedbackLanePreparationSweeper} retries
 * unmarked jobs within its recovery window, including jobs whose listener submission was rejected.
 */
@Component
public class InAppCompositionListener {

    private static final Logger log = LoggerFactory.getLogger(InAppCompositionListener.class);

    private final AgentJobRepository agentJobRepository;
    private final PracticeResultRepository practiceResultRepository;
    private final FeedbackCompositionResultParser resultParser;
    private final InAppFeedbackPreparer preparer;
    private final PracticeFeedbackDeliveryPolicy deliveryPolicy;
    private final @Nullable InAppSupportReader supportReader;
    private final Clock clock;

    public InAppCompositionListener(
            AgentJobRepository agentJobRepository,
            PracticeResultRepository practiceResultRepository,
            ObjectProvider<InAppSupportReader> supportReaders,
            FeedbackCompositionResultParser resultParser,
            InAppFeedbackPreparer preparer,
            PracticeFeedbackDeliveryPolicy deliveryPolicy,
            Clock clock) {
        this.agentJobRepository = agentJobRepository;
        this.practiceResultRepository = practiceResultRepository;
        this.resultParser = resultParser;
        this.preparer = preparer;
        this.deliveryPolicy = deliveryPolicy;
        this.supportReader = supportReaders.getIfAvailable();
        this.clock = clock;
    }

    @Async(FeedbackLaneExecutor.BEAN_NAME)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPracticeFeedbackPreparationRequested(PracticeFeedbackPreparationRequestedEvent event) {
        try {
            prepare(event.agentJobId(), event.agentJobId(), event.workspaceId());
        } catch (RuntimeException e) {
            log.warn(
                    "In-app composition routing failed (delivery unaffected): jobId={}, error={}",
                    event.agentJobId(),
                    e.toString());
        }
    }

    /**
     * Records completion even when nothing is prepared. Exceptions propagate so recovery can retry
     * jobs whose completion mark was not committed.
     *
     * @return newly prepared units
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int prepare(UUID agentJobId, Long workspaceId) {
        return prepare(agentJobId, agentJobId, workspaceId);
    }

    /** Prepare source results using a separate composition job's output. */
    public int prepare(UUID sourceJobId, UUID compositionJobId, Long workspaceId) {
        int prepared = route(sourceJobId, compositionJobId, workspaceId);
        agentJobRepository.markInAppPrepared(compositionJobId, clock.instant());
        return prepared;
    }

    private int route(UUID agentJobId, UUID outputJobId, Long workspaceId) {
        AgentJob sourceJob = agentJobRepository.findById(agentJobId).orElse(null);
        if (sourceJob == null || !deliveryPolicy.allowsComposition(sourceJob, DeliveryPolicySurface.IN_APP)) {
            return 0;
        }
        AgentJob job = agentJobRepository.findById(outputJobId).orElse(null);
        if (job == null) {
            return 0;
        }
        List<ComposedInAppMessage> messages = inAppMessages(job.getOutput());
        if (messages.isEmpty()) {
            return 0;
        }
        List<Long> recipients = practiceResultRepository.findSubjectUserIdsByAgentJobId(agentJobId, workspaceId);
        // Stable recipient ordering preserves unique (agent_job_id, position) slots across retries.
        int positionBase = FeedbackLedgerRecorder.IN_APP_UNIT_ORDINAL_BASE;
        int prepared = 0;
        for (Long recipient : recipients) {
            if (recipient == null) {
                continue;
            }
            // The pulled page's practice authority belongs to InAppFeedbackRouter, per message.
            if (deliveryPolicy
                    .evaluateForRecipient(
                            sourceJob,
                            DeliveryPolicyStage.COMPOSITION,
                            null,
                            DeliveryPolicySurface.IN_APP,
                            recipient,
                            List.of())
                    .allowed()) {
                prepared += prepareFor(outputJobId, workspaceId, recipient, messages, positionBase);
            }
            positionBase += messages.size();
        }
        return prepared;
    }

    private List<ComposedInAppMessage> inAppMessages(@Nullable JsonNode jobOutput) {
        return resultParser.parse(jobOutput, FeedbackChannel.IN_APP).stream()
                .filter(unit -> unit.action() != ComposedFeedbackUnit.Action.WITHHOLD)
                .filter(ComposedFeedbackUnit::isComplete)
                // The composer's supersession target is not carried: which card a new one replaces is the
                // server's rule, read off the page's own view of the previous card in prepareFor.
                .map(unit -> new ComposedInAppMessage(
                        unit.practiceSlug(),
                        Objects.requireNonNull(unit.title()),
                        Objects.requireNonNull(unit.body()),
                        Objects.requireNonNull(unit.nextStep())))
                .toList();
    }

    private int prepareFor(
            UUID agentJobId,
            Long workspaceId,
            Long recipientUserId,
            List<ComposedInAppMessage> messages,
            int positionBase) {
        var reader = supportReader;
        if (reader == null) return 0;
        Instant now = clock.instant();
        List<InAppFeedbackPreparer.RoutedMessage> routed = new ArrayList<>(messages.size());
        for (ComposedInAppMessage message : messages) {
            // An open previous card is what the new one replaces.
            InAppSupportReader.Selection selection =
                    reader.select(workspaceId, recipientUserId, message.practiceSlug(), now);
            Optional<PreviousInAppFeedback.Previous> previous = selection.previous();
            List<PracticeResult> evidence = selection.evidence();
            InAppSupportReader.RoutingFacts facts =
                    reader.routingFacts(workspaceId, recipientUserId, message.practiceSlug(), evidence);
            InAppRoutingDecision decision = InAppFeedbackRouter.route(
                    message, evidence, facts.autonomy(), facts.subjectRole(), facts.lastSurfaced(), now);
            if (decision != InAppRoutingDecision.ADMIT) {
                log.debug(
                        "In-app message withheld: reason={}, practice={}, jobId={}",
                        decision,
                        message.practiceSlug(),
                        agentJobId);
            }
            // The card presents bound results as examples of the problem, not the full review window.
            routed.add(new InAppFeedbackPreparer.RoutedMessage(
                    message,
                    decision,
                    selection.supports(),
                    previous.filter(PreviousInAppFeedback.Previous::isOpen)
                            .map(PreviousInAppFeedback.Previous::id)
                            .orElse(null)));
        }
        return preparer.prepare(agentJobId, workspaceId, recipientUserId, List.copyOf(routed), positionBase);
    }
}
