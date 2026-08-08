package de.tum.cit.aet.hephaestus.integration.outline.domain.signal;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRevision;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The signal vocabulary of the documentation domain, and the per-signal rule for what counts as a new
 * occurrence.
 *
 * <p>Names are vendor-neutral for the same reason the SCM ones are. Outline is the only vendor that
 * writes documents here today, but nothing in {@code docs.document.published} says so, and a second
 * documentation tool would raise the same signal against the same practices rather than arriving with
 * a vocabulary of its own.
 *
 * <p>Only some ingested Outline events carry review meaning; the rest move a document, rename a
 * collection or delete something, and a review occasioned by one of those could only read the same
 * bytes it read before. The mapping is stated once, in {@link #forOutlineEvent(String)}, because the
 * event names are also what the sync path already switches on.
 *
 * <p>Both content signals digest the document rather than keying on anything version-shaped: a document
 * has no commits, and the whole point of reviewing one is to read what it says. Archiving is terminal
 * — an archived document can be restored, but the archiving itself happened once.
 */
public final class DocsSignals {

    /** A written document — prose, structure and its revision history; no diff, no code. */
    public static final ArtifactKind DOCUMENT = ArtifactKind.of("docs.document");

    /**
     * A draft became visible to the team. The most informative moment a documentation practice has:
     * it is the first time the thing being judged exists as something anyone was meant to read.
     */
    public static final SignalName DOCUMENT_PUBLISHED = SignalName.of("docs.document.published");

    /** A published document's content changed — the occasion to re-measure what it now says. */
    public static final SignalName DOCUMENT_UPDATED = SignalName.of("docs.document.updated");

    /** A document was taken out of circulation. */
    public static final SignalName DOCUMENT_ARCHIVED = SignalName.of("docs.document.archived");

    /** The Outline webhook event names that raise each signal. */
    private static final Map<String, SignalName> BY_OUTLINE_EVENT = Map.of(
        "documents.publish",
        DOCUMENT_PUBLISHED,
        "documents.update",
        DOCUMENT_UPDATED,
        "documents.archive",
        DOCUMENT_ARCHIVED
    );

    private static final Map<SignalName, RevisionScheme> SCHEMES = Map.of(
        DOCUMENT_PUBLISHED,
        RevisionScheme.CONTENT_DIGEST,
        DOCUMENT_UPDATED,
        RevisionScheme.CONTENT_DIGEST,
        DOCUMENT_ARCHIVED,
        RevisionScheme.TERMINAL_STATE
    );

    private DocsSignals() {}

    /**
     * The signal an ingested Outline event raises, if it carries review meaning at all.
     *
     * <p>Empty is the common answer and not a gap: a move, a rename or a collection edit changes where
     * a document sits, not what it says, and occasioning a review on one would spend a model call to
     * re-read unchanged bytes.
     */
    public static Optional<SignalName> forOutlineEvent(@Nullable String outlineEventName) {
        return Optional.ofNullable(outlineEventName).map(BY_OUTLINE_EVENT::get);
    }

    public static RevisionScheme revisionScheme(SignalName signal) {
        RevisionScheme scheme = SCHEMES.get(signal);
        if (scheme == null) {
            throw new IllegalArgumentException("No revision scheme declared for signal: " + signal);
        }
        return scheme;
    }

    /**
     * The ledger identity of a document signal.
     *
     * <p>{@code documentId} is the mirror row's primary key, not Outline's UUID: the ledger keys on
     * local identity everywhere, and a reconnect that re-mirrors a document is destructive-and-rare
     * enough that keying on the provider's id would buy nothing for the cost of a second identity.
     *
     * @param contentHash the mirror's hash of the document body, which is what a content-shaped signal
     *                    is keyed on; empty when the body has been evicted and there is nothing stable
     *                    to key on, in which case no signal is recorded rather than a wrong one
     */
    public static Optional<SignalKey> documentKey(
        long workspaceId,
        long documentId,
        SignalName signal,
        @Nullable String contentHash,
        @Nullable String title
    ) {
        if (!DOCUMENT.equals(signal.artifactKind())) {
            return Optional.empty();
        }
        return revisionFor(signal, contentHash, title).map(revision ->
            new SignalKey(workspaceId, documentId, signal, revision)
        );
    }

    private static Optional<SignalRevision> revisionFor(
        SignalName signal,
        @Nullable String contentHash,
        @Nullable String title
    ) {
        return switch (revisionScheme(signal)) {
            case CONTENT_DIGEST -> contentHash == null || contentHash.isBlank()
                ? Optional.empty()
                : Optional.of(SignalRevision.ofContentDigest(title, contentHash));
            case TERMINAL_STATE -> Optional.of(SignalRevision.ofTerminalState(lastSegmentOf(signal)));
            // A document has no commits and nobody asks for a document review by hand yet; neither
            // scheme can produce an identity here, and inventing one would key every occurrence alike.
            case HEAD_COMMIT, RUN_ID -> Optional.empty();
        };
    }

    private static String lastSegmentOf(SignalName signal) {
        return signal.value().substring(signal.value().lastIndexOf('.') + 1);
    }
}
