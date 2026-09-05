package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxInfrastructureException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

class SandboxWorkspaceManagerTest extends BaseUnitTest {

    @Mock
    private DockerFileOperations fileOps;

    private SandboxWorkspaceManager manager;

    private static final String CONTAINER_ID = "abc123";

    @BeforeEach
    void setUp() {
        manager = new SandboxWorkspaceManager(fileOps);
    }

    @Nested
    class InjectFiles {

        @Test
        void shouldInjectFiles() {
            Map<String, byte[]> files = Map.of(".prompt", "test prompt".getBytes(), "config.json", "{}".getBytes());

            manager.injectFiles(CONTAINER_ID, files);

            verify(fileOps).copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));
        }

        @Test
        void shouldApplyWorkspaceRegionPermissionsWhenFilesAreInjected() throws IOException {
            Map<String, byte[]> files = new HashMap<>();
            files.put("inputs/context/diff.patch", "d".getBytes());
            files.put(".pi/settings.json", "{}".getBytes());
            files.put(".sessions/thread.jsonl", "{}".getBytes());
            files.put("out/.gitkeep", new byte[0]);
            files.put("work/analysis/practices/.gitkeep", new byte[0]);
            files.put("work/precompute/practices/foo.ts", "x".getBytes());

            Map<String, Long> dirUid = new HashMap<>();
            Map<String, Integer> dirMode = new HashMap<>();
            Map<String, Long> fileUid = new HashMap<>();
            Map<String, Integer> fileMode = new HashMap<>();
            doAnswer(invocation -> {
                        try (var tis = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                                invocation.getArgument(2, InputStream.class))) {
                            TarArchiveEntry e;
                            while ((e = tis.getNextEntry()) != null) {
                                if (e.isDirectory()) {
                                    dirUid.put(e.getName(), e.getLongUserId());
                                    dirMode.put(e.getName(), e.getMode());
                                } else {
                                    fileUid.put(e.getName(), e.getLongUserId());
                                    fileMode.put(e.getName(), e.getMode());
                                }
                            }
                        }
                        return null;
                    })
                    .when(fileOps)
                    .copyArchiveToContainer(any(), any(), any());

            manager.injectFiles(CONTAINER_ID, files);

            assertThat(dirUid)
                    .containsEntry(".pi/", 1000L)
                    .containsEntry(".sessions/", 1000L)
                    .containsEntry("out/", 1000L)
                    .containsEntry("inputs/", 0L)
                    .containsEntry("inputs/context/", 0L)
                    .containsEntry("work/", 1000L)
                    .containsEntry("work/analysis/", 1000L)
                    .containsEntry("work/analysis/practices/", 1000L)
                    .containsEntry("work/precompute/", 1000L)
                    .containsEntry("work/precompute/practices/", 1000L);
            assertThat(dirMode)
                    .containsEntry(".pi/", 0755)
                    .containsEntry(".sessions/", 0755)
                    .containsEntry("out/", 0755)
                    .containsEntry("inputs/context/", 0555)
                    .containsEntry("work/analysis/", 0755);
            assertThat(fileUid)
                    .containsEntry("inputs/context/diff.patch", 0L)
                    .containsEntry(".pi/settings.json", 1000L)
                    .containsEntry(".sessions/thread.jsonl", 1000L)
                    .containsEntry("out/.gitkeep", 1000L)
                    .containsEntry("work/analysis/practices/.gitkeep", 1000L);
            assertThat(fileMode)
                    .containsEntry("inputs/context/diff.patch", 0444)
                    .containsEntry(".pi/settings.json", 0644)
                    .containsEntry(".sessions/thread.jsonl", 0644)
                    .containsEntry("out/.gitkeep", 0644)
                    .containsEntry("work/analysis/practices/.gitkeep", 0644);
        }

        @Test
        void shouldSkipWhenEmpty() {
            manager.injectFiles(CONTAINER_ID, Map.of());

            verify(fileOps, never()).copyArchiveToContainer(any(), any(), any());
        }

        @Test
        void shouldSkipWhenNull() {
            manager.injectFiles(CONTAINER_ID, null);

            verify(fileOps, never()).copyArchiveToContainer(any(), any(), any());
        }

        @Test
        void shouldRejectDirectoryTraversal() {
            Map<String, byte[]> files = Map.of("../../etc/passwd", "malicious".getBytes());

            assertThatThrownBy(() -> manager.injectFiles(CONTAINER_ID, files))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("traversal");
        }

        @Test
        void shouldRejectAbsolutePaths() {
            Map<String, byte[]> files = Map.of("/etc/shadow", "malicious".getBytes());

            assertThatThrownBy(() -> manager.injectFiles(CONTAINER_ID, files))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("Absolute");
        }

        @Test
        void shouldStreamLargeInputsFromDisk(@TempDir Path tempDir) throws Exception {
            Path large = tempDir.resolve("large.bin");
            byte[] chunk = new byte[1024 * 1024];
            java.util.Arrays.fill(chunk, (byte) 'x');
            try (var out = Files.newOutputStream(large)) {
                for (int i = 0; i < 64; i++) {
                    out.write(chunk);
                }
            }

            long[] staged = {0};
            doAnswer(invocation -> {
                        try (var tis = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                                invocation.getArgument(2, InputStream.class))) {
                            TarArchiveEntry entry;
                            while ((entry = tis.getNextEntry()) != null) {
                                if (!entry.isDirectory()) {
                                    staged[0] += entry.getSize();
                                }
                            }
                        }
                        return null;
                    })
                    .when(fileOps)
                    .copyArchiveToContainer(any(), any(), any());

            manager.injectFiles(CONTAINER_ID, Map.of(), Map.of("inputs/large.bin", large));

            assertThat(staged[0]).isEqualTo(64L * 1024 * 1024);
        }

        @Test
        void shouldStreamOnDiskInputs(@TempDir Path tempDir) throws Exception {
            Path source = tempDir.resolve("App.java");
            Files.writeString(source, "class App {}");

            Map<String, byte[]> captured = new HashMap<>();
            doAnswer(invocation -> {
                        try (var tis = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                                invocation.getArgument(2, InputStream.class))) {
                            TarArchiveEntry entry;
                            while ((entry = tis.getNextEntry()) != null) {
                                if (!entry.isDirectory()) {
                                    captured.put(entry.getName(), tis.readAllBytes());
                                }
                            }
                        }
                        return null;
                    })
                    .when(fileOps)
                    .copyArchiveToContainer(any(), any(), any());

            manager.injectFiles(
                    CONTAINER_ID,
                    Map.of("inputs/context/diff.patch", "diff".getBytes()),
                    Map.of("inputs/sources/scm/repo/App.java", source));

            assertThat(captured).containsOnlyKeys("inputs/context/diff.patch", "inputs/sources/scm/repo/App.java");
            assertThat(new String(captured.get("inputs/sources/scm/repo/App.java")))
                    .isEqualTo("class App {}");
        }
    }

    @Nested
    class CollectOutput {

        @Test
        void shouldExtractFiles() throws Exception {
            byte[] tarBytes = createTestTar(Map.of("out/result.json", "{\"status\":\"ok\"}".getBytes()));
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out"))
                    .thenReturn(new ByteArrayInputStream(tarBytes));

            Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/out");

            assertThat(output).containsKey("result.json");
            assertThat(new String(output.get("result.json"))).isEqualTo("{\"status\":\"ok\"}");
        }

        @Test
        void shouldFailWithoutInfrastructureRetryWhenOutputCannotBeCollected() {
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out"))
                    .thenThrow(new SandboxInfrastructureException("No such path"));

            assertThatThrownBy(() -> manager.collectOutput(CONTAINER_ID, "/workspace/out"))
                    .isExactlyInstanceOf(SandboxException.class)
                    .hasCauseInstanceOf(SandboxInfrastructureException.class);
        }

        @Test
        void shouldEnforceOutputSizeLimit() throws Exception {
            var limitedManager = new SandboxWorkspaceManager(
                    fileOps,
                    1024,
                    SandboxWorkspaceManager.MAX_SINGLE_FILE_BYTES,
                    SandboxWorkspaceManager.MAX_DIRECTORY_BYTES,
                    SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES);

            byte[] largeContent = new byte[800];
            byte[] secondContent = new byte[500];

            byte[] tarBytes = createTestTar(Map.of("out/first.bin", largeContent, "out/second.bin", secondContent));
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out"))
                    .thenReturn(new ByteArrayInputStream(tarBytes));

            assertThatThrownBy(() -> limitedManager.collectOutput(CONTAINER_ID, "/workspace/out"))
                    .isInstanceOf(SandboxException.class);
        }

        @Test
        void shouldRejectWholeOutputWhenPathTraversesRoot() throws Exception {
            byte[] tarBytes = createTestTar(Map.of(
                    "out/../../../etc/passwd", "malicious".getBytes(), "out/safe.txt", "safe content".getBytes()));
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out"))
                    .thenReturn(new ByteArrayInputStream(tarBytes));

            assertThatThrownBy(() -> manager.collectOutput(CONTAINER_ID, "/workspace/out"))
                    .isInstanceOf(SandboxException.class);
        }

        @Test
        void shouldRejectOutputWhenItContainsSymlinks() throws Exception {
            byte[] tarBytes = createTestTarWithSymlink("out/evil", "/etc/shadow");
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out"))
                    .thenReturn(new ByteArrayInputStream(tarBytes));

            assertThatThrownBy(() -> manager.collectOutput(CONTAINER_ID, "/workspace/out"))
                    .isInstanceOf(SandboxException.class);
        }

        @Test
        void shouldRejectOutputWhenItContainsHardLinks() throws Exception {
            byte[] tarBytes = createTestTarWithHardLink("out/link", "out/target");
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out"))
                    .thenReturn(new ByteArrayInputStream(tarBytes));

            assertThatThrownBy(() -> manager.collectOutput(CONTAINER_ID, "/workspace/out"))
                    .isInstanceOf(SandboxException.class);
        }

        @Test
        void shouldRejectWholeOutputWhenSingleFileExceedsLimit() throws Exception {
            var limitedManager = new SandboxWorkspaceManager(
                    fileOps,
                    10_000,
                    10,
                    SandboxWorkspaceManager.MAX_DIRECTORY_BYTES,
                    SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES);

            byte[] smallContent = "small".getBytes();
            byte[] oversizedContent = "this is way too big".getBytes();

            byte[] tarBytes = createTestTar(Map.of("out/small.txt", smallContent, "out/toobig.txt", oversizedContent));
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out"))
                    .thenReturn(new ByteArrayInputStream(tarBytes));

            assertThatThrownBy(() -> limitedManager.collectOutput(CONTAINER_ID, "/workspace/out"))
                    .isInstanceOf(SandboxException.class);
        }

        @Test
        void shouldSkipDirectories() throws Exception {
            byte[] tarBytes = createTestTarWithDir("result.json", "{}".getBytes());
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out"))
                    .thenReturn(new ByteArrayInputStream(tarBytes));

            Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/out");

            assertThat(output).containsKey("result.json");
            assertThat(output).hasSize(1);
        }
    }

    @Nested
    class InjectDirectories {

        @TempDir
        Path tempDir;

        @Test
        void shouldExcludeSymbolicLinksWhenInjectingDirectory() throws Exception {
            Path source = Files.createDirectory(tempDir.resolve("source"));
            Path outside = Files.createDirectory(tempDir.resolve("outside"));
            Path secret = Files.writeString(outside.resolve("secret.txt"), "not an input");
            Files.writeString(source.resolve("kept.txt"), "input");
            Files.createSymbolicLink(source.resolve("file-link"), secret);
            Files.createSymbolicLink(source.resolve("directory-link"), outside);
            Files.createSymbolicLink(source.resolve("dangling-link"), outside.resolve("missing"));
            Map<String, byte[]> entries = new HashMap<>();
            doAnswer(invocation -> {
                        try (var tar = new TarArchiveInputStream(invocation.getArgument(2, InputStream.class))) {
                            TarArchiveEntry entry;
                            while ((entry = tar.getNextEntry()) != null) {
                                entries.put(entry.getName(), tar.readAllBytes());
                            }
                        }
                        return null;
                    })
                    .when(fileOps)
                    .copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));

            manager.injectDirectories(CONTAINER_ID, Map.of(source.toString(), "/workspace/repo"));

            assertThat(entries).containsOnlyKeys("repo/", "repo/kept.txt");
            assertThat(entries.get("repo/kept.txt"))
                    .isEqualTo("input".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Test
        void shouldRejectDirectoryExceedingSizeLimit() throws Exception {
            var limitedManager = new SandboxWorkspaceManager(
                    fileOps, 50L * 1024 * 1024, 10L * 1024 * 1024, 1024, SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES);

            Files.write(tempDir.resolve("file1.txt"), new byte[600]);
            Files.write(tempDir.resolve("file2.txt"), new byte[600]);

            assertThatThrownBy(() -> limitedManager.injectDirectories(
                            CONTAINER_ID, Map.of(tempDir.toAbsolutePath().toString(), "/workspace/repo")))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("size limit");
        }

        @Test
        void shouldAcceptDirectoryAtExactSizeLimit() throws Exception {
            var limitedManager = new SandboxWorkspaceManager(
                    fileOps, 50L * 1024 * 1024, 10L * 1024 * 1024, 100, SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES);

            Files.write(tempDir.resolve("exact.txt"), new byte[100]);

            limitedManager.injectDirectories(
                    CONTAINER_ID, Map.of(tempDir.toAbsolutePath().toString(), "/workspace/repo"));

            verify(fileOps).copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));
        }

        @Test
        void shouldAcceptDirectoryWithinSizeLimit() throws Exception {
            var limitedManager = new SandboxWorkspaceManager(
                    fileOps, 50L * 1024 * 1024, 10L * 1024 * 1024, 4096, SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES);

            Files.write(tempDir.resolve("small.txt"), "hello".getBytes());

            limitedManager.injectDirectories(
                    CONTAINER_ID, Map.of(tempDir.toAbsolutePath().toString(), "/workspace/repo"));

            verify(fileOps).copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));
        }

        @Test
        void shouldInjectNestedSubdirectories() throws Exception {
            var limitedManager = new SandboxWorkspaceManager(
                    fileOps, 50L * 1024 * 1024, 10L * 1024 * 1024, 4096, SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES);

            Path subDir = Files.createDirectory(tempDir.resolve("sub"));
            Files.write(subDir.resolve("nested.txt"), "nested content".getBytes());
            Files.write(tempDir.resolve("root.txt"), "root content".getBytes());

            limitedManager.injectDirectories(
                    CONTAINER_ID, Map.of(tempDir.toAbsolutePath().toString(), "/workspace/repo"));

            verify(fileOps).copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));
        }

        @Test
        void shouldHaveReasonableEntryCountLimit() {
            assertThat(SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES).isEqualTo(500_000);
        }

        @Test
        void shouldSkipWhenDirectoryMountsNull() {
            manager.injectDirectories(CONTAINER_ID, null);

            verify(fileOps, never()).copyArchiveToContainer(any(), any(), any());
        }

        @Test
        void shouldSkipWhenMountsMapIsEmpty() {
            manager.injectDirectories(CONTAINER_ID, Map.of());

            verify(fileOps, never()).copyArchiveToContainer(any(), any(), any());
        }

        @Test
        void shouldRejectNullHostPath() {
            Map<String, String> mounts = new HashMap<>();
            mounts.put(null, "/container/path");

            assertThatThrownBy(() -> manager.injectDirectories(CONTAINER_ID, mounts))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("Host path must not be empty");
        }

        @Test
        void shouldRejectEmptyHostPath() {
            assertThatThrownBy(() -> manager.injectDirectories(CONTAINER_ID, Map.of("", "/container/path")))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("Host path must not be empty");
        }

        @Test
        void shouldRejectRelativeHostPath() {
            assertThatThrownBy(
                            () -> manager.injectDirectories(CONTAINER_ID, Map.of("relative/path", "/container/path")))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("Host path must be absolute");
        }

        @Test
        void shouldRejectNonExistentHostPath() {
            String nonExistent = tempDir.resolve("does-not-exist").toString();

            assertThatThrownBy(() -> manager.injectDirectories(CONTAINER_ID, Map.of(nonExistent, "/container/path")))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("Host path does not exist");
        }

        @Test
        void shouldRejectSymlinkHostPath() throws Exception {
            Path realDir = Files.createDirectory(tempDir.resolve("real-dir"));
            Path symlink = Files.createSymbolicLink(tempDir.resolve("symlink-dir"), realDir);

            assertThatThrownBy(() ->
                            manager.injectDirectories(CONTAINER_ID, Map.of(symlink.toString(), "/container/path")))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("Host path must not be a symlink");
        }

        @Test
        void shouldRejectNullContainerPath() {
            Map<String, String> mounts = new HashMap<>();
            mounts.put(tempDir.toString(), null);

            assertThatThrownBy(() -> manager.injectDirectories(CONTAINER_ID, mounts))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("Container path must not be empty");
        }

        @Test
        void shouldRejectEmptyContainerPath() {
            assertThatThrownBy(() -> manager.injectDirectories(CONTAINER_ID, Map.of(tempDir.toString(), "")))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("Container path must not be empty");
        }

        @Test
        void shouldRejectRelativeContainerPath() {
            assertThatThrownBy(() ->
                            manager.injectDirectories(CONTAINER_ID, Map.of(tempDir.toString(), "relative/container")))
                    .isInstanceOf(SandboxException.class)
                    .hasMessageContaining("Container path must be absolute");
        }

        @Test
        void shouldInjectValidDirectory() throws Exception {
            Path subDir = Files.createDirectory(tempDir.resolve("src"));
            Files.writeString(subDir.resolve("main.py"), "print('hello')");

            manager.injectDirectories(CONTAINER_ID, Map.of(tempDir.toString(), "/workspace/repo"));

            verify(fileOps).copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));
        }
    }

    private byte[] createTestTar(Map<String, byte[]> files) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (var entry : files.entrySet()) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(entry.getKey());
                tarEntry.setSize(entry.getValue().length);
                tar.putArchiveEntry(tarEntry);
                tar.write(entry.getValue());
                tar.closeArchiveEntry();
            }
            tar.finish();
        }
        return baos.toByteArray();
    }

    private byte[] createTestTarWithSymlink(String name, String linkTarget) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
            TarArchiveEntry entry = new TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK);
            entry.setLinkName(linkTarget);
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
            tar.finish();
        }
        return baos.toByteArray();
    }

    private byte[] createTestTarWithHardLink(String name, String linkTarget) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
            TarArchiveEntry entry = new TarArchiveEntry(name, TarArchiveEntry.LF_LINK);
            entry.setLinkName(linkTarget);
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
            tar.finish();
        }
        return baos.toByteArray();
    }

    private byte[] createTestTarWithDir(String fileName, byte[] content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            TarArchiveEntry dirEntry = new TarArchiveEntry("out/");
            tar.putArchiveEntry(dirEntry);
            tar.closeArchiveEntry();

            TarArchiveEntry fileEntry = new TarArchiveEntry("out/" + fileName);
            fileEntry.setSize(content.length);
            tar.putArchiveEntry(fileEntry);
            tar.write(content);
            tar.closeArchiveEntry();

            tar.finish();
        }
        return baos.toByteArray();
    }
}
