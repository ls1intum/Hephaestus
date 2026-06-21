package de.tum.cit.aet.hephaestus.practices.finding.dto;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReflectionItemDTOTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static PracticeFinding finding(String evidenceJson) {
        return PracticeFinding.builder()
            .id(UUID.randomUUID())
            .title("Distance-warning logic ships with no test")
            .severity(Severity.MAJOR)
            .verdict(Observation.NOT_OBSERVED)
            .artifactType(WorkArtifact.PULL_REQUEST)
            .artifactId(575L)
            .evidence(evidenceJson == null ? null : MAPPER.readTree(evidenceJson))
            .build();
    }

    @Test
    @DisplayName("a real source location renders as path:line")
    void realSourceLocation() {
        var item = ReflectionItemDTO.from(
            finding("{\"locations\":[{\"path\":\"client/App/Services/AR/FrameRecorder.swift\",\"startLine\":212}]}"),
            null
        );
        assertThat(item.locator()).isEqualTo("client/App/Services/AR/FrameRecorder.swift:212");
    }

    @Test
    @DisplayName("an agent-internal context file is NOT leaked as a locator")
    void internalContextPathSuppressed() {
        assertThat(
            ReflectionItemDTO.from(
                finding("{\"locations\":[{\"path\":\"inputs/context/test_presence.json\",\"startLine\":1}]}"),
                null
            ).locator()
        ).isNull();
        assertThat(
            ReflectionItemDTO.from(
                finding("{\"locations\":[{\"path\":\"context/target/review_threads.json\",\"startLine\":1}]}"),
                null
            ).locator()
        ).isNull();
        assertThat(
            ReflectionItemDTO.from(finding("{\"locations\":[{\"path\":\"metadata.json\"}]}"), null).locator()
        ).isNull();
    }

    @Test
    @DisplayName("no evidence / no location → no locator (not an error)")
    void noLocation() {
        assertThat(ReflectionItemDTO.from(finding(null), null).locator()).isNull();
        assertThat(ReflectionItemDTO.from(finding("{\"snippets\":[\"x\"]}"), null).locator()).isNull();
        assertThat(ReflectionItemDTO.from(finding("{\"locations\":[]}"), null).locator()).isNull();
    }

    @Test
    @DisplayName("path with no startLine renders as the bare path")
    void pathWithoutLine() {
        var item = ReflectionItemDTO.from(finding("{\"locations\":[{\"path\":\"README.md\"}]}"), null);
        assertThat(item.locator()).isEqualTo("README.md");
    }

    @Test
    @DisplayName("guidance is the delivered feedback body passed in — null when nothing was delivered")
    void guidanceComesFromDeliveredBody() {
        // ADR 0021: the finding carries no advice; guidance is the delivered Feedback body, supplied by the caller.
        assertThat(ReflectionItemDTO.from(finding(null), "Add a unit test for evaluateDistance.").guidance()).isEqualTo(
            "Add a unit test for evaluateDistance."
        );
        assertThat(ReflectionItemDTO.from(finding(null), null).guidance()).isNull();
    }
}
