package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceDefaults;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptionsFixture;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BundledPracticeCatalogLoaderTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final ClasspathArtifactSourceCatalogRegistry catalogs = new ClasspathArtifactSourceCatalogRegistry(
        objectMapper,
        java.time.Clock.systemUTC()
    );
    private final BundledPracticeCatalogLoader loader = new BundledPracticeCatalogLoader(
        objectMapper,
        new PracticeDefinitionValidator(catalogs, PracticeSignalOptionsFixture.real()),
        new PracticeEvidenceDefaults(catalogs)
    );

    @Test
    void shouldLoadComposedDefinitionsAndScripts() {
        BundledPracticeCatalog catalog = loader.catalog();

        assertThat(catalog.areas()).allSatisfy(area -> assertThat(area.definition().name()).isNotBlank());
        assertThat(catalog.practices()).allSatisfy(practice ->
            assertThat(practice.definition().criteria()).contains("\n\n---\n\n")
        );
        assertThat(catalog.practices())
            .filteredOn(practice -> practice.slug().equals("ships-tests-with-the-change"))
            .singleElement()
            .satisfies(practice -> assertThat(practice.definition().precomputeScript()).isNotBlank());
    }

    @Test
    void shouldKeepDetectorVocabularyOutOfLearnerCopy() {
        Pattern detectorVocabulary = Pattern.compile("\\b(?:PRESENT|ABSENT|GOOD|BAD|NOT_APPLICABLE)\\b");

        assertThat(loader.catalog().practices()).allSatisfy(practice -> {
            assertThat(practice.definition().whyItMatters())
                .as("whyItMatters for '%s'", practice.slug())
                .isNotNull()
                .doesNotContainPattern(detectorVocabulary);
            assertThat(practice.definition().whatGoodLooksLike())
                .as("whatGoodLooksLike for '%s'", practice.slug())
                .isNotNull()
                .doesNotContainPattern(detectorVocabulary);
        });
    }

    @Test
    void shouldUseRealNewlinesInCriteria() {
        assertThat(loader.catalog().practices()).allSatisfy(practice ->
            assertThat(practice.definition().criteria()).as("criteria for '%s'", practice.slug()).doesNotContain("\\n")
        );
    }
}
