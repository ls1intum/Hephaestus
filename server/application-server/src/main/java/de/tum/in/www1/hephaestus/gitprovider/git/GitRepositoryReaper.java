package de.tum.in.www1.hephaestus.gitprovider.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily sweep over {@link GitRepositoryProperties#storagePath()} that deletes per-repo working
 * trees whose {@code lastModifiedTime} is older than
 * {@link GitRepositoryProperties#cacheMaxAgeDays()} days. The agent re-clones on next demand —
 * small one-time latency cost in exchange for bounded disk usage on long-running deployments.
 *
 * <p>Scoped to top-level entries of the storage path: we never descend into a working tree to
 * compute its newest file mtime (would O(n) walk every clone every day). Treating top-level
 * mtime as "last touched" is acceptable because the touch path is the agent's
 * {@code git clone}/{@code git fetch} call, which always bumps the parent directory's mtime
 * via Linux directory-entry updates.
 *
 * <p>Why a fixed schedule rather than tying eviction to clone calls: clone is the hot path,
 * we don't want to scan every entry on every clone. A nightly sweep at 03:00 UTC matches the
 * pattern used by other Hephaestus reapers ({@code MentorInFlightReaper}, GitHub data sync
 * schedulers) and keeps reasoning local — one timer to audit, one log line to grep.
 *
 * <p>Disabled when {@code hephaestus.git.cache-max-age-days = 0} (local dev / CI), and
 * conditionally instantiated only when {@code hephaestus.git.enabled = true} so the JVM
 * skeleton in test profiles doesn't pull in the unused {@code @Scheduled} machinery.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "hephaestus.git.enabled", havingValue = "true")
public class GitRepositoryReaper {

    private final GitRepositoryProperties properties;

    public GitRepositoryReaper(GitRepositoryProperties properties) {
        this.properties = properties;
    }

    /**
     * Walks {@link GitRepositoryProperties#storagePath()} once per day and deletes every
     * top-level entry whose mtime is older than the configured cutoff. Logs a single summary
     * line — individual entry deletions go DEBUG.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void evictStale() {
        if (properties.cacheMaxAgeDays() == 0) {
            log.debug("git repo reaper disabled (cacheMaxAgeDays=0)");
            return;
        }
        Path base = Path.of(properties.storagePath());
        if (!Files.isDirectory(base)) {
            log.debug("git storage path does not exist: {}", base);
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.cacheMaxAgeDays()));
        int evicted = 0;
        long bytesFreed = 0L;
        try (var stream = Files.list(base)) {
            for (Path entry : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(entry)) continue;
                try {
                    Instant mtime = Files.getLastModifiedTime(entry).toInstant();
                    if (mtime.isAfter(cutoff)) continue;
                    long size = computeSize(entry);
                    deleteRecursive(entry);
                    evicted++;
                    bytesFreed += size;
                    log.debug("evicted stale repo {} (mtime={}, ~{} bytes)", entry, mtime, size);
                } catch (IOException io) {
                    log.warn("failed to evict repo {}: {}", entry, io.toString());
                }
            }
        } catch (IOException io) {
            log.warn("git repo reaper failed to list {}: {}", base, io.toString());
            return;
        }
        if (evicted > 0) {
            log.info(
                "git repo reaper: evicted {} stale clones, freed ~{} MB (cutoff={} days)",
                evicted,
                bytesFreed / 1_048_576L,
                properties.cacheMaxAgeDays()
            );
        }
    }

    /**
     * Best-effort size estimate for an evicted repo. Walks the tree once; any IO failure
     * mid-walk yields the partial sum rather than aborting. Used only for the summary log
     * line — never for control flow.
     */
    private static long computeSize(Path root) {
        final long[] total = { 0L };
        try {
            Files.walk(root)
                .filter(Files::isRegularFile)
                .forEach(p -> {
                    try {
                        total[0] += Files.size(p);
                    } catch (IOException ignored) {
                        // best-effort accounting
                    }
                });
        } catch (IOException ignored) {
            // partial sum is acceptable
        }
        return total[0];
    }

    private static void deleteRecursive(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            stream
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort: the next sweep retries
                    }
                });
        }
    }
}
