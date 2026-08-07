package de.tum.cit.aet.hephaestus.agent.handler.spi;

import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;

/**
 * Marker interface for type-safe handler dispatch.
 *
 * <p>Each {@link JobTypeHandler} accepts a specific implementation of this interface in
 * {@link JobTypeHandler#createSubmission}. Handlers validate the concrete type at runtime
 * via {@code instanceof} and throw {@link IllegalArgumentException} on mismatch.
 *
 * <p>Not sealed because sealing would require this SPI type to reference implementation
 * classes via {@code permits}, creating a compile-time dependency from the SPI package
 * to handler implementations — violating the SPI isolation enforced by ArchUnit tests.
 */
public interface JobSubmissionRequest {
    /**
     * What occasioned this run, and therefore which population its observations belong to.
     *
     * <p>Declared on the submission rather than inferred downstream because only the submitter knows.
     * The delivery path sees a job and its metadata, where a scheduled sweep over live conversations and
     * a backfill sweep over year-old ones look identical; getting that wrong would silently merge two
     * incomparable samples into one trend.
     *
     * <p>Defaults to {@link ObservationOrigin#LIVE}: a request type that has not thought about this is
     * event-driven, which is what every one of them was when the axis was introduced.
     */
    default ObservationOrigin observationOrigin() {
        return ObservationOrigin.LIVE;
    }
}
