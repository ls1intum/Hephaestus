package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BundledPracticeCatalogLoaderTest extends BaseUnitTest {

    private final BundledPracticeCatalogLoader loader = new BundledPracticeCatalogLoader(JsonMapper.builder().build());

    @Test
    void shouldLoadComposedDefinitionsAndScripts() {
        BundledPracticeCatalog catalog = loader.load();

        assertThat(catalog.catalogRevision()).isEqualTo(1);
        assertThat(catalog.contentDigest()).isEqualTo(
            "ab744b2836878e92f69d4492b9594f47178cb2d5a07f394280e32e150b5d78d3"
        );
        assertThat(catalog.areas()).isNotEmpty();
        assertThat(catalog.practices()).allSatisfy(practice -> {
            assertThat(practice.definition().criteria()).contains("\n\n---\n\n");
            assertThat(practice.definitionDigest()).hasSize(64);
        });
        assertThat(catalog.practices())
            .filteredOn(practice -> practice.slug().equals("ships-tests-with-the-change"))
            .singleElement()
            .satisfies(practice -> assertThat(practice.definition().precomputeScript()).isNotBlank());
    }

    @Test
    void shouldKeepDetectorVocabularyOutOfLearnerCopy() {
        Pattern detectorVocabulary = Pattern.compile("\\b(?:PRESENT|ABSENT|GOOD|BAD|NOT_APPLICABLE)\\b");

        assertThat(loader.load().practices()).allSatisfy(practice -> {
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
        assertThat(loader.load().practices()).allSatisfy(practice ->
            assertThat(practice.definition().criteria()).as("criteria for '%s'", practice.slug()).doesNotContain("\\n")
        );
    }
}
