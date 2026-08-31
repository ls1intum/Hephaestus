package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxInfrastructureException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages file injection and output collection for sandbox containers.
 *
 * <p>Uses {@code docker cp} (tar archive API) for all file transfers — no bind mounts. This works
 * identically for local and remote Docker daemons, enabling future multi-node runner support.
 */
public class SandboxWorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(SandboxWorkspaceManager.class);

    /** Maximum total size of collected output files (50 MB). */
    static final long MAX_OUTPUT_BYTES = 50L * 1024 * 1024;

    /** Maximum size of a single output file (10 MB). */
    static final long MAX_SINGLE_FILE_BYTES = 10L * 1024 * 1024;

    /** Maximum total size of a directory injected via tar (1 GB). */
    static final long MAX_DIRECTORY_BYTES = 1024L * 1024 * 1024;

    /** Maximum number of entries (files + directories) in a directory injection. */
    static final int MAX_DIRECTORY_ENTRIES = 500_000;

    /** Maximum directory tree depth for walk operations. */
    static final int MAX_WALK_DEPTH = 50;

    private final DockerFileOperations fileOps;
    private final long maxOutputBytes;
    private final long maxSingleFileBytes;
    private final long maxDirectoryBytes;
    private final int maxDirectoryEntries;

    public SandboxWorkspaceManager(DockerFileOperations fileOps) {
        this(fileOps, MAX_OUTPUT_BYTES, MAX_SINGLE_FILE_BYTES, MAX_DIRECTORY_BYTES, MAX_DIRECTORY_ENTRIES);
    }

    /** Package-private constructor for testing with custom limits. */
    SandboxWorkspaceManager(
            DockerFileOperations fileOps,
            long maxOutputBytes,
            long maxSingleFileBytes,
            long maxDirectoryBytes,
            int maxDirectoryEntries) {
        this.fileOps = fileOps;
        this.maxOutputBytes = maxOutputBytes;
        this.maxSingleFileBytes = maxSingleFileBytes;
        this.maxDirectoryBytes = maxDirectoryBytes;
        this.maxDirectoryEntries = maxDirectoryEntries;
    }

    /**
     * Inject files into a container via {@code docker cp}.
     *
     * @param containerId the target container (must be created but can be stopped)
     * @param files map of relative paths to file contents
     */
    public void injectFiles(String containerId, @org.jspecify.annotations.Nullable Map<String, byte[]> files) {
        injectFiles(containerId, files, Map.of());
    }

    /**
     * Inject files into a container via {@code docker cp}, from memory and from disk.
     *
     * <p>The archive is written to a temporary file rather than held in memory, so heap use is
     * independent of how much content is staged — on-disk entries travel disk-to-socket without this
     * process ever holding their bytes.
     *
     * @param containerId the target container (must be created but can be stopped)
     * @param files map of relative paths to file contents held in memory
     * @param filesOnDisk map of relative paths to host files, streamed rather than read
     * @implNote The archive stream is valid only for the duration of the {@code copyArchiveToContainer}
     *     call; callers and test doubles must consume it eagerly rather than retain it.
     */
    public void injectFiles(
            String containerId,
            @org.jspecify.annotations.Nullable Map<String, byte[]> files,
            @org.jspecify.annotations.Nullable Map<String, Path> filesOnDisk) {
        Map<String, byte[]> inMemory = files == null ? Map.of() : files;
        Map<String, Path> onDisk = filesOnDisk == null ? Map.of() : filesOnDisk;
        if (inMemory.isEmpty() && onDisk.isEmpty()) {
            return;
        }

        Path tarFile = null;
        try {
            tarFile = Files.createTempFile("sandbox-inputs-", ".tar");
            writeInputTar(tarFile, inMemory, onDisk);
            try (InputStream tarStream = Files.newInputStream(tarFile)) {
                fileOps.copyArchiveToContainer(containerId, "/workspace", tarStream);
            }
            log.debug("Injected {} files into container {}", inMemory.size() + onDisk.size(), containerId);
        } catch (IOException e) {
            throw new SandboxInfrastructureException("Failed to inject files into container: " + containerId, e);
        } finally {
            deleteQuietly(tarFile);
        }
    }

    private static void deleteQuietly(@Nullable Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete temporary archive {}", path, e);
        }
    }

    /**
     * Inject host directories into a container via {@code docker cp}.
     *
     * <p>Manually creates a tar archive from the host directory and streams it via the Docker API.
     * This avoids docker-java's internal tar creation which has a commons-compress version conflict.
     * Works identically for local and remote Docker daemons.
     *
     * @param containerId the target container (must be created but can be stopped)
     * @param directoryMounts map of host path to container path
     */
    public void injectDirectories(
            String containerId, @org.jspecify.annotations.Nullable Map<String, String> directoryMounts) {
        if (directoryMounts == null || directoryMounts.isEmpty()) {
            return;
        }
        for (var entry : directoryMounts.entrySet()) {
            String hostPath = entry.getKey();
            String containerPath = entry.getValue();
            validateDirectoryMount(hostPath, containerPath);
            injectDirectoryViaTar(containerId, hostPath, containerPath);
            log.debug("Injected directory into container {}: {} -> {}", containerId, hostPath, containerPath);
        }
    }

    /**
     * Walk a host directory, create a tar archive on a temp file, and stream it into the container.
     *
     * <p>Uses a temporary file instead of {@link ByteArrayOutputStream} to avoid loading the entire
     * archive into JVM heap. Memory usage is O(buffer_size) regardless of directory size, since each
     * file is streamed through a fixed buffer. The docker-java transport streams the tar lazily via
     * chunked transfer encoding — no additional buffering occurs downstream.
     *
     * <p>The tar entries are prefixed with the final path component so that extracting at the parent
     * of containerPath produces the correct layout.
     */
    private void injectDirectoryViaTar(String containerId, String hostPath, String containerPath) {
        Path hostDir = Path.of(hostPath);
        Path containerParent = Path.of(containerPath).getParent();
        String dirName = Path.of(containerPath).getFileName().toString();
        if (containerParent == null) {
            containerParent = Path.of("/");
        }

        Path tempTar = null;
        try {
            tempTar = Files.createTempFile("hephaestus-inject-", ".tar");

            // Phase 1: Walk directory and write tar to temp file.
            // Memory: O(COPY_BUFFER_SIZE) — each file is streamed, never loaded whole.
            writeTarToFile(tempTar, hostDir, dirName, hostPath);

            // Phase 2: Stream tar from disk to Docker daemon.
            // docker-java wraps this in InputStreamEntity (chunked transfer) — no heap copy.
            try (InputStream tarStream = new BufferedInputStream(Files.newInputStream(tempTar))) {
                fileOps.copyArchiveToContainer(containerId, containerParent.toString(), tarStream);
            }
        } catch (IOException e) {
            throw new SandboxInfrastructureException(
                    "Failed to inject directory " + hostPath + " into container " + containerId, e);
        } finally {
            if (tempTar != null) {
                try {
                    Files.deleteIfExists(tempTar);
                } catch (IOException e) {
                    log.warn("Failed to delete temp tar file {}: {}", tempTar, e.getMessage());
                }
            }
        }
    }

    /** Buffer size for streaming file contents into the tar archive (64 KB). */
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    /**
     * Write a tar archive of the given directory to a file on disk. Files are streamed through a
     * fixed-size buffer rather than loaded entirely into memory.
     */
    private void writeTarToFile(Path tarFile, Path hostDir, String dirName, String hostPath) throws IOException {
        long[] totalBytes = {0};
        int[] entryCount = {0};

        try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(tarFile), COPY_BUFFER_SIZE);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(fileOut);
                Stream<Path> paths = Files.walk(hostDir, MAX_WALK_DEPTH)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

            paths.forEach(path -> {
                try {
                    entryCount[0]++;
                    if (entryCount[0] > maxDirectoryEntries) {
                        throw new SandboxException("Directory injection exceeds entry count limit ("
                                + maxDirectoryEntries + "): " + hostPath);
                    }

                    String relativePath = hostDir.relativize(path).toString();
                    String entryName = relativePath.isEmpty() ? dirName : dirName + "/" + relativePath;

                    if (Files.isDirectory(path)) {
                        TarArchiveEntry dirEntry = new TarArchiveEntry(entryName + "/");
                        dirEntry.setModTime(Files.getLastModifiedTime(path).toMillis());
                        dirEntry.setUserId(1000);
                        dirEntry.setGroupId(1000);
                        tar.putArchiveEntry(dirEntry);
                        tar.closeArchiveEntry();
                    } else if (Files.isRegularFile(path)) {
                        long fileSize = Files.size(path);
                        totalBytes[0] += fileSize;
                        if (totalBytes[0] > maxDirectoryBytes) {
                            throw new SandboxException("Directory injection exceeds size limit (" + maxDirectoryBytes
                                    + " bytes): " + hostPath);
                        }

                        TarArchiveEntry fileEntry = new TarArchiveEntry(entryName);
                        fileEntry.setSize(fileSize);
                        fileEntry.setModTime(Files.getLastModifiedTime(path).toMillis());
                        fileEntry.setUserId(1000);
                        fileEntry.setGroupId(1000);
                        tar.putArchiveEntry(fileEntry);

                        // Stream EXACTLY the declared number of bytes (not transferTo's open-ended copy) so a
                        // source file mutated between the stat above and this read fails with a clear
                        // concurrent-modification diagnostic rather than an opaque tar size-mismatch at finish().
                        long written = copyExactly(path, tar, fileSize);
                        if (written != fileSize) {
                            throw new SandboxException("Source file changed during injection (declared " + fileSize
                                    + " bytes, read "
                                    + written
                                    + "): "
                                    + path);
                        }
                        tar.closeArchiveEntry();
                    }
                    // Symlinks are silently skipped: Files.walk() does not follow them by default,
                    // and Files.isRegularFile/isDirectory return false for unresolved symlinks.
                } catch (IOException e) {
                    throw new SandboxInfrastructureException("Failed to add file to tar: " + path, e);
                }
            });

            tar.finish();
        }
    }

    /**
     * Copy at most {@code limit} bytes from {@code source} into {@code out}, returning the number actually
     * read. Bounding the copy to the declared size means a source file that GREW since it was stat'd is
     * truncated to the declared length (the tar entry stays valid) and one that SHRANK returns fewer bytes
     * (the caller raises a clear concurrent-modification error) — never an open-ended {@code transferTo}
     * that would over- or under-run the entry's declared size.
     */
    private static long copyExactly(Path source, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long total = 0;
        try (InputStream in = Files.newInputStream(source)) {
            while (total < limit) {
                int toRead = (int) Math.min(buffer.length, limit - total);
                int read = in.read(buffer, 0, toRead);
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                total += read;
            }
        }
        return total;
    }

    /**
     * Collect output files from a container via {@code docker cp}.
     *
     * @param containerId the source container
     * @param outputPath path inside the container (e.g. {@code /workspace/.output})
     * @return map of relative file paths to contents
     */
    public Map<String, byte[]> collectOutput(String containerId, String outputPath) {
        Map<String, byte[]> result = new HashMap<>();
        long totalBytes = 0;

        try (InputStream tarStream = fileOps.copyArchiveFromContainer(containerId, outputPath)) {
            try (TarArchiveInputStream tar = new TarArchiveInputStream(tarStream)) {
                TarArchiveEntry entry;
                while ((entry = tar.getNextTarEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    // Reject symlinks and hard links — a malicious container could
                    // create a symlink pointing to /etc/shadow or injected secrets
                    if (entry.isSymbolicLink() || entry.isLink()) {
                        log.warn("Skipping symbolic/hard link in output archive: {}", entry.getName());
                        continue;
                    }
                    // Normalize the RAW entry name first so the traversal check is authoritative over the
                    // name the container actually emitted — stripping the leading component before
                    // normalizing would silently reinterpret a "../out/x" entry as a benign "out/x".
                    String rawName = entry.getName();
                    Path rawNormalized = Path.of(rawName).normalize();
                    if (rawNormalized.isAbsolute() || rawNormalized.startsWith("..")) {
                        log.warn("Skipping unsafe path in output archive: {}", rawName);
                        continue;
                    }
                    // Strip the leading directory component (docker cp includes the parent dir name).
                    String name = rawNormalized.toString();
                    int slashIndex = name.indexOf('/');
                    if (slashIndex >= 0) {
                        name = name.substring(slashIndex + 1);
                    }
                    if (name.isEmpty()) {
                        continue;
                    }
                    // Per-file size guard: reject files with declared size > limit or negative
                    // (a long->int cast on a crafted size > 2GB would produce a negative int).
                    if (entry.getSize() < 0 || entry.getSize() > maxSingleFileBytes) {
                        log.warn("Skipping oversized file in output archive: name={}, size={}", name, entry.getSize());
                        continue;
                    }
                    if (totalBytes + entry.getSize() > maxOutputBytes) {
                        log.warn(
                                "Output size limit exceeded ({} bytes) for container {}, skipping remaining files",
                                maxOutputBytes,
                                containerId);
                        break;
                    }
                    byte[] content = tar.readNBytes((int) entry.getSize());
                    totalBytes += content.length;
                    result.put(name, content);
                }
            }
            log.debug(
                    "Collected {} output files ({} bytes) from container {} at {}",
                    result.size(),
                    totalBytes,
                    containerId,
                    outputPath);
        } catch (SandboxException e) {
            // docker cp failed — output directory may not exist (agent didn't write output)
            log.warn("Failed to collect output from container {} at {}: {}", containerId, outputPath, e.getMessage());
        } catch (IOException e) {
            log.warn("Failed to read output tar from container {}: {}", containerId, e.getMessage());
        }

        return result;
    }

    // Internal helpers

    private void writeInputTar(Path tarFile, Map<String, byte[]> files, Map<String, Path> filesOnDisk)
            throws IOException {
        try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(tarFile), COPY_BUFFER_SIZE);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(fileOut)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

            Set<String> allPaths = new LinkedHashSet<>();
            files.keySet().stream().map(SandboxWorkspaceManager::validatePath).forEach(allPaths::add);
            filesOnDisk.keySet().stream()
                    .map(SandboxWorkspaceManager::validatePath)
                    .forEach(allPaths::add);
            for (String dir : ancestorDirs(allPaths)) {
                TarArchiveEntry dirEntry = new TarArchiveEntry(dir + "/");
                dirEntry.setModTime(System.currentTimeMillis());
                boolean writable = isWritableRegion(dir + "/");
                dirEntry.setUserId(writable ? 1000 : 0);
                dirEntry.setGroupId(writable ? 1000 : 0);
                dirEntry.setMode(writable ? 0755 : 0555);
                tar.putArchiveEntry(dirEntry);
                tar.closeArchiveEntry();
            }

            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                TarArchiveEntry tarEntry = newInputEntry(validatePath(entry.getKey()), entry.getValue().length);
                tar.putArchiveEntry(tarEntry);
                tar.write(entry.getValue());
                tar.closeArchiveEntry();
            }

            for (Map.Entry<String, Path> entry : filesOnDisk.entrySet()) {
                Path source = entry.getValue();
                long fileSize = Files.size(source);
                TarArchiveEntry tarEntry = newInputEntry(validatePath(entry.getKey()), fileSize);
                tar.putArchiveEntry(tarEntry);
                long written = copyExactly(source, tar, fileSize);
                if (written != fileSize) {
                    throw new SandboxException("Source file changed during injection (declared " + fileSize
                            + " bytes, read "
                            + written
                            + "): "
                            + source);
                }
                tar.closeArchiveEntry();
            }

            tar.finish();
        }
    }

    private static TarArchiveEntry newInputEntry(String safePath, long size) {
        TarArchiveEntry entry = new TarArchiveEntry(safePath);
        entry.setSize(size);
        entry.setModTime(System.currentTimeMillis());
        boolean writable = isWritableRegion(safePath);
        entry.setUserId(writable ? 1000 : 0);
        entry.setGroupId(writable ? 1000 : 0);
        entry.setMode(writable ? 0644 : 0444);
        return entry;
    }

    private static boolean isWritableRegion(String path) {
        return (path.startsWith(SandboxLayout.WORK_PREFIX)
                || path.startsWith(SandboxLayout.PI_AGENT_PREFIX)
                || path.startsWith(SandboxLayout.SESSIONS_DIR_PREFIX)
                || path.startsWith(SandboxLayout.OUTPUT_PREFIX));
    }

    private static SortedSet<String> ancestorDirs(Set<String> keys) {
        SortedSet<String> dirs = new TreeSet<>();
        for (String key : keys) {
            for (int slash = key.indexOf('/'); slash >= 0; slash = key.indexOf('/', slash + 1)) {
                dirs.add(key.substring(0, slash));
            }
        }
        return dirs;
    }

    /**
     * Validate a directory mount path pair. Rejects relative paths, non-existent paths, and
     * symlinks (prevents symlink escape attacks).
     */
    private static void validateDirectoryMount(String hostPath, String containerPath) {
        if (hostPath == null || hostPath.isEmpty()) {
            throw new SandboxException("Host path must not be empty");
        }
        if (containerPath == null || containerPath.isEmpty()) {
            throw new SandboxException("Container path must not be empty");
        }
        Path host = Path.of(hostPath);
        if (!host.isAbsolute()) {
            throw new SandboxException("Host path must be absolute: " + hostPath);
        }
        if (!Files.exists(host)) {
            throw new SandboxException("Host path does not exist: " + hostPath);
        }
        if (Files.isSymbolicLink(host)) {
            throw new SandboxException("Host path must not be a symlink: " + hostPath);
        }
        Path container = Path.of(containerPath);
        if (!container.isAbsolute()) {
            throw new SandboxException("Container path must be absolute: " + containerPath);
        }
    }

    /**
     * Validate a file path to prevent directory traversal attacks (tar-slip).
     *
     * <p>Rejects absolute paths and paths containing {@code ..} components.
     *
     * @param path the path to validate
     * @return the normalized path
     * @throws SandboxException if the path is unsafe
     */
    private static String validatePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new SandboxException("File path must not be empty");
        }
        Path normalized = Path.of(path).normalize();
        if (normalized.isAbsolute()) {
            throw new SandboxException("Absolute paths are not allowed: " + path);
        }
        if (normalized.startsWith("..")) {
            throw new SandboxException("Path traversal detected: " + path);
        }
        return normalized.toString();
    }
}
