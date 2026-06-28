package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class PracticeTest extends BaseUnitTest {

    /**
     * {@code isDefectDetector()} is the single source of the firewall that coerces a model-emitted
     * {@code (PRESENT, GOOD)} to NOT_APPLICABLE and suppresses false "strengths" on the dashboard. It keys on the
     * verbatim {@code DEFECT-DETECTOR DISCIPLINE} marker in the criteria, so pin the contract in both directions
     * plus the null-criteria (transient-entity) guard.
     */
    @Test
    void isDefectDetector_trueOnlyWhenCriteriaContainsTheMarker() {
        Practice marked = new Practice();
        marked.setCriteria("DEFECT-DETECTOR DISCIPLINE: a clean surface is NOT_APPLICABLE, never a strength.");

        Practice ordinary = new Practice();
        ordinary.setCriteria("Assess whether the PR description explains the change.");

        Practice noCriteria = new Practice(); // criteria == null (e.g. a freshly-constructed transient entity)

        assertThat(marked.isDefectDetector()).isTrue();
        assertThat(ordinary.isDefectDetector()).isFalse();
        assertThat(noCriteria.isDefectDetector()).isFalse();
    }

    @Test
    void isDefectDetector_isCaseAndPunctuationSensitive_markerMatchesVerbatim() {
        // The marker is matched verbatim — a reformatted token (lowercased / hyphen→space) does NOT count, which
        // is exactly why the marker is documented as load-bearing and an admin edit must preserve it.
        Practice lowercased = new Practice();
        lowercased.setCriteria("defect-detector discipline applies here");

        Practice hyphenStripped = new Practice();
        hyphenStripped.setCriteria("DEFECT DETECTOR DISCIPLINE applies here");

        assertThat(lowercased.isDefectDetector()).isFalse();
        assertThat(hyphenStripped.isDefectDetector()).isFalse();
    }
}
