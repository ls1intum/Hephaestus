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
                new SourceCaptureState.Unavailable(SourceAbsenceReason.PINNED_HEAD_MISSING),
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

    @Test
    void shouldRejectEmptyManifestAndReadinessCollections() {
        Instant now = Instant.parse("2026-08-03T10:00:00Z");

        assertThatIllegalArgumentException().isThrownBy(() ->
            new ArtifactSourceManifest(
                new SourceContractVersion("1.0.0"),
                "a".repeat(64),
                new EvidenceProfileId("pull-request-review"),
                now,
                List.of(),
                List.of()
            )
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
            new AutomatedReviewReadinessDecision("review-quality", now, true, List.of(), List.of())
        );
        assertThat(
            new AutomatedReviewReadinessDecision(
                "no-automated-review",
                now,
                false,
                List.of(AutomatedReviewReadinessReason.NO_AUTOMATED_REVIEW),
                List.of()
            ).sourceChecks()
        ).isEmpty();
    }

    @Test
    void shouldEnforceAssessmentReasonCodeSemantics() {
        Instant now = Instant.parse("2026-08-03T10:00:00Z");
        SourceKind kind = new SourceKind("scm.pull-request.diff");
        SourceContractVersion version = new SourceContractVersion("1.0.0");

        assertThatIllegalArgumentException().isThrownBy(() ->
            new SourceReadinessCheck(
                kind,
                version,
                now,
                now,
                SourceFreshness.CURRENT,
                true,
                List.of(SourceReadinessReason.SOURCE_NOT_CURRENT)
            )
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
            new SourceReadinessCheck(
                kind,
                version,
                now,
                now,
                SourceFreshness.STALE,
                false,
                List.of(SourceReadinessReason.SOURCE_NOT_CURRENT, SourceReadinessReason.SOURCE_NOT_CURRENT)
            )
        );
    }

    @Test
    void shouldRejectNonCanonicalArtifactPaths() {
        for (String path : List.of("foo/..", "foo/../bar", "./foo", "foo\\bar")) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                new SourceArtifact(path, "application/json", "a".repeat(64), 1)
            );
        }
    }

    @Test
    void shouldRejectDuplicateArtifactPathsInACapture() {
        SourceArtifact first = new SourceArtifact("context.json", "application/json", "a".repeat(64), 1);
        SourceArtifact second = new SourceArtifact("context.json", "application/json", "b".repeat(64), 2);

        assertThatIllegalArgumentException().isThrownBy(() ->
            new SourceCapture(
                new SourceKind("scm.pull-request.core"),
                new SourceCaptureState.Available(
                    SourceContentState.NON_EMPTY,
                    SourceCompleteness.COMPLETE,
                    new SourceCaptureFacts(
                        Instant.parse("2026-08-03T10:00:00Z"),
                        null,
                        null,
                        null,
                        "one pull request",
                        CompletenessBasis.IMMUTABLE_OBJECT,
                        RepresentationFidelity.EXACT
                    )
                ),
                List.of(first, second)
            )
        );
    }
}
