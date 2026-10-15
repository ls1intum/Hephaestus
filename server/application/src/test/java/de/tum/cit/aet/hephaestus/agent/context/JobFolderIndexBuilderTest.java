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
import de.tum.cit.aet.hephaestus.evidence.capture.EvidenceDirectory;
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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @TempDir
    Path root;

    private CaptureContribution staged(
            Map<String, byte[]> memory,
            Map<String, Path> disk,
            List<EvidenceDirectory> directories,
            List<CitableText> texts) {
        return new CaptureContribution(
                memory,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                disk,
                null,
                Map.of(),
                directories,
                List.of(),
                texts);
    }

    @Test
    void shouldIndexDiskFilesWithTheirActualDigestAndMediaType() throws IOException {
        Path file = Files.writeString(root.resolve("document.json"), "{\"title\":\"Design\"}");
        var captured = staged(
                Map.of(),
                Map.of("context/document.json", file),
                List.of(),
                List.of(new CitableText(
                        source.value(),
                        "document/body",
                        CitationSide.NEW,
                        null,
                        "context/document.json",
                        ProvenanceDigest.sha256Hex(Files.readAllBytes(file)))));
        var prepared = builder().build(new ItemKind("docs.document"), Map.of(source, captured));
        assertThat(prepared.filesOnDisk()).containsEntry("context/document.json", file);
        assertThat(Objects.requireNonNull(prepared.manifest()).files())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.file().mediaType()).isEqualTo("application/json");
                    assertThat(entry.file().bytes()).isEqualTo(Files.size(file));
                    assertThat(entry.file().sha256()).isEqualTo(ProvenanceDigest.sha256Hex(Files.readAllBytes(file)));
                });
        assertThat(Objects.requireNonNull(prepared.manifest()).citableTexts()).isEqualTo(captured.citableTexts());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldResolveBothRelativeAndAlreadyPrefixedDirectoryText(boolean prefixed) throws IOException {
        Files.writeString(root.resolve("body.txt"), "Evidence");
        var text = new CitableText(
                source.value(),
                "document/body",
                CitationSide.NEW,
                null,
                prefixed ? "context/body.txt" : "body.txt",
                ProvenanceDigest.sha256Hex("Evidence".getBytes(StandardCharsets.UTF_8)));
        var captured = staged(
                Map.of("context/index.json", "{}".getBytes(StandardCharsets.UTF_8)),
                Map.of(),
                List.of(new EvidenceDirectory("context/", root)),
                List.of(text));
        var prepared = builder().build(new ItemKind("docs.document"), Map.of(source, captured));
        assertThat(prepared.directories()).isEqualTo(captured.directories());
        assertThat(Objects.requireNonNull(prepared.manifest()).citableTexts())
                .containsExactly(new CitableText(
                        text.source(), text.path(), text.side(), text.commit(), "context/body.txt", text.sha256()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldRefuseTextWithoutOneCapturedDirectory(int owners) throws IOException {
        Files.writeString(root.resolve("body.txt"), "Evidence");
        var text = new CitableText(source.value(), "document/body", CitationSide.NEW, null, "body.txt", "a".repeat(64));
        var directory = new EvidenceDirectory("context/", root);
        var captured =
                staged(Map.of(), Map.of(), owners == 0 ? List.of() : List.of(directory, directory), List.of(text));
        assertThatThrownBy(() -> builder().build(new ItemKind("docs.document"), Map.of(source, captured)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A citable text has no unique captured directory");
    }

    @ParameterizedTest
    @ValueSource(strings = {"memory-memory", "memory-disk", "disk-memory", "disk-disk"})
    void shouldRefuseAPathClaimedByTwoSources(String storage) throws IOException {
        Path file = Files.writeString(root.resolve("body.txt"), "Evidence");
        var other = new SourceKind("scm.pull-request.core");
        var contributions = new LinkedHashMap<SourceKind, CaptureContribution>();
        contributions.put(
                source,
                staged(
                        storage.startsWith("memory") ? Map.of("context/body.txt", new byte[0]) : Map.of(),
                        storage.startsWith("disk") ? Map.of("context/body.txt", file) : Map.of(),
                        List.of(),
                        List.of()));
        contributions.put(
                other,
                staged(
                        storage.endsWith("memory") ? Map.of("context/body.txt", new byte[0]) : Map.of(),
                        storage.endsWith("disk") ? Map.of("context/body.txt", file) : Map.of(),
                        List.of(),
                        List.of()));
        assertThatThrownBy(() -> builder().build(new ItemKind("docs.document"), contributions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate captured path: context/body.txt");
    }
}
