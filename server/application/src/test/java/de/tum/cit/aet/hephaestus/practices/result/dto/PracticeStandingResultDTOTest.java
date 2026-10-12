package de.tum.cit.aet.hephaestus.practices.result.dto;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PracticeStandingResultDTOTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static PracticeResult practiceResult(@Nullable String evidenceJson) {
        return PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .summary("Distance-warning logic ships with no test")
                .severity(Severity.MAJOR)
                .outcome(Outcome.NOT_MET)
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(575L)
                .evidence(evidenceJson == null ? null : MAPPER.readTree(evidenceJson))
                .build();
    }

    private static String citation(String path, int line) {
        return citation(path, line, "scm.pull-request.diff");
    }

    private static String citation(String path, int line, String sourceKind) {
        String artifactPath =
                sourceKind.equals("scm.pull-request.diff") ? "context/diff.patch" : "context/metadata.json";
        String side = sourceKind.equals("scm.pull-request.diff") ? ",\"side\":\"NEW\"" : "";
        return """
        {"citations":[{"sourceKind":"%s",\
        "artifactPath":"%s","path":"%s"%s,\
        "startLine":%d,"endLine":%d,"quote":"line","quoteRedacted":false}]}
        """.formatted(sourceKind, artifactPath, path, side, line, line);
    }

    @Test
    @DisplayName("a real source location renders as path:line")
    void realSourceLocation() {
        var result = PracticeStandingResultDTO.from(
                practiceResult(citation("client/App/Services/AR/FrameRecorder.swift", 212)), null);
        assertThat(result.locator()).isEqualTo("client/App/Services/AR/FrameRecorder.swift:212");
    }

    @Test
    @DisplayName("an agent-internal context file is NOT leaked as a locator")
    void internalContextPathSuppressed() {
        assertThat(PracticeStandingResultDTO.from(
                                practiceResult(citation("test_presence.json", 1, "scm.pull-request.core")), null)
                        .locator())
                .isNull();
        assertThat(PracticeStandingResultDTO.from(
                                practiceResult(citation("review_threads.json", 1, "scm.pull-request.core")), null)
                        .locator())
                .isNull();
        assertThat(PracticeStandingResultDTO.from(
                                practiceResult(citation("metadata.json", 1, "scm.pull-request.core")), null)
                        .locator())
                .isNull();
    }

    @Test
    @DisplayName("a repository file named metadata.json remains a valid code locator")
    void repositoryMetadataFileIsNotMistakenForInternalContext() {
        var result = PracticeStandingResultDTO.from(practiceResult(citation("metadata.json", 12)), null);
        assertThat(result.locator()).isEqualTo("metadata.json:12");
    }

    @Test
    @DisplayName("C2: inputs/practices and the input manifest stay suppressed as internal plumbing")
    void practicesAndManifestStillSuppressed() {
        assertThat(PracticeStandingResultDTO.from(
                                practiceResult(citation("index.json", 1, "scm.pull-request.core")), null)
                        .locator())
                .isNull();
        assertThat(PracticeStandingResultDTO.from(
                                practiceResult(citation("manifest.json", 1, "scm.pull-request.core")), null)
                        .locator())
                .isNull();
    }

    @Test
    @DisplayName("no evidence / no location → no locator (not an error)")
    void noLocation() {
        assertThat(PracticeStandingResultDTO.from(practiceResult(null), null).locator())
                .isNull();
        assertThat(PracticeStandingResultDTO.from(practiceResult("{\"citations\":[]}"), null)
                        .locator())
                .isNull();
    }

    @Test
    @DisplayName("deliveredFeedback is the delivered feedback body passed in — null when nothing was delivered")
    void deliveredFeedbackComesFromDeliveredBody() {
        assertThat(PracticeStandingResultDTO.from(practiceResult(null), "Add a unit test for evaluateDistance.")
                        .deliveredFeedback())
                .isEqualTo("Add a unit test for evaluateDistance.");
        assertThat(PracticeStandingResultDTO.from(practiceResult(null), null).deliveredFeedback())
                .isNull();
    }
}
