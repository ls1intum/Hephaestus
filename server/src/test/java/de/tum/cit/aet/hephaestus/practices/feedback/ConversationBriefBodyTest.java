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
    private static final String SITUATION = "On !18, !20 and !22 the test landed a push after the review comment.";
    private static final String COACHING_GOAL = "Writing the test last is what makes the review find the gap.";
    private static final String EVIDENCE_SUMMARY =
        "On !18, !20 and !22 the test arrived a push later.\n\nNo test was wrong.";
    private static final String SUCCESS_SIGNAL = "They name a check they could run before pushing.";

    @Test
    @DisplayName("every note survives the round trip, newlines and all")
    void roundTrips() {
        ConversationBriefBody.Brief brief = ConversationBriefBody.parse(
            ConversationBriefBody.render(TITLE, SITUATION, COACHING_GOAL, EVIDENCE_SUMMARY, SUCCESS_SIGNAL)
        );

        assertThat(brief).isNotNull();
        assertThat(brief.title()).isEqualTo(TITLE);
        assertThat(brief.situation()).isEqualTo(SITUATION);
        assertThat(brief.capability()).isEqualTo(COACHING_GOAL);
        assertThat(brief.evidenceSummary()).isEqualTo(EVIDENCE_SUMMARY);
        assertThat(brief.inConversationSignal()).isEqualTo(SUCCESS_SIGNAL);
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
            "plain prose that happens to mention situation and evidenceSummary and inConversationSignal",
            "{",
            "{\"kind\":\"conversation-brief\",\"title\":\"t\",\"situation\":\"s\"}",
            "{\"kind\":\"in-app\",\"title\":\"t\",\"situation\":\"s\",\"capability\":\"g\"," +
                "\"evidenceSummary\":\"e\",\"inConversationSignal\":\"x\"}",
            "{\"kind\":\"conversation-brief\",\"title\":\"t\",\"situation\":\"s\"," +
                "\"capability\":\"g\",\"evidenceSummary\":\"e\"}",
            "  ",
        }
    )
    void refusesAnythingElse(String body) {
        assertThat(ConversationBriefBody.parse(body)).isNull();
    }

    /**
     * The narrower question {@code isBrief} answers - "is this a plan for the mentor rather than words a
     * person read?" - must still say no to every body this class did not write. Both recognition and
     * parsing require the same complete final shape.
     */
    @ParameterizedTest
    @DisplayName("nothing another producer wrote is recognised as a brief")
    @ValueSource(
        strings = {
            "### You keep shipping untested changes\n\nthe process-level message",
            "plain prose that happens to mention situation and evidenceSummary and inConversationSignal",
            "{",
            "{\"kind\":\"in-app\",\"title\":\"t\",\"situation\":\"s\",\"capability\":\"g\"," +
                "\"evidenceSummary\":\"e\",\"inConversationSignal\":\"x\"}",
            "  ",
        }
    )
    void recognisesNoOtherProducersBody(String body) {
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
        String body = ConversationBriefBody.render(TITLE, SITUATION, COACHING_GOAL, EVIDENCE_SUMMARY, SUCCESS_SIGNAL);

        assertThat(InAppFeedbackBody.headlineOf(body)).isNull();
        assertThat(ConversationBriefBody.isBrief(body)).isTrue();
    }
}
