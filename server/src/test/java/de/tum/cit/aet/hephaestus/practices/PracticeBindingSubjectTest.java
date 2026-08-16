package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Whose conduct an occasion judges — the fact that decides which person an observation is filed
 * against, and therefore whose reflection surface it can reach.
 */
class PracticeBindingSubjectTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    /**
     * Every binding written before roles existed is stored without the key. Reading it as anything but
     * AUTHOR would silently re-attribute the entire installed catalogue.
     */
    @Test
    void readsABindingWithNoSubjectAsBeingAboutTheAuthor() {
        PracticeBinding binding = objectMapper.readValue(
            "{\"signals\":[\"scm.pull_request.merged\"],\"needs\":[]}",
            PracticeBinding.class
        );

        assertThat(binding.subject()).isEqualTo(ActorRole.AUTHOR);
    }

    @Test
    void readsADeclaredSubject() {
        PracticeBinding binding = objectMapper.readValue(
            "{\"signals\":[\"scm.pull_request.reviewed\"],\"needs\":[],\"subject\":\"REVIEWER\"}",
            PracticeBinding.class
        );

        assertThat(binding.subject()).isEqualTo(ActorRole.REVIEWER);
    }

    @Test
    void resolvesTheSubjectOfTheOccasionThatRanTheReview() {
        PracticeBinding reviewerSide = new PracticeBinding(
            List.of(ScmSignals.PULL_REQUEST_REVIEWED),
            List.of(),
            false,
            ActorRole.REVIEWER
        );

        assertThat(PracticeBinding.subjectRoleOf(List.of(reviewerSide), ScmSignals.PULL_REQUEST_REVIEWED)).isEqualTo(
            ActorRole.REVIEWER
        );
    }

    /**
     * One signal occasions both kinds of practice — {@code scm.pull_request.reviewed} starts a practice
     * about the author's uptake AND practices about the reviewer's craft — which is why the subject
     * lives on the occasion and not on the signal.
     */
    @Test
    void answersPerOccasionRatherThanPerSignal() {
        PracticeBinding authorSide = new PracticeBinding(
            List.of(ScmSignals.PULL_REQUEST_REVIEWED),
            List.of(),
            false,
            ActorRole.AUTHOR
        );
        PracticeBinding reviewerSide = new PracticeBinding(
            List.of(ScmSignals.PULL_REQUEST_REVIEWED),
            List.of(),
            false,
            ActorRole.REVIEWER
        );

        assertThat(PracticeBinding.subjectRoleOf(List.of(authorSide), ScmSignals.PULL_REQUEST_REVIEWED)).isEqualTo(
            ActorRole.AUTHOR
        );
        assertThat(PracticeBinding.subjectRoleOf(List.of(reviewerSide), ScmSignals.PULL_REQUEST_REVIEWED)).isEqualTo(
            ActorRole.REVIEWER
        );
    }

    /**
     * Bindings that disagree about the subject resolve to the role that withholds. Reachable only for a
     * practice declaring several occasions, which the single-occasion rule makes rare — but the cost of
     * guessing wrong is showing a reviewer's conduct to the author.
     */
    @Test
    void leansToTheNonAuthorRoleWhenTwoOccasionsDisagree() {
        PracticeBinding authorSide = new PracticeBinding(
            List.of(ScmSignals.PULL_REQUEST_MERGED),
            List.of(),
            false,
            ActorRole.AUTHOR
        );
        PracticeBinding reviewerSide = new PracticeBinding(
            List.of(ScmSignals.PULL_REQUEST_REVIEWED),
            List.of(),
            false,
            ActorRole.REVIEWER
        );

        assertThat(PracticeBinding.subjectRoleOf(List.of(authorSide, reviewerSide), (SignalName) null)).isEqualTo(
            ActorRole.REVIEWER
        );
    }

    /**
     * A review asked for by hand names no occasion, so every binding applies; the answer then leans to
     * the role that withholds, because attributing a reviewer's conduct to the author is the failure
     * this fact exists to prevent.
     */
    @Test
    void leansToTheNonAuthorRoleWhenNoOccasionWasNamed() {
        PracticeBinding reviewerSide = new PracticeBinding(
            List.of(ScmSignals.PULL_REQUEST_REVIEWED),
            List.of(),
            false,
            ActorRole.REVIEWER
        );

        assertThat(PracticeBinding.subjectRoleOf(List.of(reviewerSide), (SignalName) null)).isEqualTo(
            ActorRole.REVIEWER
        );
    }

    /** An occasion the run did not fire says nothing about who this run's results are about. */
    @Test
    void ignoresAnOccasionThatDidNotStartThisReview() {
        PracticeBinding reviewerSide = new PracticeBinding(
            List.of(ScmSignals.PULL_REQUEST_REVIEWED),
            List.of(),
            false,
            ActorRole.REVIEWER
        );

        assertThat(PracticeBinding.subjectRoleOf(List.of(reviewerSide), ScmSignals.PULL_REQUEST_MERGED)).isEqualTo(
            ActorRole.AUTHOR
        );
    }
}
