package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OutlineSubjectParser")
class OutlineSubjectParserTest extends BaseUnitTest {

    private final OutlineSubjectParser parser = new OutlineSubjectParser();

    @Test
    void parsesFourComponentSubject() {
        // SubjectKeyDeriver sanitizes dots in Outline event names (e.g. "documents.create")
        // to underscores so the subject stays at 4 tokens. The parser asserts that shape.
        EventTypeKey key = parser.parse("outline.team-abc.coll-def.documents_create");
        assertThat(key.kind()).isEqualTo(IntegrationKind.OUTLINE);
        assertThat(key.eventType()).isEqualTo("documents_create");
    }

    @Test
    void rejectsWrongPrefix() {
        assertThatThrownBy(() -> parser.parse("slack.T.C.message"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Not an Outline subject");
    }

    @Test
    void rejectsInsufficientComponents() {
        assertThatThrownBy(() -> parser.parse("outline.team-abc.coll-def"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("4 dot-separated tokens");
    }

    @Test
    void rejectsBlankEventToken() {
        assertThatThrownBy(() -> parser.parse("outline.team.coll."))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank event token");
    }

    @Test
    void rejectsNullSubject() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
