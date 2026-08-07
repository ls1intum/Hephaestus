package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeTriggerOptionsFixture;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
    private final BundledPracticeCatalogLoader loader = new BundledPracticeCatalogLoader(
        objectMapper,
        new PracticeDefinitionValidator(registry, PracticeTriggerOptionsFixture.real())
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
        for (ArtifactKind kind : ArtifactKinds.authorable()) {
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
     * Across every shipped practice, a given source is always demanded at the same strictness. Nothing in
     * the schema enforces that today — each practice restates completeness and content per source — so the
     * uniformity is evidence that the axis belongs to the source rather than to the practice.
     *
     * <p>Stated over the practices rather than over the raw policy map on purpose: a policy nothing
     * references proves nothing about what authors need.
     */
    @Test
    void aSourceIsAlwaysDemandedAtTheSameStrictness() {
        Map<SourceKind, Set<String>> strictnessBySource = new TreeMap<>((a, b) -> a.value().compareTo(b.value()));

        loader
            .catalog()
            .practices()
            .forEach(practice ->
                practice
                    .definition()
                    .automatedReviewPolicy()
                    .requiredEvidence()
                    .forEach(requirement ->
                        strictnessBySource
                            .computeIfAbsent(requirement.sourceKind(), k -> new LinkedHashSet<>())
                            .add(requirement.completeness().name() + "+" + requirement.content().name())
                    )
            );

        assertThat(strictnessBySource).isNotEmpty();
        Map<SourceKind, Set<String>> disagreeing = strictnessBySource
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        assertThat(disagreeing)
            .as(
                "a source demanded at two different strictnesses would mean strictness is a per-practice " +
                    "choice after all, and could not be moved into the source contract without losing that choice"
            )
            .isEmpty();
    }
}
