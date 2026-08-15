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

    private static final String TYPE = "scm.pull_request";

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
        assertThat(FeedbackThreadKey.compute("scm.issue", 42L, 7L, FeedbackChannel.IN_CONTEXT))
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
        String n1 = FeedbackThreadKey.compute("", null, 7L, FeedbackChannel.REFLECTION);
        String n2 = FeedbackThreadKey.compute("", null, 7L, FeedbackChannel.REFLECTION);
        assertThat(n1).isEqualTo(n2).hasSize(64);
        assertThat(n1)
            .as("a reflection digest is distinct from an in-context unit")
            .isNotEqualTo(FeedbackThreadKey.compute("", null, 7L, FeedbackChannel.IN_CONTEXT));
    }

    @Test
    @DisplayName("golden vectors: the canonical digest is pinned so supersession identity never drifts")
    void goldenVectors() {
        // The key digests the artifact kind, so re-spelling a kind orphans every thread opened under the
        // old spelling. The second vector names no kind, which is what isolates that axis.
        assertThat(FeedbackThreadKey.compute(TYPE, 42L, 7L, FeedbackChannel.IN_CONTEXT)).isEqualTo(
            "a94dab8733d9b5e1ae7933969116e27e717b88f43e06ee63c23cf905d1b3fc96"
        );
        // Re-pinned when the channel was renamed PROFILE → REFLECTION. The digest takes the channel's
        // name, so the rename moved this vector — safe only because the lane had no producer until it
        // was named REFLECTION, so no thread was ever opened under the old spelling. A future rename of
        // a channel that has shipped feedback orphans every thread it opened, and needs a migration.
        assertThat(FeedbackThreadKey.compute("", null, 7L, FeedbackChannel.REFLECTION)).isEqualTo(
            "bb385db1ef94b5a7e85e4c09d3a66ea0f3c73c01d9761a04d5121b063c7450f7"
        );
    }
}
