package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.RuntimeReviewTestData;
import de.tum.cit.aet.hephaestus.practices.spi.MentorPracticeQuery;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PracticeCatalogContentSourceTest extends BaseUnitTest {
    @Test
    void shouldStageTheDefinitionsTheCatalogOwnerAuthorized() {
        var practices = mock(MentorPracticeQuery.class);
        when(practices.current(1))
                .thenReturn(List.of(new MentorPracticeQuery.MentorPractice(
                        "clear-names", RuntimeReviewTestData.definition(null, null))));
        var row = new PracticeCatalogContentSource(practices, new JsonMapper())
                .buildPayload(1L)
                .path("practices")
                .get(0);
        assertThat(row.path("slug").asString()).isEqualTo("clear-names");
        assertThat(row.path("displayName").asString()).isEqualTo("Clear names");
        assertThat(row.path("criteria").asString()).isEqualTo("Use clear names.");
    }

    @Test
    void shouldKeepAnEmptyAuthorizedCatalogEmpty() {
        var practices = mock(MentorPracticeQuery.class);
        when(practices.current(1)).thenReturn(List.of());
        assertThat(new PracticeCatalogContentSource(practices, new JsonMapper())
                        .buildPayload(1L)
                        .path("practices"))
                .isEmpty();
    }
}
