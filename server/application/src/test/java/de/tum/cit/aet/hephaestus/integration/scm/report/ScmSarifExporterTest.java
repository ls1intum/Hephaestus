package de.tum.cit.aet.hephaestus.integration.scm.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExport;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExportLocation;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExportProblem;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class ScmSarifExporterTest extends BaseUnitTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ScmSarifExporter exporter = new ScmSarifExporter(mapper, "1.0.0");

    @Test
    void shouldExportVisibleProblemsWithExactQuotesUrisAndPartialCoverage() {
        var problems = List.of(
                problem("CRITICAL", "NEW"),
                problem("MAJOR", "UNCHANGED"),
                problem("MINOR", "UPDATED"),
                problem("TRIVIAL", null));
        var data = mapper.readTree(exporter.export(report(false, false, false, problems)));
        var run = data.path("runs").path(0);
        assertThat(data.path("version").asString()).isEqualTo("2.1.0");
        assertThat(run.path("results")).hasSize(4);
        assertThat(run.path("tool").path("driver").path("rules")).hasSize(1);
        assertThat(run.path("results").path(0).path("level").asString()).isEqualTo("error");
        assertThat(run.path("results").path(2).path("level").asString()).isEqualTo("warning");
        assertThat(run.path("results").path(3).path("level").asString()).isEqualTo("note");
        var physical = run.path("results").path(0).path("locations").path(0).path("physicalLocation");
        assertThat(physical.path("artifactLocation").path("uri").asString()).isEqualTo("src/a%20%23%25%0A%C3%A4.java");
        assertThat(physical.path("region").path("snippet").path("text").asString())
                .isEqualTo("if (ready) run();");
        assertThat(physical.path("region").has("startColumn")).isFalse();
        assertThat(run.path("invocations").path(0).path("executionSuccessful").asBoolean())
                .isFalse();
        assertThat(run.path("versionControlProvenance")
                        .path(0)
                        .path("repositoryUri")
                        .asString())
                .isEqualTo("https://example.test/team/repo");
        assertThat(new String(exporter.export(report(false, false, false, problems)), StandardCharsets.UTF_8))
                .doesNotContain("secret");
    }

    @Test
    void shouldRejectActiveOrExpiredProofAndUnprovedAbsence() {
        assertThatThrownBy(() -> exporter.export(report(true, false, true, List.of())))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThatThrownBy(() -> exporter.export(report(false, true, true, List.of())))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(410));
        assertThatThrownBy(() -> problem("MAJOR", "ABSENT")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldProduceTheOfficialSchemaProtocolFixture() throws Exception {
        var problem = new ScanExportProblem(
                UUID.fromString("00000000-0000-4000-8000-000000000003"),
                UUID.fromString("00000000-0000-4000-8000-000000000004"),
                3,
                "frozen",
                4,
                "clear-code",
                "Clear code",
                "Keep code clear.",
                "digest",
                "MAJOR",
                "Explain the branch.",
                "src/a #%\nä.java",
                "scheme-1-identity",
                "NEW",
                false,
                List.of(new ScanExportLocation("src/a #%\nä.java", 2, 2, "if (ready) run();")));
        var report = new ScanExport(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                1,
                2,
                "https://user:secret@example.test/team/repo?token=secret#secret",
                "a".repeat(40),
                "selected",
                "model",
                1,
                false,
                false,
                false,
                true,
                UUID.fromString("00000000-0000-4000-8000-000000000002"),
                true,
                Map.of("problems", 1L),
                List.of("INCOMPLETE"),
                List.of(problem));
        try (var fixture =
                Objects.requireNonNull(getClass().getResourceAsStream("/integration/scm/sarif/expected.json"))) {
            assertThat(mapper.readTree(exporter.export(report))).isEqualTo(mapper.readTree(fixture));
        }
    }

    private ScanExport report(boolean active, boolean expired, boolean complete, List<ScanExportProblem> problems) {
        return new ScanExport(
                UUID.randomUUID(),
                1,
                2,
                "https://user:secret@example.test/team/repo?token=secret#secret",
                "a".repeat(40),
                "selected",
                "model",
                1,
                active,
                expired,
                complete,
                true,
                UUID.randomUUID(),
                true,
                Map.of("problems", (long) problems.size()),
                List.of("INCOMPLETE"),
                problems);
    }

    private ScanExportProblem problem(String severity, @Nullable String baseline) {
        return new ScanExportProblem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                3,
                "frozen",
                4,
                "clear-code",
                "Clear code",
                "Keep code clear.",
                "digest",
                severity,
                "Explain the branch.",
                "src/a #%\nä.java",
                "scheme-1-identity",
                baseline,
                false,
                List.of(new ScanExportLocation("src/a #%\nä.java", 2, 2, "if (ready) run();")));
    }
}
