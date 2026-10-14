package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedByKind;
import java.util.List;
import java.util.Map;

public final class DefinitionTestFixtures {
    private DefinitionTestFixtures() {}

    public static PracticeDefinition definition() {
        return definition("Describe the change", "Explain the change and its purpose.");
    }

    public static PracticeDefinition definition(String name, String criteria) {
        return new PracticeDefinition(
                name,
                new ReviewOn(List.of(new ItemEventName("scm.pull_request.opened")), Map.of()),
                null,
                AboutRole.AUTHOR,
                new PracticeReads(
                        "2.0.0",
                        List.of(new PracticeReadSource(new SourceKind("scm.pull-request.core"), ReadNeed.REQUIRED))),
                new ReviewedBy(ReviewedByKind.HEPHAESTUS, null, List.of()),
                criteria,
                null,
                null,
                true,
                new PracticeGuidance(
                        "Makes the change clear.",
                        "The description explains the purpose.",
                        "You explain each change.",
                        null,
                        null));
    }

    public static PracticeDefinition rename(PracticeDefinition original, String name) {
        return new PracticeDefinition(
                name,
                original.reviewOn(),
                original.appliesWhen(),
                original.about(),
                original.reads(),
                original.reviewedBy(),
                original.criteria(),
                original.script(),
                original.scan(),
                original.lineNotes(),
                original.guidance());
    }
}
