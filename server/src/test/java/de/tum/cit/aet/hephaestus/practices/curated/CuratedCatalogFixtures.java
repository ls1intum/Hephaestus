package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;

/** Definitions for tests, so a case describes content rather than construction. */
final class CuratedCatalogFixtures {

    private CuratedCatalogFixtures() {}

    static PracticeDefinition practice(String name, String criteria, String whyItMatters) {
        return new PracticeDefinition(
            name,
            WorkArtifact.PULL_REQUEST,
            List.of("PullRequestCreated"),
            criteria,
            null,
            whyItMatters,
            "Bundled exemplar",
            "packaging"
        );
    }

    static AreaDefinition area(String name, String description) {
        return new AreaDefinition(name, description, 0, "Target", "sky");
    }
}
