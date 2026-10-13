package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedReview;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionResultParser;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import java.util.ArrayList;
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

/** Reads the submission against this job's admitted results and gives it one normalized identity. */
@Component
@RequiredArgsConstructor
class PublicReviewCompletionInputs {
    private final PracticeResultRepository results;
    private final FeedbackCompositionResultParser parser;
    private final PublicReviewEligibility eligibility;
    private final JsonMapper mapper;
    private final ReviewOutputService reviewOutput;

    record Prepared(
            List<PracticeResult> results,
            Set<UUID> publicIds,
            @Nullable ComposedReview review,
            @Nullable JsonNode normalized,
            @Nullable String digest) {}

    Prepared prepare(AgentJob job, @Nullable JsonNode submitted, JsonNode admitted) {
        List<PracticeResult> persisted =
                results.findByAgentJobId(job.getId(), job.getWorkspace().getId());
        Set<UUID> publicIds = eligibility.publicResultIds(job, persisted);
        ComposedReview review = parser.review(submitted, admitted.path("results"));
        JsonNode normalized = review == null ? null : normalized(review, admitted.path("results"));
        String digest = normalized == null ? null : ProvenanceDigest.sha256Hex(mapper.writeValueAsBytes(normalized));
        return new Prepared(persisted, publicIds, review, normalized, digest);
    }

    /**
     * The review as the server read it, in the runner's own field order: what is stored and compared, so a repeat is
     * judged by its value and not by how its JSON was spelled.
     */
    private ObjectNode normalized(ComposedReview review, JsonNode admittedResults) {
        ObjectNode root = mapper.createObjectNode();
        ComposedReview.Summary summary = review.summary();
        if (summary == null) {
            root.putNull("summary");
        } else {
            ObjectNode node = root.putObject("summary");
            node.put("body", summary.body());
            references(node.putArray("basedOn"), summary.basedOn(), summary.observations(), admittedResults);
        }
        ArrayNode inline = root.putArray("inline");
        for (ComposedReview.InlineNote note : review.inline()) {
            ObjectNode node = inline.addObject();
            node.put("body", note.body());
            references(node.putArray("basedOn"), note.basedOn(), note.observations(), admittedResults);
            ObjectNode anchor = node.putObject("anchor");
            anchor.put("observationId", note.anchor().observationId());
            anchor.put("citationIndex", note.anchor().citationIndex());
        }
        ArrayNode withheld = root.putArray("withheld");
        for (ComposedReview.Withheld decision : review.withheld()) {
            ObjectNode node = withheld.addObject();
            references(node.putArray("basedOn"), decision.basedOn(), List.of(), admittedResults);
            node.put("reason", decision.reason().name());
        }
        return root;
    }

    private static void references(
            ArrayNode array, List<String> results, List<String> observations, JsonNode admittedResults) {
        for (String resultId : results) {
            List<String> problems = new ArrayList<>();
            for (JsonNode result : admittedResults) {
                if (!resultId.equals(result.path("practiceResultId").asString())) continue;
                for (JsonNode problem : result.path("observations")) {
                    String id = problem.path("observationId").asString();
                    if (observations.contains(id)) problems.add(id);
                }
            }
            if (problems.isEmpty()) {
                array.addObject().put("practiceResultId", resultId);
            } else {
                for (String problem : problems) {
                    array.addObject().put("practiceResultId", resultId).put("observationId", problem);
                }
            }
        }
    }

    /**
     * The admitted results as delivery reads them, after their stored citations verify again: a result whose
     * evidence no longer verifies throws, and the completion is refused.
     */
    List<ReviewResultParser.ValidatedResult> recorded(AgentJob job, List<PracticeResult> persisted) {
        reviewOutput.requirePublished(job);
        return reviewOutput.recorded(job, persisted);
    }
}
