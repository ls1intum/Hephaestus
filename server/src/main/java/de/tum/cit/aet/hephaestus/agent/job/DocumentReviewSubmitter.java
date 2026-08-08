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
 * <p>One class serves both paths deliberately. The live path ({@link DocumentReviewTrigger}) runs when
 * the sync writes a new ledger row; the reaper path ({@link PendingSignalResubmitter}) re-offers a row
 * that was refused for something an operator can undo. Splitting them would be two places to keep the
 * refusal vocabulary honest, and the reaper's whole purpose is that the second attempt reaches exactly
 * the same decision as the first.
 *
 * <p>The observation is attributed to the document's author, per {@code DocumentArtifactDescriptor}'s
 * sole {@code AUTHOR} role, never to whoever last edited it. The consequence is accepted rather than
 * hidden: on an update signal the author is measured on a document somebody else may have changed.
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
        // The ledger row's discovery mode is the only thing that still remembers which population this
        // review was meant to measure; deriving it from the retry would file a campaign's tail as LIVE.
        submit(signal.key(), signal.getDiscoveredVia());
    }

    /**
     * Deliberately NOT transactional: {@link AgentJobService#submit} opens its own and takes a pessimistic
     * lock on the workspace inside it, which must not be widened to a caller's unit of work.
     *
     * <p>Which is why every refusal below goes through {@link #refuse} rather than calling the recorder
     * directly: {@code markRefused} is {@code Propagation.MANDATORY}, and neither entry point holds a
     * transaction. A direct call throws, the caller swallows it, and the row stays {@code RECORDED}
     * forever with no reason on it.
     */
    private void submit(SignalKey key, DiscoveredVia discoveredVia) {
        Workspace workspace = workspaceRepository.findById(key.workspaceId()).orElse(null);
        if (workspace == null) {
            // The ledger has a foreign key to the workspace, so this is a workspace deleted between
            // recording and here — nothing left to review, and nothing an operator can undo.
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
     * Settle the ledger row in a transaction of this class's own.
     *
     * <p>Not an annotation on this method: it is private, so self-invocation would bypass the proxy and
     * the {@code MANDATORY} recorder would still throw, with the annotation claiming otherwise.
     */
    private void refuse(SignalKey key, SignalStateReason reason) {
        transactionTemplate.executeWithoutResult(status -> signalRecorder.markRefused(key, reason));
    }
}
