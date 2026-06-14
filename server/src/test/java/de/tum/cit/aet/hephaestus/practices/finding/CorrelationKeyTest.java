package de.tum.cit.aet.hephaestus.practices.finding;

import static org.assertj.core.api.Assertions.assertThat;

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
class CorrelationKeyTest extends BaseUnitTest {

    private static final String SLUG = "ships-tests-with-the-change";
    private static final String TYPE = "PULL_REQUEST";

    @Test
    @DisplayName("identical locus → identical 64-char key (deterministic across runs)")
    void deterministic() {
        String a = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Foo.swift");
        String b = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Foo.swift");
        assertThat(a).isEqualTo(b).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("the title is NOT part of identity — re-wording the same concern keeps one key")
    void titleDoesNotParticipate() {
        // compute() no longer takes a title; same locus = same key regardless of how the agent phrased it.
        String run1 = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Foo.swift");
        String run2 = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Foo.swift");
        assertThat(run2).isEqualTo(run1);
    }

    @Test
    @DisplayName("the file PATH is part of identity; a different file is a different finding")
    void pathParticipates() {
        String foo = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Foo.swift");
        assertThat(CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Bar.swift")).isNotEqualTo(foo);
    }

    @Test
    @DisplayName("a different practice / target / subject is a different finding")
    void discriminators() {
        String base = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "F.swift");
        assertThat(CorrelationKey.compute("other-practice", TYPE, 42L, 7L, "F.swift")).isNotEqualTo(base);
        assertThat(CorrelationKey.compute(SLUG, "ISSUE", 42L, 7L, "F.swift")).isNotEqualTo(base);
        assertThat(CorrelationKey.compute(SLUG, TYPE, 99L, 7L, "F.swift")).isNotEqualTo(base);
        assertThat(CorrelationKey.compute(SLUG, TYPE, 42L, 8L, "F.swift")).isNotEqualTo(base);
    }

    @Test
    @DisplayName("a metadata practice (null path) correlates by (practice, target, subject) and is stable")
    void nullPathStable() {
        String n1 = CorrelationKey.compute("mr-description-quality", TYPE, 42L, 7L, null);
        String n2 = CorrelationKey.compute("mr-description-quality", TYPE, 42L, 7L, null);
        assertThat(n1).isEqualTo(n2).hasSize(64);
        assertThat(n1).isNotEqualTo(CorrelationKey.compute("mr-description-quality", TYPE, 42L, 7L, "F.swift"));
    }
}
