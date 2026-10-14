package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.evidence.capture.CitableText;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JobFolderIndexBuilderTest extends BaseUnitTest {
    private final SourceKind source = new SourceKind("docs.document.core");

    private JobFolderIndexBuilder builder() {
        var registry = new ClasspathSourceCatalogRegistry(new JsonMapper(), Clock.systemUTC());
        return new JobFolderIndexBuilder(registry, new JsonMapper(), Clock.systemUTC());
    }

    private CaptureContribution contribution(String registeredSource) {
        byte[] content = "Evidence".getBytes(StandardCharsets.UTF_8);
        var text = new CitableText(
                registeredSource,
                "document/body",
                CitationSide.NEW,
                null,
                "context/body.txt",
                ProvenanceDigest.sha256Hex(content));
        return new CaptureContribution(
                Map.of("context/body.txt", content),
                Map.of(source, SourceCompleteness.COMPLETE),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                Map.of(),
                List.of(),
                List.of(),
                List.of(text));
    }

    @Test
    void retainsTheLogicalCitationAndItsActualCapturedBytes() {
        var prepared = builder().build(new ItemKind("docs.document"), Map.of(source, contribution(source.value())));
        assertThat(Objects.requireNonNull(prepared.manifest()).citableTexts())
                .singleElement()
                .satisfies(text -> {
                    assertThat(text.path()).isEqualTo("document/body");
                    assertThat(text.stagedPath()).isEqualTo("context/body.txt");
                    assertThat(text.sha256())
                            .isEqualTo(Objects.requireNonNull(prepared.manifest())
                                    .files()
                                    .getFirst()
                                    .file()
                                    .sha256());
                });
    }

    @Test
    void shouldStageTheCatalogCitationCapabilitiesForTheSharedRunnerFixture() throws IOException {
        var mapper = JsonMapper.builder().build();
        var clock = Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC);
        var registry = new ClasspathSourceCatalogRegistry(mapper, clock);
        var contributions = new LinkedHashMap<SourceKind, CaptureContribution>();
        for (var kind :
                List.of("scm.pull-request.core", "scm.pull-request.diff", "scm.repository.tree", "docs.document.core"))
            contributions.put(new SourceKind(kind), CaptureContribution.empty());
        var prepared = new JobFolderIndexBuilder(registry, mapper, clock)
                .build(new ItemKind("scm.pull_request"), contributions);
        var actual = mapper.readTree(prepared.files().get(SandboxLayout.MANIFEST_PATH));
        var expected =
                mapper.readTree(Files.readString(Path.of("src/test/resources/agent/folder-citation-index.json")));
        assertThat(actual.path("sources")).isEqualTo(expected.path("sources"));
        assertThat(actual.path("files")).isEqualTo(expected.path("files"));
    }

    @Test
    void rejectsACitationRegisteredUnderAnotherSource() {
        assertThatThrownBy(() -> builder()
                        .build(new ItemKind("docs.document"), Map.of(source, contribution("docs.document.other"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
