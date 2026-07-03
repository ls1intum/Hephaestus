package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.slack.events.SlackSafetyClassifier.Category;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackSafetyClassifier.Verdict;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

/**
 * Duty-of-care heuristic tests: unambiguous self-harm and harassment cues divert with a canned response, while
 * an ordinary coding question (even one mentioning "kill" in a technical sense) is answered normally.
 */
class HeuristicSlackSafetyClassifierTest extends BaseUnitTest {

    private final HeuristicSlackSafetyClassifier classifier = new HeuristicSlackSafetyClassifier();

    @Test
    void selfHarmCue_isDivertedWithSupportResponse() {
        Verdict verdict = classifier.classify("honestly I want to die");

        assertThat(verdict.category()).isEqualTo(Category.SELF_HARM);
        assertThat(verdict.safeToMentor()).isFalse();
        assertThat(verdict.cannedResponse()).isEqualTo(HeuristicSlackSafetyClassifier.SELF_HARM_RESPONSE);
    }

    @Test
    void harassmentCue_isDeclined() {
        Verdict verdict = classifier.classify("kys stupid bot");

        assertThat(verdict.category()).isEqualTo(Category.HARASSMENT);
        assertThat(verdict.cannedResponse()).isEqualTo(HeuristicSlackSafetyClassifier.HARASSMENT_RESPONSE);
    }

    @Test
    void ordinaryCodingQuestion_isSafeToMentor() {
        Verdict verdict = classifier.classify("how do I kill a zombie process in my test harness?");

        assertThat(verdict.category()).isEqualTo(Category.OK);
        assertThat(verdict.safeToMentor()).isTrue();
        assertThat(verdict.cannedResponse()).isNull();
    }

    @Test
    void blankInput_isOk() {
        assertThat(classifier.classify("  ").category()).isEqualTo(Category.OK);
    }
}
