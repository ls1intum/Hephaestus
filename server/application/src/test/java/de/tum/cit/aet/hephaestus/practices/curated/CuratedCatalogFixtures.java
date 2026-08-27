package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;

final class CuratedCatalogFixtures {

    private CuratedCatalogFixtures() {}

    static PracticeDefinition practice(String name, String criteria, String whyItMatters) {
        return new PracticeDefinition(
                name,
                PracticeTestEvidence.bindings(ArtifactKinds.PULL_REQUEST),
                criteria,
                null,
                PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST),
                whyItMatters,
                "Bundled exemplar",
                "packaging");
    }

    static GroupDefinition group(String name, String description) {
        return new GroupDefinition(name, description, "Target", "sky");
    }
}
