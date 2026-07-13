package de.tum.cit.aet.hephaestus.practices.report.dto;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PracticeReportItemDTOTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Observation finding(String evidenceJson) {
        return Observation.builder()
            .id(UUID.randomUUID())
            .title("Distance-warning logic ships with no test")
            .severity(Severity.MAJOR)
            .presence(Presence.ABSENT)
            .assessment(Assessment.BAD)
            .artifactType(WorkArtifact.PULL_REQUEST)
            .artifactId(575L)
            .evidence(evidenceJson == null ? null : MAPPER.readTree(evidenceJson))
            .build();
    }

    @Test
    @DisplayName("a real source location renders as path:line")
    void realSourceLocation() {
        var item = PracticeReportItemDTO.from(
            finding("{\"locations\":[{\"path\":\"client/App/Services/AR/FrameRecorder.swift\",\"startLine\":212}]}"),
            null
        );
        assertThat(item.locator()).isEqualTo("client/App/Services/AR/FrameRecorder.swift:212");
    }

    @Test
    @DisplayName("an agent-internal context file is NOT leaked as a locator")
    void internalContextPathSuppressed() {
        assertThat(
            PracticeReportItemDTO.from(
                finding("{\"locations\":[{\"path\":\"inputs/context/test_presence.json\",\"startLine\":1}]}"),
                null
            ).locator()
        ).isNull();
        assertThat(
            PracticeReportItemDTO.from(
                finding("{\"locations\":[{\"path\":\"context/target/review_threads.json\",\"startLine\":1}]}"),
                null
            ).locator()
        ).isNull();
        assertThat(
            PracticeReportItemDTO.from(finding("{\"locations\":[{\"path\":\"metadata.json\"}]}"), null).locator()
        ).isNull();
    }

    @Test
    @DisplayName("C2: a genuine repo file under the inputs/sources/scm/repo mount is repo-relativized, not suppressed")
    void repoMountedUserCodeIsRepoRelativeNotInternal() {
        // Real user code mounted under inputs/sources/scm/repo/ must have the mount prefix stripped FIRST,
        // so the path renders openable rather than being misclassified as internal "inputs/" plumbing.
        var item = PracticeReportItemDTO.from(
            finding(
                "{\"locations\":[{\"path\":\"inputs/sources/scm/repo/client/App/Services/AR/FrameRecorder.swift\",\"startLine\":212}]}"
            ),
            null
        );
        assertThat(item.locator()).isEqualTo("client/App/Services/AR/FrameRecorder.swift:212");
    }

    @Test
    @DisplayName("C2: inputs/practices and the input manifest stay suppressed as internal plumbing")
    void practicesAndManifestStillSuppressed() {
        assertThat(
            PracticeReportItemDTO.from(
                finding("{\"locations\":[{\"path\":\"inputs/practices/index.json\",\"startLine\":1}]}"),
                null
            ).locator()
        ).isNull();
        assertThat(
            PracticeReportItemDTO.from(finding("{\"locations\":[{\"path\":\"inputs/manifest.json\"}]}"), null).locator()
        ).isNull();
    }

    @Test
    @DisplayName("no evidence / no location → no locator (not an error)")
    void noLocation() {
        assertThat(PracticeReportItemDTO.from(finding(null), null).locator()).isNull();
        assertThat(PracticeReportItemDTO.from(finding("{\"snippets\":[\"x\"]}"), null).locator()).isNull();
        assertThat(PracticeReportItemDTO.from(finding("{\"locations\":[]}"), null).locator()).isNull();
    }

    @Test
    @DisplayName("path with no startLine renders as the bare path")
    void pathWithoutLine() {
        var item = PracticeReportItemDTO.from(finding("{\"locations\":[{\"path\":\"README.md\"}]}"), null);
        assertThat(item.locator()).isEqualTo("README.md");
    }

    @Test
    @DisplayName("guidance is the delivered feedback body passed in — null when nothing was delivered")
    void guidanceComesFromDeliveredBody() {
        // ADR 0021: the finding carries no advice; guidance is the delivered Feedback body, supplied by the caller.
        assertThat(
            PracticeReportItemDTO.from(finding(null), "Add a unit test for evaluateDistance.").guidance()
        ).isEqualTo("Add a unit test for evaluateDistance.");
        assertThat(PracticeReportItemDTO.from(finding(null), null).guidance()).isNull();
    }
}
