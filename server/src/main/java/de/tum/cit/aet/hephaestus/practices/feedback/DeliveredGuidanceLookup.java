package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.ObservationAdviceBody;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves observation → the feedback body that was actually delivered for it.
 *
 * <p>Advice lives on the delivered {@code Feedback}, not on the immutable {@code Observation} (ADR 0021), so
 * every read surface that shows a developer "what to do" has to make this hop. It lives here, in the module
 * that owns the ledger, rather than being duplicated by each consumer — the observation detail view and the
 * practice report both need it and must show the same body.
 */
@Service
@RequiredArgsConstructor
public class DeliveredGuidanceLookup {

    private final FeedbackObservationRepository feedbackObservationRepository;

    /** The delivered body for one observation, or empty when nothing was ever delivered for it. */
    @Transactional(readOnly = true)
    public Optional<String> forObservation(UUID observationId) {
        return Optional.ofNullable(forObservations(Set.of(observationId)).get(observationId));
    }

    /**
     * Batch-resolve observation id → delivered body in ONE query — the whole point of the batch form is that
     * a report card with twenty items costs one round trip, not twenty.
     *
     * <p>An observation can be bound to several DELIVERED units (re-deliveries); the most recent by feedback
     * {@code createdAt} wins, so the surface shows the latest advice the developer actually saw. Observations
     * with no delivered feedback are absent from the map — callers read absence as "nothing delivered".
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> forObservations(Set<UUID> observationIds) {
        if (observationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ObservationAdviceBody> latest = new HashMap<>();
        for (ObservationAdviceBody row : feedbackObservationRepository.findAdviceBodiesByObservationIds(
            observationIds
        )) {
            latest.merge(row.getObservationId(), row, (existing, candidate) ->
                candidate.getFeedbackCreatedAt().isAfter(existing.getFeedbackCreatedAt()) ? candidate : existing
            );
        }
        Map<UUID, String> result = new HashMap<>();
        latest.forEach((id, row) -> result.put(id, row.getBody()));
        return result;
    }
}
