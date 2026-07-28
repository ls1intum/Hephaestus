package de.tum.cit.aet.hephaestus.agent.handler;

/**
 * The two persisted identities of one {@link de.tum.cit.aet.hephaestus.practices.model.Observation}, stamped
 * onto a finding by the handler so downstream stages address the stored row without recomputing either key.
 *
 * @param occurrenceKey this observation alone ({@code observation.occurrence_key}, uniquely constrained) —
 *     the key to use whenever a single observation must be addressed
 * @param recurrenceKey the locus this observation shares with re-detections of the same concern across runs
 *     ({@link de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint}). Deliberately
 *     many-to-one: several observations of one practice in one file collapse to it, so it can never stand in
 *     for {@code occurrenceKey}.
 */
public record ObservationKeys(String occurrenceKey, String recurrenceKey) {}
