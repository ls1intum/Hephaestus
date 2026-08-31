package de.tum.cit.aet.hephaestus.integration.outline.domain.signal;

import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentSnapshot;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a document's lifecycle into the signal ledger. Recording is unconditional and separate from
 * triggering: the row says a document was published, and whether a review starts is a later, policy
 * question — otherwise a publish nobody reviewed is indistinguishable from one that never arrived.
 */
@Component
public class OutlineDocumentSignalRecorder {

    private static final Logger log = LoggerFactory.getLogger(OutlineDocumentSignalRecorder.class);

    private final SignalRecorder recorder;

    public OutlineDocumentSignalRecorder(SignalRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * Records the signal an Outline event raises about a mirrored document, if any. Opens its own
     * transaction because the sync path that calls it deliberately isn't one — it makes HTTP calls to
     * Outline — while {@link SignalRecorder} requires an existing transaction.
     *
     * @param document the mirror row as it stands <em>after</em> the refresh, so a content-shaped signal
     *                 is keyed on the content the review would actually read
     * @return the ledger identity when the occurrence was new, empty when the same revision was already
     *         recorded (a redelivered webhook is inert); a key rather than a boolean because the caller's
     *         next move needs the identity to settle the very row this wrote
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SignalKey> record(
            long workspaceId,
            @Nullable OutlineDocumentSnapshot document,
            @Nullable String outlineEventName,
            Instant occurredAt,
            DiscoveredVia discoveredVia) {
        if (document == null || document.id() == null || document.isDeleted()) {
            return Optional.empty();
        }
        Optional<SignalName> signal = DocsSignals.forOutlineEvent(outlineEventName);
        if (signal.isEmpty()) {
            return Optional.empty();
        }
        Optional<SignalKey> key = DocsSignals.documentKey(
                workspaceId, document.id(), signal.get(), document.contentHash(), document.title());
        if (key.isEmpty()) {
            // An evicted body has no hash to key a content-shaped signal on, and a made-up one would break
            // dedup for every future occurrence.
            log.debug(
                    "Outline document signal has no revision, not recorded: documentId={}, signal={}",
                    document.id(),
                    signal.get());
            return Optional.empty();
        }
        boolean recorded = recorder.record(key.get(), occurredAt, discoveredVia);
        if (!recorded) {
            return Optional.empty();
        }
        log.debug(
                "Recorded document signal: workspaceId={}, documentId={}, signal={}, via={}",
                workspaceId,
                document.id(),
                signal.get(),
                discoveredVia);
        return key;
    }
}
