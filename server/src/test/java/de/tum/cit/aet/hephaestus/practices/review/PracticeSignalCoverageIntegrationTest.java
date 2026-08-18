package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalCoverage;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The check that "an authorable signal no shipped integration raises" cannot ship — run over the real
 * wiring, which is the only place it means anything.
 *
 * <p>The unit test beside this one supplies a {@link SignalCoverage} it constructs itself, which proves
 * the method's <em>logic</em> but nothing about the <em>fact</em> — a fake derived from the options can
 * only ever agree with them. Here both sides come from the container: the offered vocabulary from every
 * registered {@code ArtifactDescriptor} bean, the compiled coverage from what the shipped manifests
 * actually declare.
 */
class PracticeSignalCoverageIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PracticeSignalCoverage coverage;

    @Autowired
    private SignalCoverage declaredCoverage;

    @Autowired
    private PracticeSignalOptions options;

    @Test
    @DisplayName("every signal an author can bind to is one a shipped integration declares it raises")
    void theShippedVocabularyIsCoveredByTheShippedManifests() {
        assertThatCode(coverage::validateAuthoringVocabulary).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the two sides of the comparison are really independent")
    void neitherSideOfTheComparisonIsDerivedFromTheOther() {
        // Guards the test above from going vacuously true: if the manifests declared nothing, or no
        // descriptor were registered, an empty comparison would still pass.
        Set<SignalName> offered = new LinkedHashSet<>();
        for (ArtifactKind kind : options.authorableKinds()) {
            offered.addAll(options.eligibleFor(kind));
        }
        Set<SignalName> compiled = declaredCoverage.compiledCoverage();

        assertThat(offered).as("no artifact descriptor is registered").isNotEmpty();
        assertThat(compiled).as("no manifest declares it raises anything").isNotEmpty();
        assertThat(compiled).as("the manifests raise nothing an author can bind to").containsAnyElementsOf(offered);
    }
}
