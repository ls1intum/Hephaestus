package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.FeedbackAdmission;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Decides which of a cycle's observations are eligible for conversational delivery. An observation is
 * {@link ConversationRoutingDecision#ADMIT admitted} to the IN_CHAT channel iff ALL of: its provenance admits the
 * conversation channel, its practice's autonomy tier admits the conversation channel, author-targeted, a
 * {@link Assessment#BAD} problem, has no natural inline anchor, and does not share a {@code recurrence_key} with a
 * DELIVERED IN_CONTEXT unit for the same recipient. Every other case is a named, testable non-admission reason.
 *
 * <p>Pure routing - it reads the feedback ledger but writes nothing. The {@link ConversationalFeedbackPreparer}
 * turns the admitted set into PREPARED units.
 */
@Component
public class FeedbackChannelRouter {

    private final FeedbackRepository feedbackRepository;
    private final ObservationRepository observationRepository;
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;

    public FeedbackChannelRouter(
        FeedbackRepository feedbackRepository,
        ObservationRepository observationRepository,
        WorkspaceReviewDefaultsProvider workspaceDefaults
    ) {
        this.feedbackRepository = feedbackRepository;
        this.observationRepository = observationRepository;
        this.workspaceDefaults = workspaceDefaults;
    }

    /** The observations from {@code observations} that are eligible for conversational delivery, order preserved. */
    public List<Observation> admit(List<Observation> observations, long workspaceId, RoutingContext context) {
        WorkspaceReviewDefaults defaults = workspaceDefaults.forWorkspace(workspaceId);
        Map<UUID, PracticeReviewTier> tiers = tiersFor(observations, defaults.defaultTier());
        List<Observation> admitted = new ArrayList<>();
        for (Observation observation : observations) {
            ConversationRoutingDecision decision = route(
                observation,
                tiers.get(observation.getId()),
                workspaceId,
                context
            );
            if (decision == ConversationRoutingDecision.ADMIT) {
                admitted.add(observation);
            }
        }
        return admitted;
    }

    /**
     * Route a single observation. See the class javadoc for the admission predicate.
     *
     * @param tier the <em>effective</em> tier of the observation's practice, already resolved through the
     *     practice → area → workspace chain, or {@code null} when it could not be resolved. Passed in rather
     *     than read off {@code observation.getPractice()} on purpose: that association is lazy, so reading it
     *     here would make the routing rule depend on whether the caller happens to hold a session. A null
     *     tier lets the remaining rules decide rather than silently withholding coaching the developer was
     *     owed.
     */
    public ConversationRoutingDecision route(
        Observation observation,
        @Nullable PracticeReviewTier tier,
        long workspaceId,
        RoutingContext context
    ) {
        // Provenance and tier in one predicate, so this path and the in-context one cannot drift on what
        // "may we say this here" means.
        if (!FeedbackAdmission.delivers(observation.getOrigin(), tier, FeedbackChannel.IN_CHAT)) {
            return observation.getOrigin().delivers(FeedbackChannel.IN_CHAT)
                ? ConversationRoutingDecision.PRACTICE_TIER_QUIET
                : ConversationRoutingDecision.BACKFILL_QUIET;
        }
        // Reviewer-targeted delivery: deferred (ADR 0021).
        if (context.recipientRole() != RecipientRole.AUTHOR) {
            return ConversationRoutingDecision.REVIEWER_DEFERRED;
        }
        // Only a problem is raised in a coaching turn; strengths, abstentions and undecided measurements
        // are not delivered.
        if (!observation.getPresence().carriesValence() || observation.getAssessment() != Assessment.BAD) {
            return ConversationRoutingDecision.NOT_DELIVERABLE;
        }
        // A locus with a natural diff anchor belongs in-context, not in the conversation.
        if (hasNaturalInlineAnchor(observation)) {
            return ConversationRoutingDecision.HAS_INLINE_ANCHOR;
        }
        // Do not re-raise a locus the developer already received in-context (keyed on the cross-run recurrence key).
        String recurrenceKey = observation.getRecurrenceKey();
        if (
            recurrenceKey != null &&
            feedbackRepository.existsDeliveredInContextForRecurrenceKey(
                workspaceId,
                observation.getAboutUserId(),
                recurrenceKey
            )
        ) {
            return ConversationRoutingDecision.ALREADY_DELIVERED_IN_CONTEXT;
        }
        return ConversationRoutingDecision.ADMIT;
    }

    /**
     * One projection query for the whole cycle's tiers. An observation with no id yet is simply absent
     * from the map, which the null-tier rule above already covers.
     */
    private Map<UUID, PracticeReviewTier> tiersFor(
        List<Observation> observations,
        PracticeReviewTier workspaceDefault
    ) {
        List<UUID> ids = observations.stream().map(Observation::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PracticeReviewTier> tiers = new HashMap<>();
        for (var row : observationRepository.practiceReviewTiersFor(ids)) {
            tiers.put(
                row.getObservationId(),
                ReviewTierResolver.resolvePractice(row.getPracticeTier(), row.getAreaTier(), workspaceDefault).tier()
            );
        }
        return tiers;
    }

    private static boolean hasNaturalInlineAnchor(Observation observation) {
        if (!ArtifactKinds.hasInlineLane(observation.getArtifactKind())) {
            return false;
        }
        JsonNode evidence = observation.getEvidence();
        if (evidence == null) {
            return false;
        }
        JsonNode citations = evidence.path("citations");
        if (!citations.isArray() || citations.isEmpty()) {
            return false;
        }
        for (JsonNode citation : citations) {
            if ("scm.pull-request.diff".equals(citation.path("sourceKind").asString())) {
                return true;
            }
        }
        return false;
    }
}
