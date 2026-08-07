package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalog;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceContract;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfile;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeTriggerOptionsFixture;
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
 * Two facts the shipped evidence vocabulary asserts about itself, held as tests rather than as a
 * paragraph in a design note.
 *
 * <p>Both are load-bearing: they are the entire argument for deleting {@code EvidenceProfile} and for
 * moving strictness out of the practice and into the source contract. A design decision justified by
 * "we looked and the data says X" decays the moment somebody adds a source, and the decay is silent —
 * the profile would quietly stop being derivable, or one practice would want a stricter diff than
 * another, and nothing would say so until the refactor that assumed otherwise was already written.
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
     * Every evidence profile is exactly the set of sources that declare they apply to its artifact kind,
     * which makes the profile a materialized view: it stores an answer the catalog already computes, and
     * the only thing it can add is a way to be wrong about it.
     */
    @Test
    void everyEvidenceProfileIsDerivableFromTheSourcesThemselves() {
        ArtifactSourceCatalog catalog = registry.current();

        Map<String, Set<SourceKind>> byArtifactKind = new LinkedHashMap<>();
        for (ArtifactSourceContract source : catalog.sources()) {
            for (String kind : source.artifactKinds()) {
                byArtifactKind.computeIfAbsent(kind, k -> new LinkedHashSet<>()).add(source.kind());
            }
        }

        assertThat(catalog.profiles()).isNotEmpty();
        for (EvidenceProfile profile : catalog.profiles()) {
            assertThat(profile.allowedSources())
                .as(
                    "profile '%s' must equal {source | '%s' in source.artifactKinds}; a profile that " +
                        "diverges is a second, hand-maintained answer to a question the catalog already answers",
                    profile.id(),
                    profile.artifactKind()
                )
                .isEqualTo(byArtifactKind.getOrDefault(profile.artifactKind(), Set.of()));
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
