package de.tum.cit.aet.hephaestus.practices.finding;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The whole research question ("do developers' practices change over time?") is unanswerable unless the
 * SAME underlying finding gets the SAME identity across separate detection runs. These cases lock that
 * stability and, just as importantly, lock what must NEVER perturb it (job id, line number, re-wording).
 */
class CorrelationKeyTest extends BaseUnitTest {

    private static final String SLUG = "ships-tests-with-the-change";
    private static final String TYPE = "PULL_REQUEST";

    @Test
    @DisplayName("identical inputs → identical 64-char key (deterministic across runs)")
    void deterministic() {
        String a = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Missing test", "Foo.swift");
        String b = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Missing test", "Foo.swift");
        assertThat(a).isEqualTo(b).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("re-wording / re-spacing / case of the title does NOT change identity")
    void titleNormalised() {
        String base = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Missing  test", "Foo.swift");
        String reworded = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "MISSING test", "Foo.swift");
        assertThat(reworded).isEqualTo(base);
    }

    @Test
    @DisplayName("the file PATH is part of identity; a different file is a different finding")
    void pathParticipates() {
        String foo = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Missing test", "Foo.swift");
        String bar = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "Missing test", "Bar.swift");
        assertThat(bar).isNotEqualTo(foo);
    }

    @Test
    @DisplayName("a different practice / target / subject is a different finding")
    void discriminators() {
        String base = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "x", "F.swift");
        assertThat(CorrelationKey.compute("other-practice", TYPE, 42L, 7L, "x", "F.swift")).isNotEqualTo(base);
        assertThat(CorrelationKey.compute(SLUG, "ISSUE", 42L, 7L, "x", "F.swift")).isNotEqualTo(base);
        assertThat(CorrelationKey.compute(SLUG, TYPE, 99L, 7L, "x", "F.swift")).isNotEqualTo(base);
        assertThat(CorrelationKey.compute(SLUG, TYPE, 42L, 8L, "x", "F.swift")).isNotEqualTo(base);
    }

    @Test
    @DisplayName("a null vs present file path are distinct but each stable")
    void nullPathStable() {
        String n1 = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "no location", null);
        String n2 = CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "no location", null);
        assertThat(n1).isEqualTo(n2).hasSize(64);
        assertThat(n1).isNotEqualTo(CorrelationKey.compute(SLUG, TYPE, 42L, 7L, "no location", "F.swift"));
    }
}
