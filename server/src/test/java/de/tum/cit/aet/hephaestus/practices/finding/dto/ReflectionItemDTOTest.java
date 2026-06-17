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
            .guidance("Add a unit test for evaluateDistance.")
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
            finding(
                "{\"locations\":[{\"path\":\"client/Obsphera/Services/AR/FrameRecorder.swift\",\"startLine\":212}]}"
            )
        );
        assertThat(item.locator()).isEqualTo("client/Obsphera/Services/AR/FrameRecorder.swift:212");
    }

    @Test
    @DisplayName("an agent-internal context file is NOT leaked as a locator")
    void internalContextPathSuppressed() {
        assertThat(
            ReflectionItemDTO.from(
                finding("{\"locations\":[{\"path\":\"inputs/context/test_presence.json\",\"startLine\":1}]}")
            ).locator()
        ).isNull();
        assertThat(
            ReflectionItemDTO.from(
                finding("{\"locations\":[{\"path\":\"context/target/review_threads.json\",\"startLine\":1}]}")
            ).locator()
        ).isNull();
        assertThat(
            ReflectionItemDTO.from(finding("{\"locations\":[{\"path\":\"metadata.json\"}]}")).locator()
        ).isNull();
    }

    @Test
    @DisplayName("no evidence / no location → no locator (not an error)")
    void noLocation() {
        assertThat(ReflectionItemDTO.from(finding(null)).locator()).isNull();
        assertThat(ReflectionItemDTO.from(finding("{\"snippets\":[\"x\"]}")).locator()).isNull();
        assertThat(ReflectionItemDTO.from(finding("{\"locations\":[]}")).locator()).isNull();
    }

    @Test
    @DisplayName("path with no startLine renders as the bare path")
    void pathWithoutLine() {
        var item = ReflectionItemDTO.from(finding("{\"locations\":[{\"path\":\"README.md\"}]}"));
        assertThat(item.locator()).isEqualTo("README.md");
    }
}
