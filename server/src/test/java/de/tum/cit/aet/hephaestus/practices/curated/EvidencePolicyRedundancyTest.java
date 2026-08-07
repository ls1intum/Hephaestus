package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalog;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceContract;
import de.tum.cit.aet.hephaestus.evidence.RequiredCaptureQuality;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceDefaults;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceRequirement;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptionsFixture;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Facts the shipped evidence vocabulary asserts about itself, held as tests rather than as a paragraph
 * in a design note.
 *
 * <p>They are load-bearing: they are the argument for deleting {@code EvidenceProfile} and for moving
 * strictness out of the practice and into the source contract. A design decision justified by "we looked
 * and the data says X" decays the moment somebody adds a source, and the decay is silent — one practice
 * would want a stricter diff than another, and nothing would say so until the refactor that assumed
 * otherwise was already written.
 *
 * <p>If either of these fails, the correct response is <em>not</em> to relax the test. It is to decide
 * whether the new case is a genuine requirement — in which case the axis it needs must be reintroduced
 * deliberately — or an authoring slip that has just been caught.
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
        new PracticeEvidenceDefaults(registry)
    );

    /**
     * Every kind a practice can be authored against has at least one source that declares it applies.
     *
     * <p>This is what the named evidence profiles used to guarantee by existing: a kind with no profile
     * could not be selected. Now that the allow-list is derived, the failure mode moves — a kind nothing
     * supplies evidence for is authorable and refuses every review it triggers, at review time rather
     * than at build time. Asking here makes it a build-time answer again.
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
     * Every source a shipped practice requires is captured to the quality its own contract demands, and
     * the contract is the only place that quality is written down.
     *
     * <p>This is the test that licensed moving the axis: it used to read that no two practices demanded
     * the same source at different strictnesses, which is what made a per-practice statement of it pure
     * duplication. Now that the statement lives in one place the invariant becomes the weaker but still
     * load-bearing one — nothing may go on demanding a quality of a source the source cannot supply,
     * because such a practice is switched on and refuses every review it ever triggers.
     */
    @Test
    void everyRequiredSourceCanSupplyTheQualityItsContractDemands() {
        ArtifactSourceCatalog catalog = registry.current();
        Map<SourceKind, RequiredCaptureQuality> demanded = new LinkedHashMap<>();

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
                            .filter(PracticeEvidenceRequirement::refuses)
                            .forEach(need ->
                                demanded.put(
                                    need.sourceKind(),
                                    catalog.source(need.sourceKind()).orElseThrow().requiredQuality()
                                )
                            )
                    )
            );

        assertThat(demanded).isNotEmpty();
        demanded.forEach((source, quality) -> {
            ArtifactSourceContract contract = catalog.source(source).orElseThrow();
            assertThat(!quality.demandsComplete() || contract.completenessPolicy().supportsComplete())
                .as("source '%s' is required by a shipped practice but can never report COMPLETE", source)
                .isTrue();
            assertThat(!quality.demandsContent() || contract.completenessPolicy().supportsEmpty())
                .as("source '%s' demands non-emptiness although an empty capture of it is never valid", source)
                .isTrue();
        });
    }
}
