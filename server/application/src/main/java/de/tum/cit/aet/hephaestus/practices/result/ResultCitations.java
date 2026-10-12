package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * The citations of a practice result. A result's stored {@code evidence.citations} lists every place it rests on: its
 * own citations, then each of its problems' that is not already among them. A reader that shows the result beside its
 * problems takes its own: the stored list without the problems'. A reader that authorizes or attributes sources takes
 * every place, the example's and the open questions' included.
 */
public final class ResultCitations {

    private ResultCitations() {}

    /** The result's own citations: the stored list without any citation one of its problems carries. */
    public static List<JsonNode> own(@Nullable JsonNode evidence, List<Observation> problems) {
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

    /** Every place the result cites: the stored list, the example's citation and each open question's. */
    public static List<JsonNode> all(PracticeResult result) {
        return all(result.getEvidence(), result.getExample());
    }

    public static List<JsonNode> all(@Nullable JsonNode evidence, @Nullable JsonNode example) {
        List<JsonNode> all = new ArrayList<>();
        if (evidence != null) {
            evidence.path("citations").forEach(all::add);
            for (JsonNode question : evidence.path("undecidability")) {
                if (question.path("citation").isObject()) all.add(question.path("citation"));
            }
        }
        if (example != null && example.path("citation").isObject()) all.add(example.path("citation"));
        return List.copyOf(all);
    }
}
