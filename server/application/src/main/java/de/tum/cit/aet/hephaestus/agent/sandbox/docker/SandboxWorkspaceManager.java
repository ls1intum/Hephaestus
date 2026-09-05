package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxOutputArchive;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxInfrastructureException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Transfers sandbox files through the Docker archive API. */
public class SandboxWorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(SandboxWorkspaceManager.class);

    static final long MAX_OUTPUT_BYTES = SandboxOutputArchive.MAX_OUTPUT_BYTES;

    static final long MAX_SINGLE_FILE_BYTES = SandboxOutputArchive.MAX_SINGLE_FILE_BYTES;

    static final long MAX_DIRECTORY_BYTES = 1024L * 1024 * 1024;

    static final int MAX_DIRECTORY_ENTRIES = 500_000;

    static final int MAX_WALK_DEPTH = 50;

    private final DockerFileOperations fileOps;
    private final long maxOutputBytes;
    private final long maxSingleFileBytes;
    private final long maxDirectoryBytes;
    private final int maxDirectoryEntries;

    public SandboxWorkspaceManager(DockerFileOperations fileOps) {
        this(fileOps, MAX_OUTPUT_BYTES, MAX_SINGLE_FILE_BYTES, MAX_DIRECTORY_BYTES, MAX_DIRECTORY_ENTRIES);
    }

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
     * <p>Stages the archive on disk to avoid retaining on-disk file contents in heap.
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

    /** Prefix entries with the destination basename so extraction at its parent preserves the layout. */
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

            writeTarToFile(tempTar, hostDir, dirName, hostPath);

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

    private static final int COPY_BUFFER_SIZE = 64 * 1024;

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

                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        TarArchiveEntry dirEntry = new TarArchiveEntry(entryName + "/");
                        dirEntry.setModTime(Files.getLastModifiedTime(path).toMillis());
                        dirEntry.setUserId(1000);
                        dirEntry.setGroupId(1000);
                        tar.putArchiveEntry(dirEntry);
                        tar.closeArchiveEntry();
                    } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
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

                        long written = copyFilePrefix(path, tar, fileSize);
                        if (written != fileSize) {
                            throw new SandboxException("Source file changed during injection (declared " + fileSize
                                    + " bytes, read "
                                    + written
                                    + "): "
                                    + path);
                        }
                        tar.closeArchiveEntry();
                    }
                } catch (IOException e) {
                    throw new SandboxInfrastructureException("Failed to add file to tar: " + path, e);
                }
            });

            tar.finish();
        }
    }

    private static long copyFilePrefix(Path source, OutputStream out, long limit) throws IOException {
        try (InputStream in = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
            return IOUtils.copyLarge(in, out, 0, limit);
        }
    }

    /**
     * Collect output files from a container via {@code docker cp}.
     *
     * @param containerId the source container
     * @param outputPath path inside the container (e.g. {@code /workspace/out})
     * @return map of relative file paths to contents
     */
    public Map<String, byte[]> collectOutput(String containerId, String outputPath) {
        var reader = new SandboxOutputArchive(
                MAX_OUTPUT_BYTES, maxOutputBytes, maxSingleFileBytes, SandboxOutputArchive.MAX_ENTRIES);
        try (InputStream tarStream = fileOps.copyArchiveFromContainer(containerId, outputPath)) {
            return reader.read(tarStream, Path.of(outputPath).getFileName().toString());
        } catch (IOException | SandboxException e) {
            // Execution already happened; collection failure must not trigger an infrastructure retry.
            throw new SandboxException("Invalid or incomplete sandbox output archive", e);
        }
    }

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
                long written = copyFilePrefix(source, tar, fileSize);
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

    /** Returns a normalized relative path that does not escape the archive root. */
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
