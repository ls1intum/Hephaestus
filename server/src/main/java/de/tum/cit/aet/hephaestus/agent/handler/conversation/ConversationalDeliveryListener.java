package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drives the conversational-feedback router + preparer off {@link PracticeDetectionDeliveredEvent}. Runs
 * {@code @Async @TransactionalEventListener(AFTER_COMMIT)} so it never blocks the delivery path and reads the
 * cycle's observations only after they are committed, in its own {@code REQUIRES_NEW} transaction. Best-effort: any
 * failure is logged, never propagated. Reviewer attribution is not built (ADR-0021-C2), so every pass is driven with
 * {@link RoutingContext#author()}.
 */
@Component
public class ConversationalDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(ConversationalDeliveryListener.class);

    private final ObservationRepository observationRepository;
    private final FeedbackChannelRouter router;
    private final ConversationalFeedbackPreparer preparer;

    public ConversationalDeliveryListener(
        ObservationRepository observationRepository,
        FeedbackChannelRouter router,
        ConversationalFeedbackPreparer preparer
    ) {
        this.observationRepository = observationRepository;
        this.router = router;
        this.preparer = preparer;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPracticeDetectionDelivered(PracticeDetectionDeliveredEvent event) {
        try {
            List<Observation> observations = observationRepository.findByAgentJobId(event.agentJobId());
            if (observations.isEmpty()) {
                return;
            }
            List<Observation> admitted = router.admit(observations, event.workspaceId(), RoutingContext.author());
            preparer.prepare(event.agentJobId(), event.workspaceId(), admitted);
        } catch (RuntimeException e) {
            log.warn(
                "Conversational routing/prepare failed (delivery unaffected): jobId={}, error={}",
                event.agentJobId(),
                e.toString()
            );
        }
    }
}
