package de.tum.cit.aet.hephaestus.integration.sync;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.SyncMessage;

/**
 * Per-stream persistence + state-commit handler.
 *
 * <p>Records and state come back from {@link de.tum.cit.aet.hephaestus.integration.spi.SyncSource#read}
 * interleaved; this handler persists them atomically — entity upserts AND the
 * subsequent state commit run in one transaction so a mid-stream crash resumes
 * from the last fully-persisted batch.
 *
 * <p>One impl per stream class (per family-lib, per resource): the SCM family
 * provides handlers for {@code github.issues}, {@code github.pullrequests}, etc.
 */
public interface SyncMessageHandler {

    /** Stream name this handler accepts (matches {@code SyncMessage.stream()}). */
    String streamName();

    /** Persist a single record. Called inside an active transaction. */
    void handleRecord(IntegrationRef ref, SyncMessage.Record record);

    /** Optional hook on stream end — emit any reconciliation summaries / metrics. */
    default void onStreamComplete(IntegrationRef ref, SyncMessage.StreamComplete complete) {
    }
}
