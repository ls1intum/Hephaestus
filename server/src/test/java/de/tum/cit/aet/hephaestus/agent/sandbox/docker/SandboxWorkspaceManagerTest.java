package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
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
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
        @DisplayName("should create tar archive and copy to container")
        void shouldInjectFiles() {
            Map<String, byte[]> files = Map.of(".prompt", "test prompt".getBytes(), "config.json", "{}".getBytes());

            manager.injectFiles(CONTAINER_ID, files);

            verify(fileOps).copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));
        }

        @Test
        @DisplayName("emits work/ ancestor dirs as uid-1000 (writable) and leaves inputs/ to root auto-create")
        void shouldMakeWorkRegionWritableButNotInputs() throws IOException {
            // ADR 0020: read-only vs writable by LOCATION. The agent + precompute write under work/ as the
            // container uid (1000); a root-owned work/ (Docker's default for auto-created intermediate dirs)
            // would deny `mkdir -p work/precompute-out` and scratch writes. inputs/ must stay root (RO).
            Map<String, byte[]> files = new HashMap<>();
            files.put("inputs/context/diff.patch", "d".getBytes());
            files.put("work/analysis/practices/.gitkeep", new byte[0]);
            files.put("work/precompute/practices/foo.ts", "x".getBytes());

            manager.injectFiles(CONTAINER_ID, files);

            org.mockito.ArgumentCaptor<InputStream> tar = org.mockito.ArgumentCaptor.forClass(InputStream.class);
            verify(fileOps).copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), tar.capture());

            Map<String, Long> dirUid = new HashMap<>();
            try (var tis = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(tar.getValue())) {
                TarArchiveEntry e;
                while ((e = tis.getNextEntry()) != null) {
                    if (e.isDirectory()) {
                        dirUid.put(e.getName(), e.getLongUserId());
                    }
                }
            }

            // Every work/ ancestor is pre-created and owned by the container uid.
            assertThat(dirUid).containsKeys(
                "work/",
                "work/analysis/",
                "work/analysis/practices/",
                "work/precompute/",
                "work/precompute/practices/"
            );
            assertThat(dirUid.values()).allMatch(uid -> uid == 1000L);
            // inputs/ dirs are deliberately NOT emitted — Docker auto-creates them as root, which IS the
            // read-only guarantee (uid 1000 cannot create files in a root-owned directory).
            assertThat(dirUid).doesNotContainKey("inputs/").doesNotContainKey("inputs/context/");
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
        void shouldRejectOversizedInput() {
            // Use a tiny input limit (1 KB) to avoid allocating ~50 MB in CI, mirroring the other size tests.
            var limitedManager = new SandboxWorkspaceManager(
                fileOps,
                SandboxWorkspaceManager.MAX_OUTPUT_BYTES,
                SandboxWorkspaceManager.MAX_SINGLE_FILE_BYTES,
                1024,
                SandboxWorkspaceManager.MAX_DIRECTORY_BYTES,
                SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES
            );
            byte[] largeFile = new byte[1025]; // 1 byte over the 1 KB input limit
            Map<String, byte[]> files = Map.of("huge.bin", largeFile);

            assertThatThrownBy(() -> limitedManager.injectFiles(CONTAINER_ID, files))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("maximum size limit");
        }
    }

    @Nested
    class CollectOutput {

        @Test
        void shouldExtractFiles() throws Exception {
            byte[] tarBytes = createTestTar(Map.of("out/result.json", "{\"status\":\"ok\"}".getBytes()));
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out")).thenReturn(
                new ByteArrayInputStream(tarBytes)
            );

            Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/out");

            assertThat(output).containsKey("result.json");
            assertThat(new String(output.get("result.json"))).isEqualTo("{\"status\":\"ok\"}");
        }

        @Test
        @DisplayName("should return empty map when docker cp fails")
        void shouldReturnEmptyOnFailure() {
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out")).thenThrow(
                new SandboxException("No such path")
            );

            Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/out");

            assertThat(output).isEmpty();
        }

        @Test
        void shouldEnforceOutputSizeLimit() throws Exception {
            // Use a small limit (1 KB) for this test to avoid allocating megabytes in CI
            var limitedManager = new SandboxWorkspaceManager(
                fileOps,
                1024,
                SandboxWorkspaceManager.MAX_SINGLE_FILE_BYTES,
                SandboxWorkspaceManager.MAX_DIRECTORY_BYTES,
                SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES
            );

            byte[] largeContent = new byte[800]; // 800 bytes
            byte[] secondContent = new byte[500]; // 500 bytes — total 1300 > 1024

            byte[] tarBytes = createTestTar(Map.of("out/first.bin", largeContent, "out/second.bin", secondContent));
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out")).thenReturn(
                new ByteArrayInputStream(tarBytes)
            );

            Map<String, byte[]> output = limitedManager.collectOutput(CONTAINER_ID, "/workspace/out");

            // Only one file should be collected — the second pushes past the limit
            assertThat(output).hasSize(1);
        }

        @Test
        void shouldSkipTraversalPathsInOutput() throws Exception {
            byte[] tarBytes = createTestTar(
                Map.of("out/../../../etc/passwd", "malicious".getBytes(), "out/safe.txt", "safe content".getBytes())
            );
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out")).thenReturn(
                new ByteArrayInputStream(tarBytes)
            );

            Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/out");

            // Only the safe file should be collected; the traversal path should be skipped
            assertThat(output).hasSize(1);
            assertThat(output).containsKey("safe.txt");
            assertThat(output).doesNotContainKey("../../../etc/passwd");
        }

        @Test
        @DisplayName("should skip symbolic links in output archive")
        void shouldSkipSymlinks() throws Exception {
            byte[] tarBytes = createTestTarWithSymlink("out/evil", "/etc/shadow");
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out")).thenReturn(
                new ByteArrayInputStream(tarBytes)
            );

            Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/out");

            assertThat(output).isEmpty();
        }

        @Test
        void shouldSkipHardLinks() throws Exception {
            byte[] tarBytes = createTestTarWithHardLink("out/link", "out/target");
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out")).thenReturn(
                new ByteArrayInputStream(tarBytes)
            );

            Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/out");

            assertThat(output).isEmpty();
        }

        @Test
        void shouldSkipOversizedSingleFile() throws Exception {
            // Use a manager with a 10-byte per-file limit to avoid allocating megabytes in tests
            var limitedManager = new SandboxWorkspaceManager(
                fileOps,
                10_000,
                10,
                SandboxWorkspaceManager.MAX_DIRECTORY_BYTES,
                SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES
            );

            byte[] smallContent = "small".getBytes(); // 5 bytes — under limit
            byte[] oversizedContent = "this is way too big".getBytes(); // 19 bytes — over 10-byte limit

            byte[] tarBytes = createTestTar(Map.of("out/small.txt", smallContent, "out/toobig.txt", oversizedContent));
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out")).thenReturn(
                new ByteArrayInputStream(tarBytes)
            );

            Map<String, byte[]> output = limitedManager.collectOutput(CONTAINER_ID, "/workspace/out");

            // Only the small file should be collected — the oversized one is skipped
            assertThat(output).containsKey("small.txt");
            assertThat(output).doesNotContainKey("toobig.txt");
        }

        @Test
        @DisplayName("should skip directory entries in tar")
        void shouldSkipDirectories() throws Exception {
            byte[] tarBytes = createTestTarWithDir("result.json", "{}".getBytes());
            when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/out")).thenReturn(
                new ByteArrayInputStream(tarBytes)
            );

            Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/out");

            assertThat(output).containsKey("result.json");
            assertThat(output).hasSize(1);
        }
    }

    @Nested
    class InjectDirectories {

        @TempDir
        Path tempDir;

        // Size limit tests

        @Test
        void shouldRejectDirectoryExceedingSizeLimit() throws Exception {
            // Use a tiny limit (1 KB) to avoid allocating megabytes in CI
            var limitedManager = new SandboxWorkspaceManager(
                fileOps,
                50L * 1024 * 1024,
                10L * 1024 * 1024,
                1024,
                SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES
            );

            // Create two files totaling > 1024 bytes
            Files.write(tempDir.resolve("file1.txt"), new byte[600]);
            Files.write(tempDir.resolve("file2.txt"), new byte[600]);

            assertThatThrownBy(() ->
                limitedManager.injectDirectories(
                    CONTAINER_ID,
                    Map.of(tempDir.toAbsolutePath().toString(), "/workspace/repo")
                )
            )
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("size limit");
        }

        @Test
        void shouldAcceptDirectoryAtExactSizeLimit() throws Exception {
            // Exactly 100 bytes of content — limit is 100
            var limitedManager = new SandboxWorkspaceManager(
                fileOps,
                50L * 1024 * 1024,
                10L * 1024 * 1024,
                100,
                SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES
            );

            Files.write(tempDir.resolve("exact.txt"), new byte[100]);

            limitedManager.injectDirectories(
                CONTAINER_ID,
                Map.of(tempDir.toAbsolutePath().toString(), "/workspace/repo")
            );

            verify(fileOps).copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));
        }

        @Test
        void shouldAcceptDirectoryWithinSizeLimit() throws Exception {
            var limitedManager = new SandboxWorkspaceManager(
                fileOps,
                50L * 1024 * 1024,
                10L * 1024 * 1024,
                4096,
                SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES
            );

            Files.write(tempDir.resolve("small.txt"), "hello".getBytes());

            limitedManager.injectDirectories(
                CONTAINER_ID,
                Map.of(tempDir.toAbsolutePath().toString(), "/workspace/repo")
            );

            verify(fileOps).copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));
        }

        @Test
        void shouldInjectNestedSubdirectories() throws Exception {
            var limitedManager = new SandboxWorkspaceManager(
                fileOps,
                50L * 1024 * 1024,
                10L * 1024 * 1024,
                4096,
                SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES
            );

            // Create a nested directory structure: sub/nested.txt
            Path subDir = Files.createDirectory(tempDir.resolve("sub"));
            Files.write(subDir.resolve("nested.txt"), "nested content".getBytes());
            Files.write(tempDir.resolve("root.txt"), "root content".getBytes());

            limitedManager.injectDirectories(
                CONTAINER_ID,
                Map.of(tempDir.toAbsolutePath().toString(), "/workspace/repo")
            );

            verify(fileOps).copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));
        }

        @Test
        void shouldHaveReasonableEntryCountLimit() {
            assertThat(SandboxWorkspaceManager.MAX_DIRECTORY_ENTRIES).isEqualTo(500_000);
        }

        // Validation tests (from main)

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
            assertThatThrownBy(() ->
                manager.injectDirectories(CONTAINER_ID, Map.of("relative/path", "/container/path"))
            )
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
                manager.injectDirectories(CONTAINER_ID, Map.of(symlink.toString(), "/container/path"))
            )
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
                manager.injectDirectories(CONTAINER_ID, Map.of(tempDir.toString(), "relative/container"))
            )
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

            // Add directory entry
            TarArchiveEntry dirEntry = new TarArchiveEntry("out/");
            tar.putArchiveEntry(dirEntry);
            tar.closeArchiveEntry();

            // Add file entry
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
