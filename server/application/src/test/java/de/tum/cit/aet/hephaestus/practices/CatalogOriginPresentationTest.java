package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeVersionDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackModeSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogOriginPresentationTest extends BaseUnitTest {
    @Test
    void shouldPresentAnUnlinkedPracticeAsLocalWithInstanceInheritance() {
        var versions = mock(PracticeVersionService.class);
        var defaults = mock(WorkspaceReviewDefaultsProvider.class);
        var version = TestEntities.practiceVersion();
        var practice = new Practice();
        practice.setId(9007199254740993L);
        practice.setSlug("local");
        practice.setKind(new ItemKind("scm.pull_request"));
        practice.setCurrentVersion(version);
        practice.setCreatedAt(Instant.EPOCH);
        var current = new PracticeVersionDTO(
                "12",
                1,
                "Local practice",
                DefinitionTestFixtures.definition(),
                true,
                null,
                "v6:" + "a".repeat(64),
                Instant.EPOCH,
                null,
                List.of(),
                false);
        when(versions.viewCurrent(practice)).thenReturn(current);
        var dto = new CatalogOriginPresenter(
                        mock(CatalogService.class), mock(PracticeReviewDigest.class), versions, defaults)
                .present(10L, practice);
        assertThat(dto.id()).isEqualTo("9007199254740993");
        assertThat(dto.currentVersion()).isEqualTo(current);
        assertThat(dto.catalogOrigin().state()).isEqualTo(CatalogEntryState.YOURS);
        assertThat(dto.feedbackMode().effective()).isEqualTo(FeedbackMode.APPROVE_FIRST);
        assertThat(dto.feedbackMode().source()).isEqualTo(FeedbackModeSource.INSTANCE);
    }
}
