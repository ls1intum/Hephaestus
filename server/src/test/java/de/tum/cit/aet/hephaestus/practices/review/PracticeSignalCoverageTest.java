package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalCoverage;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalVocabulary;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignalVocabulary;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTriggerOptions;
import de.tum.cit.aet.hephaestus.practices.PracticeTriggerOptionsFixture;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    @Test
    void everyTriggerAnAuthorCanPickIsBackedByTheShippedIntegrations() {
        // The real vocabulary against a coverage that mirrors what GitHub and GitLab actually declare.
        PracticeSignalCoverage coverage = coverage(everySignalOfTheRealCatalog());

        assertThatCode(coverage::validateAuthoringVocabulary).doesNotThrowAnyException();
    }

    @Test
    void aTriggerNoIntegrationCanRaiseRefusesToBoot() {
        Set<SignalName> covered = everySignalOfTheRealCatalog();
        covered.remove(ScmSignals.PULL_REQUEST_SYNCHRONIZED);
        PracticeSignalCoverage coverage = coverage(covered);

        assertThatThrownBy(coverage::validateAuthoringVocabulary)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PullRequestSynchronized")
            .hasMessageContaining("scm.pull_request.synchronized")
            .hasMessageContaining("would never fire");
    }

    @Test
    void aTriggerNoDomainTranslatesRefusesToBoot() {
        // A vocabulary that offers a literal it cannot itself translate is the same failure as a missing
        // producer: the option exists in the UI and resolves to nothing.
        SignalVocabulary inconsistent = inconsistentVocabulary();
        PracticeSignalCoverage coverage = new PracticeSignalCoverage(
            fixedCoverage(everySignalOfTheRealCatalog()),
            List.of(inconsistent),
            triggerOptions(inconsistent),
            practices
        );

        assertThatThrownBy(coverage::validateAuthoringVocabulary)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no domain module translates it to a signal");
    }

    @Test
    void theShippedVocabularyIsNotEmpty() {
        // Guards the tests above from passing vacuously if the vocabulary is ever emptied.
        assertThat(triggerOptions(new ScmSignalVocabulary()).allEvents()).isNotEmpty();
    }

    private PracticeSignalCoverage coverage(Set<SignalName> covered) {
        ScmSignalVocabulary vocabulary = new ScmSignalVocabulary();
        return new PracticeSignalCoverage(
            fixedCoverage(covered),
            List.of(vocabulary),
            triggerOptions(vocabulary),
            practices
        );
    }

    /** Trigger options over the real SCM descriptors, which is what an author is actually offered. */
    private static PracticeTriggerOptions triggerOptions(SignalVocabulary vocabulary) {
        return PracticeTriggerOptionsFixture.with(vocabulary);
    }

    /** The signals the shipped manifests between them declare they raise, read off the real vocabulary. */
    private static Set<SignalName> everySignalOfTheRealCatalog() {
        Set<SignalName> signals = new HashSet<>();
        for (String triggerEvent : new ScmSignalVocabulary().triggerEventNames()) {
            ScmSignals.forTriggerEvent(triggerEvent).ifPresent(signals::add);
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

    private static SignalVocabulary inconsistentVocabulary() {
        return new SignalVocabulary() {
            @Override
            public Optional<SignalName> signalForTriggerEvent(String triggerEventName) {
                return Optional.empty();
            }

            @Override
            public Set<String> triggerEventNames() {
                return Set.of("PullRequestReady");
            }

            @Override
            public Optional<String> triggerEventFor(SignalName signal) {
                return Optional.empty();
            }
        };
    }
}
