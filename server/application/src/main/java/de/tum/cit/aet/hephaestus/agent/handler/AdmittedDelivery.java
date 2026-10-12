package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedResult;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedReview;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What a review delivers on the reviewed work once its results have passed the delivery gates. */
sealed interface AdmittedDelivery {

    /** Some part of the review rests on a result that awaits approval, so the review waits as a whole. */
    record Proposed(DeliveryContent content) implements AdmittedDelivery {}

    /** Posted without approval; {@code content} is null when nothing is left to post. */
    record Automatic(@Nullable DeliveryContent content, Set<String> contributingPracticeSlugs)
            implements AdmittedDelivery {}

    /**
     * {@link Proposed} when a part that may go out rests on a result awaiting approval: the review is one
     * text, so it waits as a whole. {@link Automatic} otherwise, with the practices its parts rest on.
     *
     * @param recorded every result the review recorded, in delivery order
     * @param publicSubjects the person each publicly eligible result is about, by result id ({@link PublicReviewEligibility})
     * @param awaitingApproval the results awaiting approval
     * @param automatic the results admitted without approval
     */
    static AdmittedDelivery decide(
            ComposedReview review,
            ArtifactKind artifact,
            List<ValidatedResult> recorded,
            Map<UUID, Long> publicSubjects,
            List<ValidatedResult> awaitingApproval,
            List<ValidatedResult> automatic) {
        ComposedReviewAdmission.Admission admission =
                ComposedReviewAdmission.admit(review, artifact, recorded, publicSubjects, automatic, awaitingApproval);
        DeliveryContent content = admission.content();
        if (admission.needsApproval() && content != null) {
            return new Proposed(content);
        }
        return new Automatic(content, content == null ? Set.of() : content.contributingPracticeSlugs(recorded));
    }
}
