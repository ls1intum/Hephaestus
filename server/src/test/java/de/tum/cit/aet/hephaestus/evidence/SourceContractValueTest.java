package de.tum.cit.aet.hephaestus.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class SourceContractValueTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void shouldSerializeSourceIdentifiersAsStrings() throws Exception {
        SourceKind kind = new SourceKind("scm.pull-request.diff");

        String json = objectMapper.writeValueAsString(kind);

        assertThat(json).isEqualTo("\"scm.pull-request.diff\"");
        assertThat(objectMapper.readValue(json, SourceKind.class)).isEqualTo(kind);
    }

    @Test
    void shouldRejectInvalidIdentifiersAndVersions() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SourceKind("SCM_DIFF"));
        assertThatIllegalArgumentException().isThrownBy(() -> new EvidenceProfileId("Pull Request"));
        assertThatIllegalArgumentException().isThrownBy(() -> new SourceContractVersion("1"));
    }

    @Test
    void shouldRoundTripCaptureFactsWithUnknownWatermark() throws Exception {
        Instant capturedAt = Instant.parse("2026-08-03T10:00:00Z");
        SourceCaptureFacts unknownWatermark = new SourceCaptureFacts(
            capturedAt,
            null,
            null,
            null,
            "bounded database projection",
            CompletenessBasis.BOUNDED_SCOPE,
            RepresentationFidelity.LOSSY_DERIVATION
        );
        SourceCaptureFacts observedAtCapture = new SourceCaptureFacts(
            capturedAt,
            null,
            capturedAt,
            null,
            "bounded database projection",
            CompletenessBasis.BOUNDED_SCOPE,
            RepresentationFidelity.LOSSY_DERIVATION
        );

        String json = objectMapper.writeValueAsString(unknownWatermark);

        assertThat(objectMapper.readValue(json, SourceCaptureFacts.class)).isEqualTo(unknownWatermark);
        assertThat(json).contains("\"sourceEffectiveAt\":null", "\"observedAt\":null", "\"immutableIdentity\":null");
        assertThat(unknownWatermark).isNotEqualTo(observedAtCapture);
    }

    @Test
    void shouldForbidArtifactsOnUnavailableCapture() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new SourceCapture(
                new SourceKind("scm.pull-request.diff"),
                new SourceCaptureState.Unavailable("PINNED_HEAD_MISSING"),
                List.of(new SourceArtifact("inputs/context/diff.patch", "text/x-diff", "a".repeat(64), 1))
            )
        );
    }

    @Test
    void shouldRejectLegacyContextIndexesAsArtifactSourceManifests() {
        String legacy = """
            {"schemaVersion":1,"entries":[]}
            """;

        assertThatThrownBy(() -> objectMapper.readValue(legacy, ArtifactSourceManifest.class)).isInstanceOf(
            RuntimeException.class
        );
    }
}
