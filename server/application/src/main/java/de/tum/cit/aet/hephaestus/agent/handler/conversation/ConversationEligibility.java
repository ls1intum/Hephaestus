package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How the conversation lane routes each admitted NOT_MET observation of a run at {@code readAt}, as
 * {@link FeedbackChannelRouter} decides it. It is read after the observations are admitted and is not part of the
 * admission: preparation routes again and decides.
 */
public record ConversationEligibility(State state, String readAt, List<ObservationRoute> observations) {

    public enum State {
        /** Every admitted NOT_MET observation was routed. */
        COMPLETE,
        /** The read failed or could not be attributed to one recipient; it says nothing about the lane. */
        UNAVAILABLE,
        /** The lane's composition permission does not allow conversational feedback for this run. */
        REFUSED,
    }

    public record ObservationRoute(UUID observationId, ConversationRoutingDecision decision) {}

    static ConversationEligibility complete(Instant readAt, Map<UUID, ConversationRoutingDecision> decisions) {
        return new ConversationEligibility(
                State.COMPLETE,
                readAt.toString(),
                decisions.entrySet().stream()
                        .map(entry -> new ObservationRoute(entry.getKey(), entry.getValue()))
                        .toList());
    }

    public static ConversationEligibility unavailable(Instant readAt) {
        return new ConversationEligibility(State.UNAVAILABLE, readAt.toString(), List.of());
    }

    static ConversationEligibility refused(Instant readAt) {
        return new ConversationEligibility(State.REFUSED, readAt.toString(), List.of());
    }
}
