package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
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

    /** Maximum total size of injected input files (50 MB). */
    static final long MAX_INPUT_BYTES = 50L * 1024 * 1024;

    private final DockerFileOperations fileOps;
    private final long maxOutputBytes;

    public SandboxWorkspaceManager(DockerFileOperations fileOps) {
        this(fileOps, MAX_OUTPUT_BYTES);
    }

    /** Package-private constructor for testing with a smaller output limit. */
    SandboxWorkspaceManager(DockerFileOperations fileOps, long maxOutputBytes) {
        this.fileOps = fileOps;
        this.maxOutputBytes = maxOutputBytes;
    }

    /**
     * Inject files into a container via {@code docker cp}.
     *
     * @param containerId the target container (must be created but can be stopped)
     * @param files map of relative paths to file contents
     */
    public void injectFiles(String containerId, Map<String, byte[]> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        byte[] tarBytes = createTarArchive(files);
        try (InputStream tarStream = new ByteArrayInputStream(tarBytes)) {
            fileOps.copyArchiveToContainer(containerId, "/workspace", tarStream);
            log.debug("Injected {} files into container {}", files.size(), containerId);
        } catch (IOException e) {
            throw new SandboxException("Failed to inject files into container: " + containerId, e);
        }
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
                    String name = entry.getName();
                    // Strip leading directory component (docker cp includes the parent dir name)
                    int slashIndex = name.indexOf('/');
                    if (slashIndex >= 0) {
                        name = name.substring(slashIndex + 1);
                    }
                    if (name.isEmpty()) {
                        continue;
                    }
                    // Reject traversal paths in collected output
                    Path normalized = Path.of(name).normalize();
                    if (normalized.isAbsolute() || normalized.startsWith("..")) {
                        log.warn("Skipping unsafe path in output archive: {}", name);
                        continue;
                    }
                    name = normalized.toString();
                    if (totalBytes + entry.getSize() > maxOutputBytes) {
                        log.warn(
                            "Output size limit exceeded ({} bytes) for container {}, skipping remaining files",
                            maxOutputBytes,
                            containerId
                        );
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
                outputPath
            );
        } catch (SandboxException e) {
            // docker cp failed — output directory may not exist (agent didn't write output)
            log.warn("Failed to collect output from container {} at {}: {}", containerId, outputPath, e.getMessage());
        } catch (IOException e) {
            log.warn("Failed to read output tar from container {}: {}", containerId, e.getMessage());
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private byte[] createTarArchive(Map<String, byte[]> files) {
        try (
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)
        ) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

            long totalBytes = 0;
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                totalBytes += entry.getValue().length;
                if (totalBytes > MAX_INPUT_BYTES) {
                    throw new SandboxException("Input files exceed maximum size limit (" + MAX_INPUT_BYTES + " bytes)");
                }
                String safePath = validatePath(entry.getKey());
                TarArchiveEntry tarEntry = new TarArchiveEntry(safePath);
                tarEntry.setSize(entry.getValue().length);
                tarEntry.setModTime(System.currentTimeMillis());
                tar.putArchiveEntry(tarEntry);
                tar.write(entry.getValue());
                tar.closeArchiveEntry();
            }

            tar.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new SandboxException("Failed to create tar archive", e);
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
