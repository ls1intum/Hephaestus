package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.slack.events.SlackSafetyClassifier.Category;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackSafetyClassifier.Verdict;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

/**
 * Obvious-abuse fast-path tests: the few unambiguous self-harm and harassment cues divert with a canned response,
 * while an ordinary coding question (even one mentioning "kill" in a technical sense) is answered normally. These
 * pin the narrow, deliberate scope of the matcher — they are NOT a claim that it detects crisis or self-harm in
 * general (paraphrase and non-English phrasing pass straight through, by construction).
 */
class ObviousAbuseFastPathSlackSafetyClassifierTest extends BaseUnitTest {

    private final ObviousAbuseFastPathSlackSafetyClassifier classifier =
        new ObviousAbuseFastPathSlackSafetyClassifier();

    @Test
    void selfHarmCue_isDivertedWithSupportResponse() {
        Verdict verdict = classifier.classify("honestly I want to die");

        assertThat(verdict.category()).isEqualTo(Category.SELF_HARM);
        assertThat(verdict.safeToMentor()).isFalse();
        assertThat(verdict.cannedResponse()).isEqualTo(ObviousAbuseFastPathSlackSafetyClassifier.SELF_HARM_RESPONSE);
    }

    @Test
    void harassmentCue_isDeclined() {
        Verdict verdict = classifier.classify("kys stupid bot");

        assertThat(verdict.category()).isEqualTo(Category.HARASSMENT);
        assertThat(verdict.cannedResponse()).isEqualTo(ObviousAbuseFastPathSlackSafetyClassifier.HARASSMENT_RESPONSE);
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

    @Test
    void paraphrasedDistress_isNotCaught_documentingTheKnownLimit() {
        // By design the fast-path is a substring matcher, not crisis detection: a paraphrase with no listed cue is
        // NOT diverted (it is mentored normally). This test documents that limit honestly rather than implying the
        // matcher provides real safety coverage — genuine detection needs a model-backed classifier via the seam.
        Verdict verdict = classifier.classify("I just don't want to be here anymore");

        assertThat(verdict.category()).isEqualTo(Category.OK);
        assertThat(verdict.safeToMentor()).isTrue();
    }
}
