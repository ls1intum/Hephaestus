package de.tum.cit.aet.hephaestus.agent.handler.spi;

import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;

/**
 * Marker interface for type-safe handler dispatch: each {@link JobTypeHandler} accepts a specific
 * implementation in {@link JobTypeHandler#createSubmission} and validates it via {@code instanceof}.
 *
 * <p>Not sealed: that would require this SPI package to reference implementation classes via
 * {@code permits}, which the SPI-isolation ArchUnit rule forbids.
 */
public interface JobSubmissionRequest {
    /**
     * What occasioned this run, and therefore which population its observations belong to. Declared on the
     * submission rather than inferred downstream, where a live sweep and a backfill sweep look identical and
     * conflating them would silently merge two incomparable samples into one trend.
     */
    default ObservationOrigin observationOrigin() {
        return ObservationOrigin.LIVE;
    }
}
