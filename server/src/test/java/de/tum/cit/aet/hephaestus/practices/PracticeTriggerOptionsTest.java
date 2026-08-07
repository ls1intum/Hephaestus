package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;
import de.tum.cit.aet.hephaestus.practices.PracticeTriggerOptions.TriggerEventOption;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What an author may start a review on, now that the answer is derived from the domains rather than
 * written down in this module.
 */
class PracticeTriggerOptionsTest extends BaseUnitTest {

    private final PracticeTriggerOptions options = PracticeTriggerOptionsFixture.real();

    @Test
    @DisplayName("a kind offers exactly the signals its descriptor declares an authoring literal for")
    void offersWhatTheDomainDeclares() {
        assertThat(options.optionsFor(ArtifactKinds.PULL_REQUEST))
            .extracting(TriggerEventOption::event)
            .containsExactly(
                TriggerEventNames.PULL_REQUEST_CREATED,
                TriggerEventNames.PULL_REQUEST_READY,
                TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
                TriggerEventNames.REVIEW_SUBMITTED,
                TriggerEventNames.PULL_REQUEST_MERGED,
                TriggerEventNames.PULL_REQUEST_CLOSED
            );
        assertThat(options.optionsFor(ArtifactKinds.ISSUE))
            .extracting(TriggerEventOption::event)
            .containsExactly(
                TriggerEventNames.ISSUE_CREATED,
                TriggerEventNames.ISSUE_LABELED,
                TriggerEventNames.ISSUE_CLOSED
            );
    }

    @Test
    @DisplayName("a signal nobody can raise by event is not offered as a trigger")
    void leavesOutWhatNoEventRaises() {
        // scm.pull_request.review_requested is a person asking by hand: declared by the descriptor,
        // given no authoring literal by the vocabulary, and therefore absent here. The old catalog had
        // no way to express that distinction and simply never listed it.
        assertThat(options.optionsFor(ArtifactKinds.PULL_REQUEST))
            .extracting(TriggerEventOption::displayName)
            .doesNotContain("Review requested by hand");
    }

    @Test
    @DisplayName("a kind no module declares offers nothing, and says so by being empty rather than by a rule")
    void offersNothingForAnUndeclaredKind() {
        // Conversation threads are reviewed on a schedule and no module contributes a descriptor for
        // them yet. The old catalog had a hand-written arm returning an empty list; here the emptiness
        // is what the absence of a descriptor means.
        assertThat(options.optionsFor(ArtifactKinds.CONVERSATION_THREAD)).isEmpty();
        assertThat(options.eligibleFor(ArtifactKinds.CONVERSATION_THREAD)).isEmpty();
    }

    @Test
    @DisplayName("the domain's recommendation reaches the authoring surface")
    void carriesTheDomainsRecommendation() {
        List<String> recommended = options
            .optionsFor(ArtifactKinds.PULL_REQUEST)
            .stream()
            .filter(TriggerEventOption::recommended)
            .map(TriggerEventOption::event)
            .toList();

        assertThat(recommended).containsExactly(
            TriggerEventNames.PULL_REQUEST_CREATED,
            TriggerEventNames.PULL_REQUEST_READY,
            TriggerEventNames.PULL_REQUEST_SYNCHRONIZED
        );
    }

    @Test
    @DisplayName("every offered literal is one a stored practice will validate against")
    void offeredLiteralsAreAcceptedLiterals() {
        assertThat(options.allEvents())
            .containsAll(options.eligibleFor(ArtifactKinds.PULL_REQUEST))
            .containsAll(options.eligibleFor(ArtifactKinds.ISSUE));
    }
}
