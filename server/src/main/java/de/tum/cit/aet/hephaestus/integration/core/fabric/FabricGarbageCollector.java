package de.tum.cit.aet.hephaestus.integration.core.fabric;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Garbage-collects the Context-Fabric cache (ADR 0020). Disk is a rebuildable cache, so collection is
 * safe and best-effort: it first prunes per-job replay directories older than the retention window, then
 * mark-and-sweeps the {@link ContentAddressedStore} against the blobs still referenced by the surviving
 * job manifests. A blob wrongly swept is simply rebuilt on next access; SQL stays the source of truth.
 *
 * <p>Mirrors the established {@code @Scheduled} sweepers (ExportRetentionSweeper, AccountHardDeleteSweeper),
 * including their {@link ConditionalOnServerRole} gate so the bean only exists on the server role rather than
 * relying incidentally on {@code @EnableScheduling} placement to keep {@link #collect()} from firing off-role.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic(
    "The fabric cache (content-addressed blob store + job-replay dirs) is shared across all workspaces " +
        "by design — like the git clone it generalises — so GC operates globally with no per-workspace iteration."
)
public class FabricGarbageCollector {

    private static final Logger log = LoggerFactory.getLogger(FabricGarbageCollector.class);

    private final FabricLayout layout;
    private final ContentAddressedStore cas;
    private final JsonMapper objectMapper;
    private final Duration retention;

    public FabricGarbageCollector(
        FabricLayout layout,
        ContentAddressedStore cas,
        JsonMapper objectMapper,
        @Value("${hephaestus.fabric.gc-retention-days:30}") long retentionDays
    ) {
        this.layout = layout;
        this.cas = cas;
        this.objectMapper = objectMapper;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(
        fixedRateString = "${hephaestus.fabric.gc-rate:86400000}",
        initialDelayString = "${hephaestus.fabric.gc-initial-delay:3600000}"
    )
    public void collect() {
        Instant cutoff = Instant.now().minus(retention);
        int prunedJobs = pruneExpiredJobs(cutoff);
        int prunedLegacy = pruneLegacyClones();
        Set<String> live = referencedShas();
        // Safety net: this GC runs under the SERVER role's scheduler, while jobs/manifests are written on
        // the job-execution path. In a split deployment that does NOT share hephaestus.fabric.root, an
        // EMPTY live set here means "manifests are not visible on this volume", NOT "every blob is
        // garbage" — so never mass-sweep the CAS on zero references. (When server+worker share the volume,
        // a genuinely-idle root has no blobs either, so the guard costs nothing.)
        int sweptBlobs = live.isEmpty() ? 0 : cas.sweep(live);
        if (prunedJobs > 0 || prunedLegacy > 0 || sweptBlobs > 0) {
            log.info(
                "Fabric GC: pruned {} expired job dir(s), {} legacy clone(s), swept {} orphaned CAS blob(s)",
                prunedJobs,
                prunedLegacy,
                sweptBlobs
            );
        }
    }

    /**
     * Reclaim git clones left at the pre-Fabric layout ({@code {root}/{repoId}}, all-digit names directly
     * under the root) after {@code GitRepositoryManager.getRepositoryPath} moved them to
     * {@code sources/scm/{repoId}}. Idempotent — once removed, later runs find none. The current regions
     * ({@code sources/}, {@code cas/}, {@code jobs/}) are never all-digit, so they are safe.
     */
    int pruneLegacyClones() {
        Path root = layout.root();
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int pruned = 0;
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.filter(Files::isDirectory).toList()) {
                String name = child.getFileName().toString();
                if (!name.isEmpty() && name.chars().allMatch(Character::isDigit)) {
                    try {
                        deleteRecursively(child);
                        pruned++;
                    } catch (IOException e) {
                        log.warn("Fabric GC could not prune legacy clone {}: {}", child, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Fabric GC could not list fabric root {}: {}", root, e.getMessage());
        }
        return pruned;
    }

    /** Delete {@code jobs/{jobId}} directories last modified before {@code cutoff}. Returns the count removed. */
    int pruneExpiredJobs(Instant cutoff) {
        Path jobsRoot = layout.jobsRoot();
        if (!Files.isDirectory(jobsRoot)) {
            return 0;
        }
        int pruned = 0;
        try (Stream<Path> jobDirs = Files.list(jobsRoot)) {
            for (Path jobDir : jobDirs.filter(Files::isDirectory).toList()) {
                try {
                    if (Files.getLastModifiedTime(jobDir).toInstant().isBefore(cutoff)) {
                        deleteRecursively(jobDir);
                        pruned++;
                    }
                } catch (IOException e) {
                    log.warn("Fabric GC could not prune {}: {}", jobDir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Fabric GC could not list jobs root {}: {}", jobsRoot, e.getMessage());
        }
        return pruned;
    }

    /** Collect every {@code sha256} referenced by a surviving job manifest under {@code jobs/}. */
    Set<String> referencedShas() {
        Set<String> shas = new HashSet<>();
        Path jobsRoot = layout.jobsRoot();
        if (!Files.isDirectory(jobsRoot)) {
            return shas;
        }
        try (Stream<Path> manifests = Files.walk(jobsRoot)) {
            manifests
                .filter(p -> p.getFileName().toString().equals("manifest.json"))
                .forEach(manifest -> {
                    try {
                        JsonNode root = objectMapper.readTree(Files.readAllBytes(manifest));
                        for (JsonNode entry : root.path("entries")) {
                            String sha = entry.path("sha256").asString("");
                            if (!sha.isBlank()) {
                                shas.add(sha);
                            }
                        }
                    } catch (IOException | RuntimeException e) {
                        log.debug("Fabric GC could not read manifest {}: {}", manifest, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.warn("Fabric GC could not walk jobs root {}: {}", jobsRoot, e.getMessage());
        }
        return shas;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
