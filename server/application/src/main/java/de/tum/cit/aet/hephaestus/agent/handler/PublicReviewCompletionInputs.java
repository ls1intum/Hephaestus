package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedReview;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionResultParser;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Reads the submission against this job's admitted observations and gives it one normalized identity. */
@Component
@RequiredArgsConstructor
class PublicReviewCompletionInputs {
    private final ObservationRepository observations;
    private final FeedbackCompositionResultParser parser;
    private final PublicReviewEligibility eligibility;
    private final JsonMapper mapper;

    record Prepared(
            List<Observation> observations,
            Set<UUID> publicIds,
            @Nullable ComposedReview review,
            @Nullable JsonNode normalized,
            @Nullable String digest) {}

    Prepared prepare(AgentJob job, @Nullable JsonNode submitted, JsonNode admitted) {
        List<Observation> persisted =
                observations.findByAgentJobId(job.getId(), job.getWorkspace().getId());
        Set<UUID> publicIds = eligibility.publicObservationIds(job, persisted);
        ComposedReview review = parser.review(submitted, admitted.path("observations"));
        JsonNode normalized = review == null ? null : normalized(review);
        String digest = normalized == null ? null : ProvenanceDigest.sha256Hex(mapper.writeValueAsBytes(normalized));
        return new Prepared(persisted, publicIds, review, normalized, digest);
    }

    /**
     * The review as the server read it, in the runner's own field order: what is stored and compared, so a repeat is
     * judged by its value and not by how its JSON was spelled.
     */
    private ObjectNode normalized(ComposedReview review) {
        ObjectNode root = mapper.createObjectNode();
        ComposedReview.Summary summary = review.summary();
        if (summary == null) {
            root.putNull("summary");
        } else {
            ObjectNode node = root.putObject("summary");
            node.put("body", summary.body());
            strings(node.putArray("basedOn"), summary.basedOn());
        }
        ArrayNode inline = root.putArray("inline");
        for (ComposedReview.InlineNote note : review.inline()) {
            ObjectNode node = inline.addObject();
            node.put("body", note.body());
            strings(node.putArray("basedOn"), note.basedOn());
            ObjectNode anchor = node.putObject("anchor");
            anchor.put("observationId", note.anchor().observationId());
            anchor.put("citationIndex", note.anchor().citationIndex());
        }
        ArrayNode withheld = root.putArray("withheld");
        for (ComposedReview.Withheld decision : review.withheld()) {
            ObjectNode node = withheld.addObject();
            strings(node.putArray("basedOn"), decision.basedOn());
            node.put("reason", decision.reason().name());
        }
        return root;
    }

    private static void strings(ArrayNode array, List<String> values) {
        values.forEach(array::add);
    }
}
