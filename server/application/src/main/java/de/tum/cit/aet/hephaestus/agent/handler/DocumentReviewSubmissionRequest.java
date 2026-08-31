package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRevision;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Submission request for {@code DOCUMENT_REVIEW} jobs: one mirrored wiki document, and the member the
 * observations are filed against.
 *
 * <p>The subject is carried explicitly rather than resolved at delivery: a document's author lives behind
 * the linked-account chain, and a submission that cannot name one must be refused before a model call — an
 * unresolvable author is an operator-fixable fact, not a delivery failure.
 *
 * @param documentId the mirror row's primary key — the same identity the signal ledger recorded
 * @param title the document's title, for the run list; third-party text, never trusted as instruction
 * @param collectionName the collection the document sits in, when the mirror captured a name for it
 * @param signal the signal that occasioned this review, e.g. {@code docs.document.published}
 * @param revision the ledger revision of that occurrence — the disposable freshness segment of the
 *     idempotency key
 * @param observationOrigin the population these measurements belong to
 */
public record DocumentReviewSubmissionRequest(
        long documentId,
        String title,
        @Nullable String collectionName,
        long aboutUserId,
        SignalName signal,
        SignalRevision revision,
        ObservationOrigin observationOrigin)
        implements JobSubmissionRequest {
    public DocumentReviewSubmissionRequest {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(signal, "signal must not be null");
        Objects.requireNonNull(revision, "revision must not be null");
        Objects.requireNonNull(observationOrigin, "observationOrigin must not be null");
        collectionName = collectionName == null || collectionName.isBlank() ? null : collectionName;
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive, got " + documentId);
        }
        if (aboutUserId <= 0) {
            throw new IllegalArgumentException("aboutUserId must be positive, got " + aboutUserId);
        }
    }
}
