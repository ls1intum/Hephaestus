package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

/**
 * Parity with {@code GithubSubjectParserTest}/{@code GitlabSubjectParserTest}: pins the
 * {@code slack.<team>.<channel>.<event>} → {@link EventTypeKey} contract and the malformed-subject
 * rejection arms of {@link SlackSubjectParser}.
 */
class SlackSubjectParserTest extends BaseUnitTest {

    private final SlackSubjectParser parser = new SlackSubjectParser();

    @Test
    void parsesMessageSubject() {
        EventTypeKey key = parser.parse("slack.T1.C1.message");

        assertThat(key).isEqualTo(new EventTypeKey(IntegrationKind.SLACK, "message"));
    }

    @Test
    void rejoinsTailComponentsWhenEventHasDots() {
        // A subtyped event token carries a dot; the join-loop must preserve every tail component.
        EventTypeKey key = parser.parse("slack.T1.C1.message.changed");

        assertThat(key).isEqualTo(new EventTypeKey(IntegrationKind.SLACK, "message.changed"));
    }

    @Test
    void blankOrNullSubjectThrows() {
        assertThatThrownBy(() -> parser.parse(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
        assertThatThrownBy(() -> parser.parse("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void wrongPrefixThrows() {
        assertThatThrownBy(() -> parser.parse("github.a.b.c"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("slack.");
    }

    @Test
    void tooFewComponentsThrows() {
        assertThatThrownBy(() -> parser.parse("slack.T1.C1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(">= 4");
    }

    @Test
    void blankEventSegmentThrows() {
        assertThatThrownBy(() -> parser.parse("slack.T1.C1."))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("event segment must not be blank");
    }

    @Test
    void parserIdentifiesAsSlackKind() {
        assertThat(parser.kind()).isEqualTo(IntegrationKind.SLACK);
    }
}
