package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Stages only the pinned definitions and scripts assigned to this job. */
@Component
public class PracticeCatalogInjector {
    private final ObjectMapper mapper;

    public PracticeCatalogInjector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void inject(Map<String, byte[]> files, List<ReviewAssignmentSnapshot> assignments, ReviewKind scope) {
        var index = mapper.createArrayNode();
        for (var assignment : assignments) {
            var practice = assignment.practice();
            var definition = practice.definition();
            var entry = index.addObject()
                    .put("slug", assignment.practiceSlug())
                    .put("name", definition.name())
                    .put("practiceId", Long.toString(practice.practiceId()))
                    .put("versionId", Long.toString(practice.practiceVersionId()))
                    .put("reviewDigest", practice.reviewDigest());
            entry.set("definition", mapper.valueToTree(definition));
            var sources = entry.putArray("readsSources");
            (scope == ReviewKind.SCAN ? Objects.requireNonNull(definition.scan()).reads() : definition.reads())
                    .sources()
                    .forEach(read -> sources.add(read.source().value()));
            files.put(
                    SandboxLayout.PRACTICES_PREFIX + assignment.practiceSlug() + ".md",
                    definition.criteria().getBytes(StandardCharsets.UTF_8));
            String script = definition.script();
            if (script != null)
                files.put(
                        SandboxLayout.PRACTICE_SCRIPT_PRACTICES_PREFIX + assignment.practiceSlug() + ".ts",
                        script.getBytes(StandardCharsets.UTF_8));
        }
        files.put(SandboxLayout.PRACTICES_PREFIX + "index.json", mapper.writeValueAsBytes(index));
    }
}
