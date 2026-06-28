package de.tum.cit.aet.hephaestus.integration.core.fabric;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The single source of every Context-Fabric on-disk path (ADR 0020). Resolves the four cache
 * regions under one configurable root so the layout is integration-namespaced and a new connector
 * needs no path plumbing of its own.
 *
 * <p>Three regions, three lifecycles, visible in one {@code ls} (the immutable/mutable split that every
 * proven content store — Git objects-vs-refs, Bazel cas-vs-ac, OCI blobs-vs-index — keeps physical):
 * <ul>
 *   <li>{@code cas/} — IMMUTABLE content-addressed blobs (the only hash-keyed region); GC by mark-and-sweep.
 *   <li>{@code sources/{connector}/{externalId}} — MUTABLE name-keyed per-connector materialisations (the SCM
 *       git checkout, a future Slack/Outline export); regenerable, GC by re-materialise-on-miss.
 *   <li>{@code jobs/{jobId}/manifest.json} — the MUTABLE provenance index (name+provenance → digests, the
 *       analogue of Git refs / Bazel ActionCache); recoverable from SQL, GC by retention window.
 * </ul>
 *
 * <p>The root defaults to the existing {@code hephaestus.git.storage-path} so a deployment that only set
 * the git path keeps working — the checkout simply moves from {@code {root}/{repoId}} to
 * {@code {root}/sources/scm/{repoId}}. Because it is a rebuildable cache, no data migration is needed:
 * a job re-materialises at the new path on first miss.
 */
@Component
public class FabricLayout {

    private static final String SOURCES = "sources";
    private static final String CAS = "cas";
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
     * The mutable per-connector source materialisation:
     * {@code {root}/sources/{connectorId}/{externalId}}. The SCM git checkout lives at
     * {@code sources/scm/{repositoryId}} (one namespace among future {@code sources/slack/...},
     * {@code sources/outline/...}). Connector-agnostic noun — "source" subsumes a git checkout, a Slack
     * export, and a docs snapshot alike; it is a name-keyed, churning view NOT addressed by its content
     * (so it is a sibling of {@code cas/}, never nested).
     */
    public Path source(String connectorId, String externalId) {
        return root.resolve(SOURCES).resolve(segment(connectorId)).resolve(segment(externalId));
    }

    /** Root of the content-addressed blob store ({@code cas/sha256/{ab}/{rest}}): {@code {root}/cas}. */
    public Path casRoot() {
        return root.resolve(CAS);
    }

    /** Root of the per-job replay directories: {@code {root}/jobs}. */
    public Path jobsRoot() {
        return root.resolve(JOBS);
    }

    /** Per-job replay/reproducibility directory: {@code {root}/jobs/{jobId}}. */
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
