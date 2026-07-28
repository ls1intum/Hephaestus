package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.core.event.ScmMirrorErasedEvent;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases the SCM-derived half of the practices ledger when a workspace's SCM mirror is erased on
 * connection-disconnect or workspace-purge — the {@code PULL_REQUEST}/{@code ISSUE} counterpart of
 * the Slack {@code ConversationFeedbackErasure} path.
 *
 * <p>These rows are not reachable from the repository cascade: {@code observation.artifact_id} and
 * {@code feedback.artifact_id} are soft references with no FK to {@code issue}/{@code pull_request},
 * and the {@code evidence} jsonb quotes mirrored diff and comment text verbatim. Left alone they
 * would survive the mirror's deletion as dangling, still-queryable copies of third-party content.
 *
 * <p><b>Why an event and not a port.</b> {@code practices} already depends on {@code workspace}
 * ({@code Practice.workspace}, {@code WorkspacePurgeContributor}), so the inbound-port shape used
 * for Slack ({@code integration.slack → practices.spi}) would close a Spring Modulith cycle here.
 * The publisher instead emits {@link ScmMirrorErasedEvent} and this listener runs synchronously
 * inside the erasing transaction, before the artifacts are dropped.
 *
 * <p>Idempotent, so it composes with {@code PracticesWorkspacePurgeAdapter}, which clears ALL
 * practice rows on purge: whichever runs first, the other deletes 0 rows.
 */
@Component
public class PracticesScmMirrorErasureListener {

    private static final Logger log = LoggerFactory.getLogger(PracticesScmMirrorErasureListener.class);

    private final FeedbackRepository feedbackRepository;
    private final ObservationRepository observationRepository;

    public PracticesScmMirrorErasureListener(
        FeedbackRepository feedbackRepository,
        ObservationRepository observationRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.observationRepository = observationRepository;
    }

    @EventListener
    @Transactional
    public void onScmMirrorErased(ScmMirrorErasedEvent event) {
        long workspaceId = event.workspaceId();
        // Feedback first: its DB ON DELETE CASCADE clears feedback_observation / _placement / _reaction.
        int feedbackDeleted = feedbackRepository.deleteAllScmArtifactFeedback(workspaceId);
        int observationsDeleted = observationRepository.deleteAllScmArtifactObservations(workspaceId);
        if (feedbackDeleted > 0 || observationsDeleted > 0) {
            log.info(
                "Erased SCM-derived practice rows: workspaceId={}, feedback={}, observations={}",
                workspaceId,
                feedbackDeleted,
                observationsDeleted
            );
        }
    }
}
