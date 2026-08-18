package de.tum.cit.aet.hephaestus.integration.core.fabric;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves Context Fabric paths below one configured root. Mutable connector materialisations, immutable CAS
 * blobs, and per-job replay metadata use separate lifecycle regions.
 */
@Component
public class FabricLayout {

    private static final String SOURCES = "sources";
    private static final String CAS = "cas";
    private static final String JOBS = "jobs";

    private final Path root;

    public FabricLayout(@Value("${hephaestus.fabric.root:/data/git-repos}") String root) {
        this.root = Path.of(root);
    }

    public Path root() {
        return root;
    }

    public Path source(String connectorId, String externalId) {
        return root.resolve(SOURCES).resolve(segment(connectorId)).resolve(segment(externalId));
    }

    public Path casRoot() {
        return root.resolve(CAS);
    }

    public Path jobsRoot() {
        return root.resolve(JOBS);
    }

    public Path jobDir(String jobId) {
        return jobsRoot().resolve(segment(jobId));
    }

    /**
     * Reject a path segment that could escape its region. Dots are allowed (a connector id like
     * {@code scm.gitlab} is legal); path separators and traversal are not.
     */
    private static String segment(String value) {
        // Separators are banned outright, so the only traversal a single segment can express is a bare
        // "." or ".." — reject exactly those, not any embedded double-dot (an id like "v1..2" is legal).
        if (
            value == null ||
            value.isBlank() ||
            value.contains("/") ||
            value.contains("\\") ||
            value.equals(".") ||
            value.equals("..")
        ) {
            throw new IllegalArgumentException("Unsafe fabric path segment: " + value);
        }
        return value;
    }
}
