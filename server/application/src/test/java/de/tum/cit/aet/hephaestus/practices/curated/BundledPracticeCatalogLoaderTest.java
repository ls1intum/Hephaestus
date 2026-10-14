package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BundledPracticeCatalogLoaderTest extends BaseUnitTest {
    @Test
    void shouldLoadExplicitKindsAndStoredGoingWellGuidanceFromTheCurrentBundle() {
        var loader =
                new BundledPracticeCatalogLoader(JsonMapper.builder().build(), mock(PracticeDefinitionValidator.class));
        assertThat(loader.catalog().practices()).isNotEmpty().allSatisfy(entry -> {
            assertThat(entry.definition().kind()).isNotNull();
            assertThat(entry.definition().definition().reads().contract()).isEqualTo("2.0.0");
        });
    }
}
