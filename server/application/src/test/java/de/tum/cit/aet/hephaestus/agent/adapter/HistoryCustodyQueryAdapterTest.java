package de.tum.cit.aet.hephaestus.agent.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.handler.EvidenceSnapshotFixtures;
import de.tum.cit.aet.hephaestus.agent.handler.RuntimeReviewTestData;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class HistoryCustodyQueryAdapterTest extends BaseUnitTest {
    private static final Instant CAPTURED_AT = Instant.parse(EvidenceSnapshotFixtures.CAPTURED_AT);
    private final JsonMapper mapper = new JsonMapper();
    private final ReviewJobRepository jobs = mock(ReviewJobRepository.class);
    private final HistoryCustodyQueryAdapter adapter =
            new HistoryCustodyQueryAdapter(mock(EvidenceFolderPersonDataCatalog.class), jobs, mapper);
    private final ReviewJob job = RuntimeReviewTestData.job();

    @BeforeEach
    void recordedCapture() {
        var capture = EvidenceSnapshotFixtures.snapshot(mapper);
        var source = EvidenceSnapshotFixtures.availableSource(capture, "scm.pull-request.diff", null);
        EvidenceSnapshotFixtures.file(capture, source, "inputs/sources/scm/diff.patch", "a".repeat(64));
        job.setCapture(capture);
    }

    @Test
    void shouldReadTheCaptureTimeOfTheReviewsOwnJobFromItsFullOrRetiredManifest() {
        when(jobs.findByIdAndWorkspaceId(job.getId(), 1L)).thenReturn(Optional.of(job));
        assertThat(adapter.capturedAt(1, RuntimeReviewTestData.REVIEW, job.getId()))
                .contains(CAPTURED_AT);

        var capture = (ObjectNode) Objects.requireNonNull(job.getCapture());
        var full = mapper.treeToValue(capture.path("manifest"), JobFolderIndex.class);
        capture.set("manifest", mapper.valueToTree(full.retained()));
        assertThat(capture.path("manifest").has("files")).isFalse();
        assertThat(adapter.capturedAt(1, RuntimeReviewTestData.REVIEW, job.getId()))
                .contains(CAPTURED_AT);
    }

    @Test
    void shouldNotReadAJobOfAnotherReviewOrAnotherWorkspace() {
        when(jobs.findByIdAndWorkspaceId(job.getId(), 1L)).thenReturn(Optional.of(job));
        when(jobs.findByIdAndWorkspaceId(job.getId(), 2L)).thenReturn(Optional.empty());

        assertThat(adapter.capturedAt(1, UUID.randomUUID(), job.getId())).isEmpty();
        assertThat(adapter.capturedAt(2, RuntimeReviewTestData.REVIEW, job.getId()))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"no capture", "no manifest", "text manifest", "no time", "invalid time", "invalid digest"})
    void shouldLeaveTheCaptureTimeUnknownWhenTheManifestCannotShowIt(String defect) {
        var capture = (ObjectNode) Objects.requireNonNull(job.getCapture());
        var manifest = (ObjectNode) capture.path("manifest");
        switch (defect) {
            case "no capture" -> job.setCapture(null);
            case "no manifest" -> capture.remove("manifest");
            case "text manifest" -> capture.put("manifest", EvidenceSnapshotFixtures.CAPTURED_AT);
            case "no time" -> manifest.remove("capturedAt");
            case "invalid time" -> manifest.put("capturedAt", "yesterday");
            case "invalid digest" -> manifest.put("catalogDigest", "not-a-digest");
            default -> throw new IllegalArgumentException(defect);
        }
        when(jobs.findByIdAndWorkspaceId(job.getId(), 1L)).thenReturn(Optional.of(job));

        assertThat(adapter.capturedAt(1, RuntimeReviewTestData.REVIEW, job.getId()))
                .isEmpty();
    }

    @Test
    void shouldPropagateAFailedJobRead() {
        var failure = new DataAccessResourceFailureException("The database is unavailable");
        when(jobs.findByIdAndWorkspaceId(job.getId(), 1L)).thenThrow(failure);

        assertThatThrownBy(() -> adapter.capturedAt(1, RuntimeReviewTestData.REVIEW, job.getId()))
                .isSameAs(failure);
    }
}
