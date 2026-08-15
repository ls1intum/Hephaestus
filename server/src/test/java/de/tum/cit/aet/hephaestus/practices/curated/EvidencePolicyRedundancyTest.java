package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.EvidenceStance;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceDefaults;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptionsFixture;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Facts the shipped evidence vocabulary asserts about itself, held as tests so a source added later
 * cannot silently invalidate them. A failure here means deciding whether the new case is a genuine
 * requirement to reintroduce deliberately, or an authoring slip just caught — not relaxing the test.
 */
class EvidencePolicyRedundancyTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final ClasspathArtifactSourceCatalogRegistry registry = new ClasspathArtifactSourceCatalogRegistry(
        objectMapper,
        Clock.systemUTC()
    );
    private final PracticeSignalOptions signalOptions = PracticeSignalOptionsFixture.real();
    private final BundledPracticeCatalogLoader loader = new BundledPracticeCatalogLoader(
        objectMapper,
        new PracticeDefinitionValidator(registry, signalOptions),
        new PracticeEvidenceDefaults(registry, PracticeSignalOptionsFixture.catalog())
    );

    /**
     * Every kind a practice can be authored against has at least one source that declares it applies —
     * checked at build time, because the alternative is discovering it at review time, unwatched.
     */
    @Test
    void everyAuthorableArtifactKindHasEvidenceThatAppliesToIt() {
        for (ArtifactKind kind : signalOptions.authorableKinds()) {
            assertThat(registry.current().sourcesFor(kind.value()))
                .as(
                    "artifact kind '%s' can be authored against but no source declares it applies; a practice " +
                        "written on it would refuse every review it triggered",
                    kind
                )
                .isNotEmpty();
        }
    }

    /**
     * Which shipped practices claim something is <em>absent</em>, recorded here because it is a reading of
     * each practice's criteria rather than anything the code can derive. Deliberately short: a practice
     * that instead points at a contradiction it can quote does not need the whole capture. Adding to this
     * list is a decision about what a practice may assert, made here rather than noticed in a diff.
     */
    @Test
    void onlyThePracticesThatAssertAnAbsenceDemandAWholeCapture() {
        Map<String, Set<SourceKind>> exhaustive = new LinkedHashMap<>();
        loader
            .catalog()
            .practices()
            .forEach(practice ->
                practice
                    .definition()
                    .bindings()
                    .forEach(binding ->
                        binding
                            .needs()
                            .stream()
                            .filter(need -> need.stance() == EvidenceStance.EXHAUSTIVE)
                            .forEach(need ->
                                exhaustive
                                    .computeIfAbsent(practice.slug(), slug -> new LinkedHashSet<>())
                                    .add(need.sourceKind())
                            )
                    )
            );

        assertThat(exhaustive).containsOnlyKeys(
            "merged-past-unresolved-review-threads",
            "engaging-with-inline-review-comments",
            "issue-closed-with-unmet-outcome",
            "ready-and-traceable-handoff"
        );
        assertThat(exhaustive.get("merged-past-unresolved-review-threads")).containsExactly(
            new SourceKind("scm.review-threads")
        );
        assertThat(exhaustive.get("issue-closed-with-unmet-outcome")).containsExactly(
            new SourceKind("scm.issue.comments")
        );
    }
}
