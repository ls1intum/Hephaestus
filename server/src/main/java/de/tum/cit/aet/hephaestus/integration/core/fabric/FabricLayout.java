package de.tum.cit.aet.hephaestus.integration.core.fabric;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The single source of every Context-Fabric on-disk path (ADR 0020). Resolves the four cache
 * regions under one configurable root so the layout is integration-namespaced and a new connector
 * needs no path plumbing of its own.
 *
 * <p>The root defaults to the existing {@code hephaestus.git.storage-path} so a deployment that only
 * set the git path keeps working — the git clone simply moves from {@code {root}/{repoId}} to
 * {@code {root}/bulk/scm/{repoId}}. Because a clone is a rebuildable cache, no data migration is
 * needed: a job re-clones at the new path on first miss.
 */
@Component
public class FabricLayout {

    private static final String BULK = "bulk";
    private static final String CAS = "cas";
    private static final String DERIVED = "derived";
    private static final String JOBS = "jobs";

    private final Path root;

    public FabricLayout(
        @Value("${hephaestus.fabric.root:${hephaestus.git.storage-path:/data/git-repos}}") String root
    ) {
        this.root = Path.of(root);
    }

    /** The fabric cache root; everything below is a rebuildable derivative of SQL + the upstream. */
    public Path root() {
        return root;
    }

    /**
     * A bulk, working-tree-shaped artifact for a connector: {@code {root}/bulk/{connectorId}/{externalId}}.
     * The git clone lives at {@code bulk/scm/{repositoryId}} (one namespace among future
     * {@code bulk/slack/...}, {@code bulk/outline/...}).
     */
    public Path bulkArtifact(String connectorId, String externalId) {
        return root.resolve(BULK).resolve(segment(connectorId)).resolve(segment(externalId));
    }

    /** Root of the content-addressed blob store (sha-256, two-char fan-out): {@code {root}/cas}. */
    public Path casRoot() {
        return root.resolve(CAS);
    }

    /** Content-hash-keyed rebuildable views for a connector: {@code {root}/derived/{connectorId}}. */
    public Path derived(String connectorId) {
        return root.resolve(DERIVED).resolve(segment(connectorId));
    }

    /** Per-job replay/reproducibility directory: {@code {root}/jobs/{jobId}}. */
    public Path jobDir(String jobId) {
        return root.resolve(JOBS).resolve(segment(jobId));
    }

    /**
     * Reject a path segment that could escape its region. Dots are allowed (a connector id like
     * {@code scm.gitlab} is legal); path separators and traversal are not.
     */
    private static String segment(String value) {
        if (value == null || value.isBlank() || value.contains("/") || value.contains("\\") || value.contains("..")) {
            throw new IllegalArgumentException("Unsafe fabric path segment: " + value);
        }
        return value;
    }
}
