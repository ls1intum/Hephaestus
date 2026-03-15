package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("SandboxWorkspaceManager")
class SandboxWorkspaceManagerTest extends BaseUnitTest {

  @Mock private DockerFileOperations fileOps;

  private SandboxWorkspaceManager manager;

  private static final String CONTAINER_ID = "abc123";

  @BeforeEach
  void setUp() {
    manager = new SandboxWorkspaceManager(fileOps);
  }

  @Nested
  @DisplayName("injectFiles")
  class InjectFiles {

    @Test
    @DisplayName("should create tar archive and copy to container")
    void shouldInjectFiles() {
      Map<String, byte[]> files =
          Map.of(".prompt", "test prompt".getBytes(), "config.json", "{}".getBytes());

      manager.injectFiles(CONTAINER_ID, files);

      verify(fileOps)
          .copyArchiveToContainer(eq(CONTAINER_ID), eq("/workspace"), any(InputStream.class));
    }

    @Test
    @DisplayName("should skip injection when files map is empty")
    void shouldSkipWhenEmpty() {
      manager.injectFiles(CONTAINER_ID, Map.of());

      verify(fileOps, never()).copyArchiveToContainer(any(), any(), any());
    }

    @Test
    @DisplayName("should skip injection when files map is null")
    void shouldSkipWhenNull() {
      manager.injectFiles(CONTAINER_ID, null);

      verify(fileOps, never()).copyArchiveToContainer(any(), any(), any());
    }

    @Test
    @DisplayName("should reject paths with directory traversal")
    void shouldRejectDirectoryTraversal() {
      Map<String, byte[]> files = Map.of("../../etc/passwd", "malicious".getBytes());

      assertThatThrownBy(() -> manager.injectFiles(CONTAINER_ID, files))
          .isInstanceOf(SandboxException.class)
          .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("should reject absolute paths")
    void shouldRejectAbsolutePaths() {
      Map<String, byte[]> files = Map.of("/etc/shadow", "malicious".getBytes());

      assertThatThrownBy(() -> manager.injectFiles(CONTAINER_ID, files))
          .isInstanceOf(SandboxException.class)
          .hasMessageContaining("Absolute");
    }

    @Test
    @DisplayName("should reject input exceeding MAX_INPUT_BYTES")
    void shouldRejectOversizedInput() {
      byte[] largeFile = new byte[(int) (SandboxWorkspaceManager.MAX_INPUT_BYTES + 1)];
      Map<String, byte[]> files = Map.of("huge.bin", largeFile);

      assertThatThrownBy(() -> manager.injectFiles(CONTAINER_ID, files))
          .isInstanceOf(SandboxException.class)
          .hasMessageContaining("maximum size limit");
    }
  }

  @Nested
  @DisplayName("collectOutput")
  class CollectOutput {

    @Test
    @DisplayName("should extract files from tar archive")
    void shouldExtractFiles() throws Exception {
      byte[] tarBytes =
          createTestTar(Map.of(".output/result.json", "{\"status\":\"ok\"}".getBytes()));
      when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/.output"))
          .thenReturn(new ByteArrayInputStream(tarBytes));

      Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/.output");

      assertThat(output).containsKey("result.json");
      assertThat(new String(output.get("result.json"))).isEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    @DisplayName("should return empty map when docker cp fails")
    void shouldReturnEmptyOnFailure() {
      when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/.output"))
          .thenThrow(new SandboxException("No such path"));

      Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/.output");

      assertThat(output).isEmpty();
    }

    @Test
    @DisplayName("should stop collecting when output size limit is exceeded")
    void shouldEnforceOutputSizeLimit() throws Exception {
      // Use a small limit (1 KB) for this test to avoid allocating megabytes in CI
      var limitedManager = new SandboxWorkspaceManager(fileOps, 1024);

      byte[] largeContent = new byte[800]; // 800 bytes
      byte[] secondContent = new byte[500]; // 500 bytes — total 1300 > 1024

      byte[] tarBytes =
          createTestTar(
              Map.of(".output/first.bin", largeContent, ".output/second.bin", secondContent));
      when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/.output"))
          .thenReturn(new ByteArrayInputStream(tarBytes));

      Map<String, byte[]> output = limitedManager.collectOutput(CONTAINER_ID, "/workspace/.output");

      // Only one file should be collected — the second pushes past the limit
      assertThat(output).hasSize(1);
    }

    @Test
    @DisplayName("should skip traversal paths in output archive")
    void shouldSkipTraversalPathsInOutput() throws Exception {
      byte[] tarBytes =
          createTestTar(
              Map.of(
                  ".output/../../../etc/passwd",
                  "malicious".getBytes(),
                  ".output/safe.txt",
                  "safe content".getBytes()));
      when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/.output"))
          .thenReturn(new ByteArrayInputStream(tarBytes));

      Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/.output");

      // Only the safe file should be collected; the traversal path should be skipped
      assertThat(output).hasSize(1);
      assertThat(output).containsKey("safe.txt");
      assertThat(output).doesNotContainKey("../../../etc/passwd");
    }

    @Test
    @DisplayName("should skip symbolic links in output archive")
    void shouldSkipSymlinks() throws Exception {
      byte[] tarBytes = createTestTarWithSymlink(".output/evil", "/etc/shadow");
      when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/.output"))
          .thenReturn(new ByteArrayInputStream(tarBytes));

      Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/.output");

      assertThat(output).isEmpty();
    }

    @Test
    @DisplayName("should skip hard links in output archive")
    void shouldSkipHardLinks() throws Exception {
      byte[] tarBytes = createTestTarWithHardLink(".output/link", ".output/target");
      when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/.output"))
          .thenReturn(new ByteArrayInputStream(tarBytes));

      Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/.output");

      assertThat(output).isEmpty();
    }

    @Test
    @DisplayName("should skip directory entries in tar")
    void shouldSkipDirectories() throws Exception {
      byte[] tarBytes = createTestTarWithDir("result.json", "{}".getBytes());
      when(fileOps.copyArchiveFromContainer(CONTAINER_ID, "/workspace/.output"))
          .thenReturn(new ByteArrayInputStream(tarBytes));

      Map<String, byte[]> output = manager.collectOutput(CONTAINER_ID, "/workspace/.output");

      assertThat(output).containsKey("result.json");
      assertThat(output).hasSize(1);
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
      TarArchiveEntry dirEntry = new TarArchiveEntry(".output/");
      tar.putArchiveEntry(dirEntry);
      tar.closeArchiveEntry();

      // Add file entry
      TarArchiveEntry fileEntry = new TarArchiveEntry(".output/" + fileName);
      fileEntry.setSize(content.length);
      tar.putArchiveEntry(fileEntry);
      tar.write(content);
      tar.closeArchiveEntry();

      tar.finish();
    }
    return baos.toByteArray();
  }
}
