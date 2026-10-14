package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PracticePageFeedbackBodyTest extends BaseUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"Tests are arriving one commit late", "One word", "Punctuation: it's fine — really"})
    void roundTripsAnyHeadlineItWrote(String headline) {
        String body = PracticePageFeedbackBody.render(headline, "The message.", "Try this next.");

        assertThat(PracticePageFeedbackBody.headlineOf(body)).isEqualTo(headline);
    }

    @Test
    void shouldReadTheMessageAndTheNextStepApartWhenTheBodyIsRendered() {
        String body =
                PracticePageFeedbackBody.render("Headline", "The message.\n\nWith two paragraphs.", "Try this next.");

        assertThat(PracticePageFeedbackBody.messageOf(body)).isEqualTo("The message.\n\nWith two paragraphs.");
        assertThat(PracticePageFeedbackBody.nextStepOf(body)).isEqualTo("Try this next.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Try this next.", "Split\nit", "Write the assertion **before** the branch"})
    void shouldRoundTripTheNextStepWhenItIsRendered(String nextStep) {
        String body = PracticePageFeedbackBody.render("Headline", "The message.\n\nWith two paragraphs.", nextStep);

        assertThat(PracticePageFeedbackBody.nextStepOf(body)).isEqualTo(nextStep.replace('\n', ' '));
    }

    // A newline would let a heading consume the rest of the message.
    @Test
    void collapsesANewlineInTheHeadlineRatherThanLettingItSplitTheBody() {
        String body = PracticePageFeedbackBody.render("Two\nlines", "The message.", "Try this next.");

        assertThat(PracticePageFeedbackBody.headlineOf(body)).isEqualTo("Two lines");
        assertThat(PracticePageFeedbackBody.messageOf(body)).startsWith("The message.");
    }

    /**
     * Null rather than a guess: an in-context body was written by a different producer with no headline
     * at all, and inventing one would put words in its mouth.
     */
    @Test
    void reportsNoHeadlineForABodyItDidNotWrite() {
        assertThat(PracticePageFeedbackBody.headlineOf("A plain in-context comment body."))
                .isNull();
        assertThat(PracticePageFeedbackBody.messageOf("A plain in-context comment body."))
                .isEqualTo("A plain in-context comment body.");
        assertThat(PracticePageFeedbackBody.nextStepOf("A plain in-context comment body."))
                .isNull();
    }

    @Test
    void toleratesAnAbsentBody() {
        assertThat(PracticePageFeedbackBody.headlineOf(null)).isNull();
        assertThat(PracticePageFeedbackBody.messageOf(null)).isEmpty();
        assertThat(PracticePageFeedbackBody.nextStepOf(null)).isNull();
    }
}
