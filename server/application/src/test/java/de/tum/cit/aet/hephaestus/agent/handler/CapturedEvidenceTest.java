package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.context.FolderSource;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.evidence.CapturedSource;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureFacts;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CapturedEvidenceTest extends BaseUnitTest {
    private final JsonMapper mapper = new JsonMapper();
    private static final SourceKind CONTENT = new SourceKind("docs.document.content");
    private static final SourceKind COMMENTS = new SourceKind("docs.document.comments");

    @Test
    void readsOnlyAvailableSourcesAndTheirExactFileProof() {
        Instant now = Instant.parse("2026-10-14T00:00:00Z");
        var manifest = new JobFolderIndex(
                SourceCatalogRegistry.CURRENT_VERSION,
                "0".repeat(64),
                "docs.document",
                now,
                List.of(
                        new SourceCapture(
                                CONTENT,
                                new SourceCaptureState.Available(
                                        SourceContentState.NON_EMPTY,
                                        SourceCompleteness.COMPLETE,
                                        new SourceCaptureFacts(now, null, null, "opaque:version"),
                                        List.of()),
                                List.of(new CapturedSource("context/document.md", "text/plain", "a".repeat(64), 8))),
                        new SourceCapture(
                                COMMENTS,
                                new SourceCaptureState.Unavailable(SourceAbsenceReason.NOT_FOUND),
                                List.of())),
                List.of(),
                (List.of(
                                new SourceCapture(
                                        CONTENT,
                                        new SourceCaptureState.Available(
                                                SourceContentState.NON_EMPTY,
                                                SourceCompleteness.COMPLETE,
                                                new SourceCaptureFacts(now, null, null, "opaque:version"),
                                                List.of()),
                                        List.of(new CapturedSource(
                                                "context/document.md", "text/plain", "a".repeat(64), 8))),
                                new SourceCapture(
                                        COMMENTS,
                                        new SourceCaptureState.Unavailable(SourceAbsenceReason.NOT_FOUND),
                                        List.of())))
                        .stream()
                                .flatMap(captureSource -> captureSource.files().stream()
                                        .map(captureFile -> new FolderSource(captureSource.kind(), captureFile)))
                                .toList(),
                List.of());
        var capture = mapper.createObjectNode();
        capture.set("manifest", mapper.valueToTree(manifest));
        var job = new ReviewJob();
        job.setCapture(capture);
        var evidence = CapturedEvidence.of(job, mapper);
        assertThat(evidence.contractVersion()).isEqualTo(SourceCatalogRegistry.CURRENT_VERSION);
        assertThat(evidence.availableSources()).containsExactly(CONTENT);
        assertThat(evidence.requireFile(CONTENT, "context/document.md"))
                .isEqualTo(new CapturedEvidence.FileProof(CONTENT, "a".repeat(64)));
        assertThatThrownBy(() -> evidence.requireFile(COMMENTS, "context/document.md"))
                .isInstanceOf(JobDeliveryException.class);
        assertThatThrownBy(() -> evidence.requireFile(CONTENT, "context/missing.md"))
                .isInstanceOf(JobDeliveryException.class);
    }

    @Test
    void refusesAnAttemptWithoutItsCapturedManifest() {
        assertThatThrownBy(() -> CapturedEvidence.of(new ReviewJob(), mapper)).isInstanceOf(JobDeliveryException.class);
    }
}
