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
 * Writes a document's lifecycle into the signal ledger.
 *
 * <p>Recording is unconditional and separate from triggering: the row says that a document was
 * published, and whether a review starts is a later, policy question. That separation is the whole
 * reason the ledger exists — it makes <em>"why did nothing happen?"</em> answerable, rather than leaving
 * a publish that nobody reviewed indistinguishable from a publish that never arrived.
 *
 * <p>Only three of the eleven ingested Outline events map to a signal; the rest return empty from
 * {@link DocsSignals#forOutlineEvent} and are silently not recorded, which is the honest answer for a
 * document that merely moved.
 */
@Component
public class OutlineDocumentSignalRecorder {

    private static final Logger log = LoggerFactory.getLogger(OutlineDocumentSignalRecorder.class);

    private final SignalRecorder recorder;

    public OutlineDocumentSignalRecorder(SignalRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * Records the signal an Outline event raises about a mirrored document, if any.
     *
     * <p>Runs in its own transaction because the sync path that calls it is deliberately not one — it
     * makes HTTP calls to Outline — and the recorder demands an existing transaction so that no caller
     * can write a ledger row outside the unique constraint that makes it a dedup key.
     *
     * @param document the mirror row as it stands <em>after</em> the refresh, so a content-shaped signal
     *                 is keyed on the content the review would actually read
     * @return whether this occurrence was new; false means the same revision was already recorded, which
     *         is what makes a redelivered webhook inert
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean record(
        long workspaceId,
        @Nullable OutlineDocumentSnapshot document,
        @Nullable String outlineEventName,
        Instant occurredAt,
        DiscoveredVia discoveredVia
    ) {
        if (document == null || document.id() == null || document.isDeleted()) {
            return false;
        }
        Optional<SignalName> signal = DocsSignals.forOutlineEvent(outlineEventName);
        if (signal.isEmpty()) {
            return false;
        }
        Optional<SignalKey> key = DocsSignals.documentKey(
            workspaceId,
            document.id(),
            signal.get(),
            document.contentHash(),
            document.title()
        );
        if (key.isEmpty()) {
            // No stable identity — an evicted body has no hash to key a content-shaped signal on. Skipping
            // is right: a made-up revision would either dedup away every future occurrence or none of them.
            log.debug(
                "Outline document signal has no revision, not recorded: documentId={}, signal={}",
                document.id(),
                signal.get()
            );
            return false;
        }
        boolean recorded = recorder.record(key.get(), occurredAt, discoveredVia);
        if (recorded) {
            log.debug(
                "Recorded document signal: workspaceId={}, documentId={}, signal={}, via={}",
                workspaceId,
                document.id(),
                signal.get(),
                discoveredVia
            );
        }
        return recorded;
    }
}
