package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The producer writes this layout and the reader takes it apart, so the two must agree exactly. These
 * are the assertions that would fail if either side drifted.
 */
class ReflectionFeedbackBodyTest extends BaseUnitTest {

    @ParameterizedTest
    @ValueSource(strings = { "Tests are arriving one commit late", "One word", "Punctuation: it's fine — really" })
    void roundTripsAnyHeadlineItWrote(String headline) {
        String body = ReflectionFeedbackBody.render(headline, "The message.", "Try this next.");

        assertThat(ReflectionFeedbackBody.headlineOf(body)).isEqualTo(headline);
    }

    @Test
    void keepsTheMessageAndTheNextStepBelowTheHeadline() {
        String body = ReflectionFeedbackBody.render("Headline", "The message.", "Try this next.");

        assertThat(ReflectionFeedbackBody.messageOf(body)).isEqualTo("The message.\n\n**Try next:** Try this next.");
    }

    /** A newline in a heading would swallow the rest of the message into the title. */
    @Test
    void collapsesANewlineInTheHeadlineRatherThanLettingItSplitTheBody() {
        String body = ReflectionFeedbackBody.render("Two\nlines", "The message.", "Try this next.");

        assertThat(ReflectionFeedbackBody.headlineOf(body)).isEqualTo("Two lines");
        assertThat(ReflectionFeedbackBody.messageOf(body)).startsWith("The message.");
    }

    /**
     * Null rather than a guess: an in-context body was written by a different producer with no headline
     * at all, and inventing one would put words in its mouth.
     */
    @Test
    void reportsNoHeadlineForABodyItDidNotWrite() {
        assertThat(ReflectionFeedbackBody.headlineOf("A plain in-context comment body.")).isNull();
        assertThat(ReflectionFeedbackBody.messageOf("A plain in-context comment body.")).isEqualTo(
            "A plain in-context comment body."
        );
    }

    @Test
    void toleratesAnAbsentBody() {
        assertThat(ReflectionFeedbackBody.headlineOf(null)).isNull();
        assertThat(ReflectionFeedbackBody.messageOf(null)).isEmpty();
    }
}
