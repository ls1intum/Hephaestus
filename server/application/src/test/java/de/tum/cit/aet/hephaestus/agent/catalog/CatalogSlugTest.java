package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class CatalogSlugTest extends BaseUnitTest {

    @Test
    void shouldNormalizeHumanNamesIntoInternalSlugs() {
        assertThat(CatalogSlug.from("  GPT 5.4 / EU  ")).isEqualTo("gpt-5-4-eu");
    }

    @Test
    void shouldFallBackWhenNameHasNoAsciiLettersOrDigits() {
        assertThat(CatalogSlug.from("---")).isEqualTo("item");
    }
}
