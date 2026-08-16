package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The producer and the reader of a conversational brief must not be able to disagree about it, and no
 * other producer's body may ever be mistaken for one.
 */
class ConversationBriefBodyTest extends BaseUnitTest {

    private static final String TITLE = "The test arrives after the review";
    private static final String OPENER = "At what point do you decide the test is done?";
    private static final String EVIDENCE = "On !18, !20 and !22 the test arrived a push later.\n\nNo test was wrong.";
    private static final String TARGET = "They name a check they could run before pushing.";

    @Test
    @DisplayName("every part survives the round trip, newlines and all")
    void roundTrips() {
        ConversationBriefBody.Brief brief = ConversationBriefBody.parse(
            ConversationBriefBody.render(TITLE, OPENER, EVIDENCE, TARGET)
        );

        assertThat(brief).isNotNull();
        assertThat(brief.title()).isEqualTo(TITLE);
        assertThat(brief.opener()).isEqualTo(OPENER);
        assertThat(brief.evidence()).isEqualTo(EVIDENCE);
        assertThat(brief.target()).isEqualTo(TARGET);
    }

    /**
     * A body some other producer wrote reads back as absent rather than as a half-parsed brief. That is the
     * whole guarantee the generic body readers rest on: a caller that guessed would either read a coaching
     * plan as words somebody was told, or tell a developer what was only ever meant for the mentor.
     */
    @ParameterizedTest
    @DisplayName("nothing this class did not write parses as a brief")
    @ValueSource(
        strings = {
            "### You keep shipping untested changes\n\nthe process-level message",
            "plain prose that happens to mention opener and evidence and target",
            "{",
            "{\"kind\":\"conversation-brief\",\"version\":1,\"title\":\"t\",\"opener\":\"o\"}",
            "{\"kind\":\"in-app\",\"version\":1,\"title\":\"t\",\"opener\":\"o\",\"evidence\":\"e\",\"target\":\"g\"}",
            "{\"kind\":\"conversation-brief\",\"version\":2,\"title\":\"t\",\"opener\":\"o\",\"evidence\":\"e\",\"target\":\"g\"}",
            "  ",
        }
    )
    void refusesAnythingElse(String body) {
        assertThat(ConversationBriefBody.parse(body)).isNull();
        assertThat(ConversationBriefBody.isBrief(body)).isFalse();
    }

    @Test
    @DisplayName("a NULL body is not a brief")
    void nullIsNotABrief() {
        assertThat(ConversationBriefBody.parse(null)).isNull();
        assertThat(ConversationBriefBody.isBrief(null)).isFalse();
    }

    /** A brief is never rendered to a person, so it must not be mistaken for the in-app layout either. */
    @Test
    @DisplayName("a brief carries no in-app headline")
    void isNotAnInAppBody() {
        String body = ConversationBriefBody.render(TITLE, OPENER, EVIDENCE, TARGET);

        assertThat(InAppFeedbackBody.headlineOf(body)).isNull();
        assertThat(ConversationBriefBody.isBrief(body)).isTrue();
    }
}
