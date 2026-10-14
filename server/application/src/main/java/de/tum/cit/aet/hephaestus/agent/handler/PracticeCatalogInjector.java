package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Stages only the pinned definitions and scripts assigned to this job. */
@Component
public class PracticeCatalogInjector {
    private final ObjectMapper mapper;

    public PracticeCatalogInjector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void inject(
            Map<String, byte[]> files,
            List<ReviewAssignmentSnapshot> assignments,
            ReviewKind scope,
            Map<ReviewAssignmentId, List<String>> contextPaths) {
        var index = mapper.createArrayNode();
        for (var assignment : assignments) {
            var practice = assignment.practice();
            var definition = practice.definition();
            var entry = index.addObject()
                    .put("slug", assignment.practiceSlug())
                    .put("name", definition.name())
                    .put("versionId", Long.toString(practice.practiceVersionId()));
            if (scope == ReviewKind.SCAN) {
                var paths =
                        Objects.requireNonNull(contextPaths.get(assignment.id()), "Missing frozen scan context grant");
                var granted = entry.putArray("contextPaths");
                paths.forEach(granted::add);
            }
            var sources = entry.putArray("readsSources");
            var exhaustive = entry.putArray("exhaustiveSources");
            entry.putArray("knownLimitations");
            var reads = scope == ReviewKind.SCAN
                    ? Objects.requireNonNull(definition.scan()).reads()
                    : definition.reads();
            for (var read : reads.sources()) {
                sources.add(read.source().value());
                if (read.need() == ReadNeed.COMPLETE)
                    exhaustive.add(read.source().value());
            }
            String whyItMatters = definition.guidance().whyItMatters();
            if (whyItMatters != null) entry.put("whyItMatters", whyItMatters);
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
