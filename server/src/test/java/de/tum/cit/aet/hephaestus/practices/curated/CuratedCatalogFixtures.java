package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.util.List;

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
            "packaging"
        );
    }

    static AreaDefinition area(String name, String description) {
        return new AreaDefinition(name, description, "Target", "sky");
    }
}
