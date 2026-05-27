package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class SlackSubjectParserTest extends BaseUnitTest {

    private final SlackSubjectParser parser = new SlackSubjectParser();

    @Test
    void parsesFourComponentSubject() {
        EventTypeKey key = parser.parse("slack.T123.C456.message");
        assertThat(key.kind()).isEqualTo(IntegrationKind.SLACK);
        assertThat(key.eventType()).isEqualTo("message");
    }

    @Test
    void rejectsWrongPrefix() {
        assertThatThrownBy(() -> parser.parse("github.acme.repo.push"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Not a Slack subject");
    }

    @Test
    void rejectsInsufficientComponents() {
        assertThatThrownBy(() -> parser.parse("slack.T123.C456"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("4 dot-separated tokens");
    }

    @Test
    void rejectsBlankEventToken() {
        assertThatThrownBy(() -> parser.parse("slack.T123.C456."))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank event token");
    }

    @Test
    void rejectsNullSubject() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
