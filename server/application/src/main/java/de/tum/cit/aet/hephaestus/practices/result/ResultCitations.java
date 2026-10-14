package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Problem;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Stored citations include problem citations; authorization must also include example and open-question sources. */
public final class ResultCitations {

    private ResultCitations() {}

    public static List<JsonNode> own(@Nullable JsonNode evidence, List<Problem> problems) {
        Set<JsonNode> problemPlaces = new HashSet<>();
        problems.forEach(problem -> problem.getCitations().forEach(problemPlaces::add));
        List<JsonNode> own = new ArrayList<>();
        if (evidence != null) {
            for (JsonNode citation : evidence.path("citations")) {
                if (!problemPlaces.contains(citation)) own.add(citation);
            }
        }
        return List.copyOf(own);
    }

    public static List<JsonNode> all(Result result) {
        return all(result.getEvidence(), result.getExample());
    }

    public static List<JsonNode> all(@Nullable JsonNode evidence, @Nullable JsonNode example) {
        List<JsonNode> all = new ArrayList<>();
        if (evidence != null) {
            evidence.path("citations").forEach(all::add);
            for (JsonNode question : evidence.path("openQuestions")) {
                if (question.path("citation").isObject()) all.add(question.path("citation"));
            }
        }
        if (example != null && example.path("citation").isObject()) all.add(example.path("citation"));
        return List.copyOf(all);
    }
}
