package de.tum.in.www1.hephaestus.agent.context;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobPreparationException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

/**
 * Orchestrates {@link ContentProvider}s to materialise the AI-readable workspace context under
 * {@code context/target/...}. Order is resolved via {@link AnnotationAwareOrderComparator}
 * ({@code @Order} / {@code Ordered}); a per-repository {@link ReentrantLock} serialises
 * concurrent builds against the same on-disk git working tree.
 *
 * <p>Failure policy is per-provider: {@link ContentProvider#required()} failures bubble as
 * {@link JobPreparationException}; non-required failures are logged and skipped. Programmer
 * errors ({@link NullPointerException}, {@link IllegalArgumentException}, {@link IllegalStateException})
 * propagate as-is so production stack traces stay diagnostic.
 *
 * <p>Two providers writing the same output key is a wiring bug — caught at {@link #build}
 * time and reported as {@link IllegalStateException}.
 */
@Service
public class WorkspaceContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceContextBuilder.class);
    private static final String METRIC_BUILD = "agent.context.build";
    private static final String METRIC_REQUIRED_FAILURE = "agent.context.provider.required.failure";

    /** Number of stripes for per-repo single-flight locks. Bounded → no map-leak. */
    private static final int LOCK_STRIPES = 64;

    private final List<ContentProvider> providers;
    private final MeterRegistry meterRegistry;
    /** Fixed-size stripe of locks → bounded memory, no map leak as new repos are seen. */
    private final ReentrantLock[] repoLockStripes;

    public WorkspaceContextBuilder(List<ContentProvider> providers, MeterRegistry meterRegistry) {
        List<ContentProvider> sorted = new ArrayList<>(providers);
        AnnotationAwareOrderComparator.sort(sorted);
        this.providers = List.copyOf(sorted);
        this.meterRegistry = meterRegistry;
        this.repoLockStripes = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            repoLockStripes[i] = new ReentrantLock();
        }
        log.info(
            "WorkspaceContextBuilder registered {} provider(s): {}",
            this.providers.size(),
            this.providers.stream()
                .map(p -> p.getClass().getSimpleName())
                .toList()
        );
    }

    /**
     * Build the workspace context for {@code request}. Concurrent builds against the same
     * repository serialise on a per-repo {@link ReentrantLock}; builds against different repos
     * run in parallel.
     *
     * @return insertion-ordered {@link LinkedHashMap} of workspace-relative path → bytes
     */
    public Map<String, byte[]> build(ContextRequest request) {
        ReentrantLock lock = stripeFor(repoKey(request));
        long startNs = System.nanoTime();
        lock.lock();
        try {
            return buildLocked(request);
        } finally {
            lock.unlock();
            meterRegistry
                .timer(METRIC_BUILD + ".duration", Tags.of("kind", request.getClass().getSimpleName()))
                .record(System.nanoTime() - startNs, TimeUnit.NANOSECONDS);
        }
    }

    /** Map a repository id to one of {@link #LOCK_STRIPES} locks; {@code null} keys all collide on stripe 0. */
    private ReentrantLock stripeFor(Long repoKey) {
        int idx = repoKey == null ? 0 : Math.floorMod(repoKey.hashCode(), LOCK_STRIPES);
        return repoLockStripes[idx];
    }

    private Map<String, byte[]> buildLocked(ContextRequest request) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<String, String> keyOwner = new java.util.HashMap<>();
        int contributed = 0;
        for (ContentProvider provider : providers) {
            if (!provider.supports(request)) {
                continue;
            }
            String providerName = provider.getClass().getSimpleName();
            // Snapshot pre-call keyset so we can detect both (a) overwrites of another provider's
            // output (caught here) and (b) brand-new keys (validated for prefix below).
            java.util.Set<String> beforeKeys = java.util.Set.copyOf(files.keySet());
            try {
                provider.contribute(request, files);
            } catch (JobPreparationException e) {
                throw e;
            } catch (RuntimeException e) {
                if (provider.required()) {
                    meterRegistry.counter(METRIC_REQUIRED_FAILURE, Tags.of("provider", providerName)).increment();
                    throw new JobPreparationException("Required content provider failed: " + providerName, e);
                }
                log.warn("Optional content provider failed, continuing: {} — {}", providerName, e.getMessage());
                continue;
            }
            for (String key : files.keySet()) {
                if (beforeKeys.contains(key)) {
                    continue; // pre-existing key — not written by this provider
                }
                if (!key.startsWith(ContentProvider.OUTPUT_PREFIX)) {
                    throw new IllegalStateException(
                        providerName + " wrote file outside " + ContentProvider.OUTPUT_PREFIX + ": " + key
                    );
                }
                String existingOwner = keyOwner.get(key);
                if (existingOwner != null) {
                    throw new IllegalStateException(
                        "Duplicate workspace key " + key + ": written by both " + existingOwner + " and " + providerName
                    );
                }
                keyOwner.put(key, providerName);
            }
            contributed++;
        }
        log.debug("Workspace context built: {} files from {} provider(s)", files.size(), contributed);
        return files;
    }

    /** Repository id for single-flight locking, or {@code null} for requests that don't touch git. */
    private static Long repoKey(ContextRequest request) {
        if (request instanceof ContextRequest.PracticeReviewRequest pr) {
            JsonNode meta = pr.job().getMetadata();
            if (meta != null && meta.has("repository_id") && meta.get("repository_id").isNumber()) {
                return meta.get("repository_id").asLong();
            }
        }
        return null;
    }
}
