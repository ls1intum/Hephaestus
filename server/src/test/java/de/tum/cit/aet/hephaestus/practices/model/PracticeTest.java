package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class PracticeTest extends BaseUnitTest {

    /** {@code artifactKind} is a projection of the bindings, so setting bindings must re-derive it. */
    @Test
    void settingBindingsIsTheOnlyThingThatDecidesTheArtifactKind() {
        Practice practice = new Practice();
        assertThat(practice.getArtifactKind()).isEqualTo(ArtifactKinds.PULL_REQUEST);

        practice.setBindings(PracticeTestEvidence.bindings(ArtifactKinds.CONVERSATION_THREAD));
        assertThat(practice.getArtifactKind()).isEqualTo(ArtifactKinds.CONVERSATION_THREAD);

        practice.setBindings(PracticeTestEvidence.bindings(ArtifactKinds.ISSUE));
        assertThat(practice.getArtifactKind()).isEqualTo(ArtifactKinds.ISSUE);
    }

    /** Pins the {@code DEFECT-DETECTOR DISCIPLINE} marker contract in both directions, plus the null-criteria guard. */
    @Test
    void isDefectDetector_trueOnlyWhenCriteriaContainsTheMarker() {
        Practice marked = new Practice();
        marked.setCriteria("DEFECT-DETECTOR DISCIPLINE: a clean surface is NOT_APPLICABLE, never a strength.");

        Practice ordinary = new Practice();
        ordinary.setCriteria("Assess whether the PR description explains the change.");

        Practice noCriteria = new Practice();

        assertThat(marked.isDefectDetector()).isTrue();
        assertThat(ordinary.isDefectDetector()).isFalse();
        assertThat(noCriteria.isDefectDetector()).isFalse();
    }

    @Test
    void isDefectDetector_isCaseAndPunctuationSensitive_markerMatchesVerbatim() {
        Practice lowercased = new Practice();
        lowercased.setCriteria("defect-detector discipline applies here");

        Practice hyphenStripped = new Practice();
        hyphenStripped.setCriteria("DEFECT DETECTOR DISCIPLINE applies here");

        assertThat(lowercased.isDefectDetector()).isFalse();
        assertThat(hyphenStripped.isDefectDetector()).isFalse();
    }
}
