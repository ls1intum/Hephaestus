package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.agent.conversation.ChatSignals;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalCoverage;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptionsFixture;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The boot-time half of the coverage question: an authoring option that can never fire must stop the
 * application, because it is a mistake in the build rather than a fact about a deployment.
 *
 * <p>Checked against the offered vocabulary rather than against stored practices on purpose. What an
 * author is offered is identical on every instance, so a gap fails here and in CI instead of on
 * whichever installation first happened to pick the broken option.
 */
class PracticeSignalCoverageTest extends BaseUnitTest {

    private final PracticeRepository practices = mock(PracticeRepository.class);
    private final PracticeSignalOptions options = PracticeSignalOptionsFixture.real();

    @Test
    @DisplayName("every signal an author can bind to is one the shipped integrations declare they raise")
    void everySignalAnAuthorCanPickIsBackedByTheShippedIntegrations() {
        assertThatCode(coverage(everyIngestedSignal())::validateAuthoringVocabulary).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a signal no integration can raise refuses to boot")
    void aSignalNoIntegrationCanRaiseRefusesToBoot() {
        Set<SignalName> covered = everyIngestedSignal();
        covered.remove(ScmSignals.PULL_REQUEST_SYNCHRONIZED);

        assertThatThrownBy(coverage(covered)::validateAuthoringVocabulary)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("scm.pull_request.synchronized")
            .hasMessageContaining("would never fire");
    }

    @Test
    @DisplayName("a signal raised from inside Hephaestus is not held to integration coverage")
    void aSignalNoIngestedEventCarriesIsNotAGap() {
        // A settled conversation and a review somebody asked for by hand are raised by a scheduler and
        // by a person. Demanding an integration behind them would fail the boot for telling the truth,
        // which is the failure mode this check exists to prevent, inverted.
        Set<SignalName> covered = everyIngestedSignal();
        assertThat(covered).doesNotContain(
            ChatSignals.CONVERSATION_THREAD_SETTLED,
            ScmSignals.PULL_REQUEST_REVIEW_REQUESTED
        );

        assertThatCode(coverage(covered)::validateAuthoringVocabulary).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the shipped vocabulary is not empty")
    void theShippedVocabularyIsNotEmpty() {
        // Guards the tests above from passing vacuously if the descriptors are ever emptied.
        assertThat(options.eligibleFor(ScmSignals.PULL_REQUEST)).isNotEmpty();
    }

    private PracticeSignalCoverage coverage(Set<SignalName> covered) {
        return new PracticeSignalCoverage(fixedCoverage(covered), options, practices);
    }

    /** Every signal an author can bind to that some ingested event is declared to raise. */
    private Set<SignalName> everyIngestedSignal() {
        Set<SignalName> signals = new HashSet<>();
        for (ArtifactKind kind : options.authorableKinds()) {
            options.eligibleFor(kind).stream().filter(options::producedByIngestion).forEach(signals::add);
        }
        return signals;
    }

    private static SignalCoverage fixedCoverage(Set<SignalName> covered) {
        return new SignalCoverage() {
            @Override
            public Set<SignalName> compiledCoverage() {
                return covered;
            }

            @Override
            public Set<SignalName> connectedCoverage(long workspaceId) {
                return covered;
            }

            @Override
            public Set<IntegrationKind> raisedBy(SignalName signal) {
                return covered.contains(signal) ? Set.of(IntegrationKind.GITHUB) : Set.of();
            }
        };
    }
}
