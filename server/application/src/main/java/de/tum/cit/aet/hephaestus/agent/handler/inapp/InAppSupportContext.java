package de.tum.cit.aet.hephaestus.agent.handler.inapp;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.result.ReviewedWorkKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Per current NOT_MET practice, how the page routes a new IN_APP card at {@code readAt} and, when it admits one, the
 * occurrences the card may cite, as the page selects them. It is read after the results are admitted and is not
 * part of the admission: a later read can differ, and the page decides again whether a card is written.
 *
 * @param refusal the recipient policy's reason, for {@link State#REFUSED}
 */
public record InAppSupportContext(
        State state, String readAt, @Nullable FeedbackSuppressionReason refusal, List<PracticeSupport> practices) {

    public enum State {
        /** Every current NOT_MET practice was read and routed. */
        COMPLETE,
        /** The read failed or could not be attributed to one recipient; it says nothing about the support. */
        UNAVAILABLE,
        /** The recipient policy does not allow an IN_APP card for this run. */
        REFUSED,
    }

    /** @param occurrences empty unless {@code decision} is {@link InAppRoutingDecision#ADMIT} */
    public record PracticeSupport(String practiceSlug, InAppRoutingDecision decision, List<Occurrence> occurrences) {}

    /** One selected result, with the native identity of the work it is about and what it recorded. */
    public record Occurrence(
            UUID practiceResultId,
            String artifactKind,
            long artifactId,
            String outcome,
            String origin,
            String summary,
            @Nullable String evidenceRationale,
            @Nullable JsonNode evidence,
            @Nullable Long practiceRevisionId,
            String observedAt) {

        static Occurrence of(PracticeResult practiceResult) {
            ReviewedWorkKey work = ReviewedWorkKey.of(practiceResult);
            PracticeRevision revision = practiceResult.getPracticeRevision();
            return new Occurrence(
                    practiceResult.getId(),
                    work.kind().value(),
                    work.id(),
                    practiceResult.getOutcome().name(),
                    practiceResult.getOrigin().name(),
                    practiceResult.getSummary(),
                    practiceResult.getEvidenceRationale(),
                    practiceResult.getEvidence(),
                    revision == null ? null : revision.getId(),
                    practiceResult.getObservedAt().toString());
        }
    }

    static InAppSupportContext complete(Instant readAt, List<PracticeSupport> practices) {
        return new InAppSupportContext(State.COMPLETE, readAt.toString(), null, List.copyOf(practices));
    }

    public static InAppSupportContext unavailable(Instant readAt) {
        return new InAppSupportContext(State.UNAVAILABLE, readAt.toString(), null, List.of());
    }

    static InAppSupportContext refused(Instant readAt, @Nullable FeedbackSuppressionReason reason) {
        return new InAppSupportContext(State.REFUSED, readAt.toString(), reason, List.of());
    }
}
