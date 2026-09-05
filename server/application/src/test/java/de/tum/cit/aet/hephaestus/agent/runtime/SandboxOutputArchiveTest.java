package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SandboxOutputArchiveTest extends BaseUnitTest {

    private final SandboxOutputArchive reader = new SandboxOutputArchive();

    @Test
    void shouldReadNestedRegularFilesWhenArchiveIsValid() throws IOException {
        byte[] archive = archive(file("out/result.json", 2), file("out/nested/report.json", 3));
        var result = reader.read(new ByteArrayInputStream(archive), "out");
        assertThat(result).containsOnlyKeys("result.json", "nested/report.json");
        assertThat(result.get("result.json")).containsExactly((byte) 1, (byte) 2);
        assertThat(result.get("nested/report.json")).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void shouldAcceptEmptyOutputWhenArchiveContainsEndRecords() throws IOException {
        assertThat(reader.read(new ByteArrayInputStream(archive()), "out")).isEmpty();
    }

    @Test
    void shouldNormalizePathsWhenTheyRemainInsideOutput() throws IOException {
        var result = reader.read(new ByteArrayInputStream(archive(file("./out/nested/./result.json", 0))), "out");
        assertThat(result).containsOnlyKeys("nested/result.json");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../out/result.json",
                "out/../result.json",
                "out/nested/../../out/result.json",
                "/out/result.json",
                "elsewhere/result.json",
                "out",
                "outlook/result.json",
                "out/..\\result.json",
                "C:/out/result.json",
                "out/C:result.json"
            })
    void shouldRejectArchiveWhenPathIsUnsafe(String path) throws IOException {
        byte[] bytes = archive(file(path, 0));
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(bytes), "out"))
                .isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {'1', '2', '3', '4', '6', '7', 'S', 'x', 'g', 'L', 'K'})
    void shouldRejectUnsupportedHeaderBeforeReadingItsPayloadWhenTypeIsNotRegular(int type) {
        var entry = new TarArchiveEntry("out/entry", (byte) type);
        // Header only: the reader must refuse metadata before a library buffers or expands it.
        byte[] header = new byte[512];
        entry.writeEntryHeader(header);
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(header), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("regular files");
    }

    @Test
    void shouldAcceptEmptyDirectoryHeadersWhenReadingOutput() throws IOException {
        byte[] bytes = archive(
                new TarArchiveEntry("out/"), new TarArchiveEntry("out/nested/"), file("out/nested/result.json", 0));
        assertThat(reader.read(new ByteArrayInputStream(bytes), "out")).containsOnlyKeys("nested/result.json");
    }

    @Test
    void shouldRejectDirectoryTraversalWhenReadingDockerOutput() throws IOException {
        byte[] bytes = archive(new TarArchiveEntry("out/../outside/"));
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(bytes), "out"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectDirectoryDisguisedAsRegularFileWhenReadingOutput() {
        var entry = new TarArchiveEntry("out/", TarConstants.LF_NORMAL);
        byte[] header = new byte[512];
        entry.writeEntryHeader(header);
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(header), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("regular files");
    }

    @Test
    void shouldRejectDirectoryPayloadWhenReadingDockerOutput() {
        var entry = new TarArchiveEntry("out/", TarConstants.LF_DIR);
        entry.setSize(100);
        byte[] header = new byte[512];
        entry.writeEntryHeader(header);
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(header), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("regular files");
    }

    @Test
    void shouldRejectArchiveWhenNormalizedPathsCollide() throws IOException {
        byte[] bytes = archive(file("out/result.json", 0), file("out/./result.json", 0));
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(bytes), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void shouldRejectArchiveWhenZeroByteFilesExceedEntryLimit() throws IOException {
        var limited = new SandboxOutputArchive(8192, 100, 100, 2);
        byte[] bytes = archive(file("out/a", 0), file("out/b", 0), file("out/c", 0));
        assertThatThrownBy(() -> limited.read(new ByteArrayInputStream(bytes), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("entry count");
    }

    @Test
    void shouldCountDirectoriesWhenReadingDockerOutput() throws IOException {
        var limited = new SandboxOutputArchive(8192, 100, 100, 1);
        byte[] bytes = archive(new TarArchiveEntry("out/"), file("out/a", 0));
        assertThatThrownBy(() -> limited.read(new ByteArrayInputStream(bytes), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("entry count");
    }

    @Test
    void shouldAcceptExactLimitsWhenArchiveIsWithinAllBudgets() throws IOException {
        byte[] bytes = archive(file("out/a", 4), file("out/b", 4));
        var limited = new SandboxOutputArchive(bytes.length, 8, 4, 2);
        assertThat(limited.read(new ByteArrayInputStream(bytes), "out")).hasSize(2);
    }

    @Test
    void shouldRejectArchiveWhenExtractedBytesExceedBudget() throws IOException {
        byte[] bytes = archive(file("out/a", 4), file("out/b", 4));
        var limited = new SandboxOutputArchive(bytes.length, 7, 4, 2);
        assertThatThrownBy(() -> limited.read(new ByteArrayInputStream(bytes), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("extracted size");
    }

    @Test
    void shouldRejectArchiveBeforeAllocatingWhenFileDeclaresExcessiveSize() {
        var entry = file("out/a", Integer.MAX_VALUE);
        byte[] header = new byte[512];
        entry.writeEntryHeader(header);
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(header), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("extracted size");
    }

    @Test
    void shouldRejectArchiveWhenPaddingExceedsWireBudget() throws IOException {
        byte[] bytes = Arrays.copyOf(archive(), 4096);
        var limited = new SandboxOutputArchive(2048, 100, 100, 2);
        assertThatThrownBy(() -> limited.read(new ByteArrayInputStream(bytes), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("wire size");
    }

    @Test
    void shouldRejectArchiveWhenChecksumIsInvalid() throws IOException {
        byte[] bytes = archive(file("out/a", 0));
        bytes[0] ^= 1;
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(bytes), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("checksums");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 511, 512, 514, 1024, 1536})
    void shouldRejectArchiveWhenTransferIsTruncated(int length) throws IOException {
        byte[] bytes = Arrays.copyOf(archive(file("out/a", 4)), length);
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(bytes), "out"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectArchiveWhenInputIsGzipEncoded() throws IOException {
        var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(archive());
        }
        var limited = new SandboxOutputArchive(8192, 100, 100, 2);
        assertThatThrownBy(() -> limited.read(new ByteArrayInputStream(compressed.toByteArray()), "out"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectArchiveWhenNonzeroDataFollowsEndRecords() throws IOException {
        byte[] bytes = archive(file("out/a", 0));
        byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
        trailing[bytes.length] = 1;
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(trailing), "out"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("after output archive");
    }

    @Test
    void shouldRejectArchiveWhenAnotherArchiveFollowsEndRecords() throws IOException {
        var bytes = new ByteArrayOutputStream();
        bytes.write(archive(file("out/a", 0)));
        bytes.write(archive(file("out/b", 0)));
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(bytes.toByteArray()), "out"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldPreserveResultWhenArchiveUsesDockerProducerFormat() throws IOException {
        // Generated with Go archive/tar, FormatPAX and second-precision mtime, as Moby uses.
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/agent/docker-output.tar"))) {
            var output = reader.read(input, "out");
            assertThat(output).containsOnlyKeys("result.json");
            assertThat(output.get("result.json")).isEqualTo("{\"observations\":[]}\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void shouldReadConfiguredRootWhenItIsNotOut() throws IOException {
        var output = reader.read(new ByteArrayInputStream(archive(file("custom/result.json", 2))), "custom");
        assertThat(output).containsOnlyKeys("result.json");
    }

    private static TarArchiveEntry file(String name, long size) {
        var entry = new TarArchiveEntry(name, TarConstants.LF_NORMAL, true);
        entry.setSize(size);
        return entry;
    }

    private static byte[] archive(TarArchiveEntry... entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(bytes)) {
            for (var entry : entries) {
                tar.putArchiveEntry(entry);
                byte[] content = new byte[(int) entry.getSize()];
                for (int i = 0; i < content.length; i++) content[i] = (byte) (i + 1);
                tar.write(content);
                tar.closeArchiveEntry();
            }
        }
        return bytes.toByteArray();
    }
}
