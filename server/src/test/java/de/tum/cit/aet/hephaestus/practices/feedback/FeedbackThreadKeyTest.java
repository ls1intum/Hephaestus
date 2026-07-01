package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The thread key decides supersede-vs-new on re-review: two deliveries that share a key edit one comment
 * in place; two that differ post a fresh one. So determinism (same destination → same key) and
 * collision-sensitivity (any destination axis change → a different key) are correctness, not cosmetics.
 * These cases lock the key's grain — {@code (artifact, recipient, surface)} — and pin a golden vector so
 * the wire identity can never drift silently. Mirrors {@code ObservationFingerprintTest}.
 */
class FeedbackThreadKeyTest extends BaseUnitTest {

    private static final String TYPE = "PULL_REQUEST";

    @Test
    @DisplayName("identical destination → identical 64-char key (deterministic across runs)")
    void deterministic() {
        String a = FeedbackThreadKey.compute(TYPE, 42L, 7L, FeedbackChannel.IN_CONTEXT);
        String b = FeedbackThreadKey.compute(TYPE, 42L, 7L, FeedbackChannel.IN_CONTEXT);
        assertThat(a).isEqualTo(b).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("every destination axis discriminates: artifact, recipient, surface")
    void axesDiscriminate() {
        String base = FeedbackThreadKey.compute(TYPE, 42L, 7L, FeedbackChannel.IN_CONTEXT);
        assertThat(FeedbackThreadKey.compute("ISSUE", 42L, 7L, FeedbackChannel.IN_CONTEXT))
            .as("a different artifact type is a different thread")
            .isNotEqualTo(base);
        assertThat(FeedbackThreadKey.compute(TYPE, 99L, 7L, FeedbackChannel.IN_CONTEXT))
            .as("a different artifact id is a different thread")
            .isNotEqualTo(base);
        assertThat(FeedbackThreadKey.compute(TYPE, 42L, 8L, FeedbackChannel.IN_CONTEXT))
            .as("a different recipient is a different thread (two authors never collapse)")
            .isNotEqualTo(base);
        assertThat(FeedbackThreadKey.compute(TYPE, 42L, 7L, FeedbackChannel.CONVERSATION))
            .as("a different surface is a different thread (in-context vs conversation)")
            .isNotEqualTo(base);
    }

    @Test
    @DisplayName("a non-artifact-anchored unit (null artifact) is stable and keyed by recipient + surface")
    void nullArtifactStable() {
        String n1 = FeedbackThreadKey.compute("", null, 7L, FeedbackChannel.PROFILE);
        String n2 = FeedbackThreadKey.compute("", null, 7L, FeedbackChannel.PROFILE);
        assertThat(n1).isEqualTo(n2).hasSize(64);
        assertThat(n1)
            .as("a profile digest is distinct from an in-context unit")
            .isNotEqualTo(FeedbackThreadKey.compute("", null, 7L, FeedbackChannel.IN_CONTEXT));
    }

    @Test
    @DisplayName("golden vectors: the canonical digest is pinned so supersession identity never drifts")
    void goldenVectors() {
        assertThat(FeedbackThreadKey.compute(TYPE, 42L, 7L, FeedbackChannel.IN_CONTEXT)).isEqualTo(
            "8ab5ba3a2c707cfe8ab0afa57bb0e2a778d09001916be9c8d6c3dda36c7d83d2"
        );
        assertThat(FeedbackThreadKey.compute("", null, 7L, FeedbackChannel.PROFILE)).isEqualTo(
            "920ac864efe41848722be5c5ee7f8a7785002c673aed740fd2674452b13b5460"
        );
    }
}
