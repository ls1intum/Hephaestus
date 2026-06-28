package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The research question ("do developers' practices change over time?") is unanswerable unless the SAME
 * underlying finding gets the SAME identity across separate detection runs. A live two-run E2E showed a
 * title-anchored key never correlated (0/26 shared) because the LLM re-words every run — so identity is
 * the {@code (practice, artifact, subject, file)} LOCUS, not the prose. These cases lock that grain and
 * what must NEVER perturb it.
 */
class ObservationFingerprintTest extends BaseUnitTest {

    private static final String SLUG = "ships-tests-with-the-change";
    private static final String TYPE = "PULL_REQUEST";

    @Test
    @DisplayName("identical locus → identical 64-char key (deterministic across runs)")
    void deterministic() {
        String a = ObservationFingerprint.compute(SLUG, TYPE, 42L, 7L, "Foo.swift");
        String b = ObservationFingerprint.compute(SLUG, TYPE, 42L, 7L, "Foo.swift");
        assertThat(a).isEqualTo(b).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("the file PATH is part of identity; a different file is a different finding")
    void pathParticipates() {
        String foo = ObservationFingerprint.compute(SLUG, TYPE, 42L, 7L, "Foo.swift");
        assertThat(ObservationFingerprint.compute(SLUG, TYPE, 42L, 7L, "Bar.swift")).isNotEqualTo(foo);
    }

    @Test
    @DisplayName("a different practice / target / subject is a different finding")
    void discriminators() {
        String base = ObservationFingerprint.compute(SLUG, TYPE, 42L, 7L, "F.swift");
        assertThat(ObservationFingerprint.compute("other-practice", TYPE, 42L, 7L, "F.swift")).isNotEqualTo(base);
        assertThat(ObservationFingerprint.compute(SLUG, "ISSUE", 42L, 7L, "F.swift")).isNotEqualTo(base);
        assertThat(ObservationFingerprint.compute(SLUG, TYPE, 99L, 7L, "F.swift")).isNotEqualTo(base);
        assertThat(ObservationFingerprint.compute(SLUG, TYPE, 42L, 8L, "F.swift")).isNotEqualTo(base);
    }

    @Test
    @DisplayName("a metadata practice (null path) correlates by (practice, target, subject) and is stable")
    void nullPathStable() {
        String n1 = ObservationFingerprint.compute("mr-description-quality", TYPE, 42L, 7L, null);
        String n2 = ObservationFingerprint.compute("mr-description-quality", TYPE, 42L, 7L, null);
        assertThat(n1).isEqualTo(n2).hasSize(64);
        assertThat(n1).isNotEqualTo(ObservationFingerprint.compute("mr-description-quality", TYPE, 42L, 7L, "F.swift"));
    }

    @Test
    @DisplayName("anchor normalization: surrounding whitespace + casing do not split identity")
    void pathNormalizationEquivalence() {
        // The anchor is locale-fixed lower-cased and whitespace-collapsed, so trivial path
        // casing/spacing differences across runs must NOT produce a different recurrence key.
        String canonical = ObservationFingerprint.compute(SLUG, TYPE, 42L, 7L, "src/foo.swift");
        assertThat(ObservationFingerprint.compute(SLUG, TYPE, 42L, 7L, "  SRC/Foo.swift  "))
            .as("leading/trailing whitespace + upper-case folds to the same key")
            .isEqualTo(canonical);
        assertThat(ObservationFingerprint.compute(SLUG, TYPE, 42L, 7L, "src/foo.swift\t"))
            .as("internal/trailing whitespace collapses to the same key")
            .isEqualTo(canonical);
    }

    @Test
    @DisplayName("required args fail fast at the guard, not deep in the digest builder")
    void requiredArgsFailFast() {
        assertThatThrownBy(() -> ObservationFingerprint.compute(null, TYPE, 1L, 1L, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("practiceSlug");
        assertThatThrownBy(() -> ObservationFingerprint.compute(SLUG, null, 1L, 1L, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("artifactType");
    }

    @Test
    @DisplayName("golden vector: the canonical digest is pinned so the wire identity never drifts silently")
    void goldenVector() {
        // A change to the field set, separator, normalization, or hash algorithm would silently
        // re-identify EVERY historical finding (breaking cross-run supersession). Pin one vector so
        // such a change must be a deliberate, reviewed edit to this expectation.
        assertThat(ObservationFingerprint.compute(SLUG, TYPE, 42L, 7L, "Foo.swift")).isEqualTo(
            "90419eec6d267f4442ca1e0fd1c8afc9658eaa003ab1dacb7af1d0f68c4809d9"
        );
    }
}
