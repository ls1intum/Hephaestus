package de.tum.cit.aet.hephaestus.integration.docs.item;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class DocumentEventsTest extends BaseUnitTest {
    @Test
    void shouldChangeTheVersionWhenTheTitleOrArchiveStateChanges() {
        var version = DocumentEvents.version("Title", "body-hash", false, null);
        assertThat(DocumentEvents.version("Title", "body-hash", false, null)).isEqualTo(version);
        assertThat(DocumentEvents.version("New title", "body-hash", false, null))
                .isNotEqualTo(version);
        assertThat(DocumentEvents.version("Title", "body-hash", true, null)).isNotEqualTo(version);
    }

    @Test
    void shouldKeepVersionsBoundedWhenTitlesHaveTheirMaximumLength() {
        assertThat(DocumentEvents.version("x".repeat(1024), "body-hash", false, null)
                        .value())
                .hasSize(64);
    }

    @Test
    void shouldChangeVersionWhenNativeAuthorshipChangesWithoutAnEditToTheBody() {
        assertThat(DocumentEvents.version("Title", "body-hash", false, "author-a"))
                .isNotEqualTo(DocumentEvents.version("Title", "body-hash", false, "author-b"));
    }

    @Test
    void absentNativeAuthorshipCannotCollideWithTheLiteralWordNull() {
        assertThat(DocumentEvents.version("Title", "body-hash", false, null))
                .isNotEqualTo(DocumentEvents.version("Title", "body-hash", false, "null"));
    }
}
