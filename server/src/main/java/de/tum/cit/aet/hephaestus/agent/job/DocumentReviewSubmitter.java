package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection;
import de.tum.cit.aet.hephaestus.agent.documentation.DocumentReviewTrigger;
import de.tum.cit.aet.hephaestus.agent.handler.DocumentReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignal;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.PendingSignalResubmitter;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns a recorded {@code docs.document} signal into a review, and settles the ledger row either way.
 *
 * <p>The live path ({@link DocumentReviewTrigger}) and the reaper path ({@link PendingSignalResubmitter})
 * share this class so a resubmitted signal reaches the same decision as the first attempt.
 *
 * <p>The observation is attributed to the document's author ({@code DocumentArtifactDescriptor}'s sole
 * {@code AUTHOR} role), never to whoever last edited it — even on an update signal.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
public class DocumentReviewSubmitter implements DocumentReviewTrigger, PendingSignalResubmitter {

    private static final Logger log = LoggerFactory.getLogger(DocumentReviewSubmitter.class);

    private final AgentJobService agentJobService;
    private final DocumentProjection documentProjection;
    private final WorkspaceRepository workspaceRepository;
    private final PracticeReviewDetectionGate practiceReviewDetectionGate;
    private final SignalRecorder signalRecorder;
    private final TransactionTemplate transactionTemplate;

    public DocumentReviewSubmitter(
        AgentJobService agentJobService,
        DocumentProjection documentProjection,
        WorkspaceRepository workspaceRepository,
        PracticeReviewDetectionGate practiceReviewDetectionGate,
        SignalRecorder signalRecorder,
        TransactionTemplate transactionTemplate
    ) {
        this.agentJobService = agentJobService;
        this.documentProjection = documentProjection;
        this.workspaceRepository = workspaceRepository;
        this.practiceReviewDetectionGate = practiceReviewDetectionGate;
        this.signalRecorder = signalRecorder;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public ArtifactKind artifactKind() {
        return ArtifactKinds.DOCUMENT;
    }

    @Override
    public void onDocumentSignal(SignalKey key, DiscoveredVia discoveredVia) {
        submit(key, discoveredVia);
    }

    @Override
    public void resubmit(ArtifactSignal signal) {
        // Reuse the ledger row's own discovery mode; deriving it from the retry would file a campaign's
        // tail as LIVE.
        submit(signal.key(), signal.getDiscoveredVia());
    }

    /**
     * Deliberately not transactional: {@link AgentJobService#submit} opens its own transaction and takes a
     * pessimistic lock on the workspace, which must not be widened to a caller's unit of work. Every
     * refusal below therefore goes through {@link #refuse} rather than the recorder directly, since
     * {@code markRefused} is {@code Propagation.MANDATORY} and neither entry point holds a transaction.
     */
    private void submit(SignalKey key, DiscoveredVia discoveredVia) {
        Workspace workspace = workspaceRepository.findById(key.workspaceId()).orElse(null);
        if (workspace == null) {
            // FK ties the ledger row to the workspace: null here means it was deleted after recording.
            refuse(key, SignalStateReason.ARTIFACT_GONE);
            return;
        }

        DocumentProjection.ProjectedDocument document = documentProjection
            .documentById(key.workspaceId(), key.artifactId())
            .filter(found -> !found.deleted())
            .orElse(null);
        if (document == null) {
            log.debug("Document signal has no reviewable document left: documentId={}", key.artifactId());
            refuse(key, SignalStateReason.ARTIFACT_GONE);
            return;
        }

        Long aboutUserId = document.createdByMemberId();
        if (aboutUserId == null || aboutUserId <= 0) {
            // Retryable on purpose: the author linking their account later makes every passed-over
            // document of theirs reviewable, with no upstream event to re-announce it.
            log.debug(
                "Document signal has no linked author to attribute to: documentId={}, subject={}",
                key.artifactId(),
                document.createdBySubject()
            );
            refuse(key, SignalStateReason.SUBJECT_UNLINKED);
            return;
        }

        switch (practiceReviewDetectionGate.evaluateSignal(workspace, key.signalName(), TriggerMode.AUTO)) {
            case GateDecision.Skip skip -> {
                log.debug(
                    "Document signal skipped by practice gate: documentId={}, reason={}",
                    key.artifactId(),
                    skip.reason()
                );
                refuse(key, skip.resolvedSignalReason());
            }
            case GateDecision.Detect detect -> agentJobService.submit(
                detect.workspace().getId(),
                AgentJobType.DOCUMENT_REVIEW,
                new DocumentReviewSubmissionRequest(
                    key.artifactId(),
                    document.title(),
                    document.collectionName() != null ? document.collectionName() : document.collectionSlug(),
                    aboutUserId,
                    key.signalName(),
                    key.revision(),
                    SignalOrigins.observationOriginOf(discoveredVia)
                ),
                key
            );
        }
    }

    /**
     * Not annotated {@code @Transactional}: this method is private, so self-invocation would bypass the
     * proxy and {@code markRefused} would still throw.
     */
    private void refuse(SignalKey key, SignalStateReason reason) {
        transactionTemplate.executeWithoutResult(status -> signalRecorder.markRefused(key, reason));
    }
}
