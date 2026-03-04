package de.tum.in.www1.hephaestus.gitprovider.common;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LabelIdUtils")
class LabelIdUtilsTest extends BaseUnitTest {

    @Test
    @DisplayName("generates negative IDs to avoid collision with real provider IDs")
    void shouldGenerateNegativeIds() {
        long id = LabelIdUtils.generateDeterministicId(100L, "bug");
        assertThat(id).isNegative();
    }

    @Test
    @DisplayName("generates consistent IDs for the same input")
    void shouldGenerateConsistentIds() {
        long id1 = LabelIdUtils.generateDeterministicId(42L, "enhancement");
        long id2 = LabelIdUtils.generateDeterministicId(42L, "enhancement");
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    @DisplayName("generates different IDs for different label names")
    void shouldGenerateDifferentIdsForDifferentNames() {
        long id1 = LabelIdUtils.generateDeterministicId(42L, "bug");
        long id2 = LabelIdUtils.generateDeterministicId(42L, "enhancement");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("generates different IDs for different repositories")
    void shouldGenerateDifferentIdsForDifferentRepos() {
        long id1 = LabelIdUtils.generateDeterministicId(1L, "bug");
        long id2 = LabelIdUtils.generateDeterministicId(2L, "bug");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("separates repository ID in upper bits and label hash in lower bits")
    void shouldUseBitShiftingSeparation() {
        // repo=2, milestone=31 and repo=3, milestone=0 should NOT collide
        // (they would with simple multiplication: 2*31 + 31 = 93, 3*31 + 0 = 93)
        long id1 = LabelIdUtils.generateDeterministicId(2L, "label31");
        long id2 = LabelIdUtils.generateDeterministicId(3L, "label0");
        assertThat(id1).isNotEqualTo(id2);
    }
}
