package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FrameCodecRoundTripTest extends BaseUnitTest {

    private final FrameCodec codec = new FrameCodec(new ObjectMapper());

    @Test
    void preservesAllFieldsThroughRoundTrip() {
        // CapacityReport carries six ints; if the sealed-switch + polymorphic deser is wired wrong
        // the decoded record will not equal the input. Stronger signal than instanceof-check.
        CapacityReport payload = new CapacityReport(4, 2, 1, 0, 3, 2);
        FrameEnvelope envelope = FrameEnvelope.of(payload);

        FrameEnvelope decoded = codec.decode(codec.encode(envelope));

        assertThat(decoded.version()).isEqualTo(FrameEnvelope.CURRENT_VERSION);
        assertThat(decoded.frameId()).isEqualTo(envelope.frameId());
        assertThat(decoded.payload()).isEqualTo(payload);
    }

    @Test
    void rejectsOversizedFrameOnEncode() {
        String huge = "x".repeat(FrameCodec.MAX_FRAME_BYTES);
        FrameEnvelope envelope = FrameEnvelope.of(new SessionInput("s", huge));
        assertThatThrownBy(() -> codec.encode(envelope))
            .isInstanceOf(FrameCodec.FrameCodecException.class)
            .hasMessageContaining("exceeds " + FrameCodec.MAX_FRAME_BYTES);
    }

    @Test
    void rejectsOversizedFrameOnDecode() {
        String tooLarge = "x".repeat(FrameCodec.MAX_FRAME_BYTES + 1);
        assertThatThrownBy(() -> codec.decode(tooLarge))
            .isInstanceOf(FrameCodec.FrameCodecException.class)
            .hasMessageContaining("exceeds " + FrameCodec.MAX_FRAME_BYTES);
    }

    @Test
    void capacityReportSnapshotForcesSpareZeroAndPreservesInFlight() {
        // Drain emits a CapacityReport with spare=0 so the hub removes the worker from rotation
        // before the heartbeat cadence catches up. The hub still sees the in-flight counts so
        // it can chart drain progress.
        CapacityReport drained = new CapacityReport(4, 2, 1, 0, 3, 2).withSpareForcedZero();
        assertThat(drained.spareReview()).isZero();
        assertThat(drained.spareMentor()).isZero();
        assertThat(drained.reviewMax()).isEqualTo(4);
        assertThat(drained.inFlightReview()).isEqualTo(1);
    }
}
