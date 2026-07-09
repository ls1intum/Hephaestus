package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Orchestrates {@link ContentSource}s to materialise the AI-readable workspace context under
 * {@code inputs/context/...}. Order is resolved via {@link AnnotationAwareOrderComparator}
 * ({@code @Order} / {@code Ordered}); a per-repository {@link ReentrantLock} serialises
 * concurrent builds against the same on-disk git working tree. Requests without a repository
 * key do not touch git and are not serialised globally.
 *
 * <p>Failure policy is per-provider: {@link ContentSource#required()} failures bubble as
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

    private final List<ContentSource> providers;
    private final MeterRegistry meterRegistry;

    /** Builds the integration-agnostic context manifest after the providers run; null in unit tests. */
    private final @Nullable ContextManifestBuilder manifestBuilder;

    private final ReentrantLock[] repoLockStripes;

    public WorkspaceContextBuilder(
        List<ContentSource> providers,
        MeterRegistry meterRegistry,
        @Nullable ContextManifestBuilder manifestBuilder
    ) {
        List<ContentSource> sorted = new ArrayList<>(providers);
        AnnotationAwareOrderComparator.sort(sorted);
        this.providers = List.copyOf(sorted);
        this.meterRegistry = meterRegistry;
        this.manifestBuilder = manifestBuilder;
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
        Long repoKey = repoKey(request);
        ReentrantLock lock = repoKey == null ? null : stripeFor(repoKey);
        long startNs = System.nanoTime();
        if (lock != null) {
            lock.lock();
        }
        try {
            return buildLocked(request);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
            meterRegistry
                .timer(METRIC_BUILD + ".duration", Tags.of("kind", request.getClass().getSimpleName()))
                .record(System.nanoTime() - startNs, TimeUnit.NANOSECONDS);
        }
    }

    /** Map a repository id to one of {@link #LOCK_STRIPES} locks. */
    private ReentrantLock stripeFor(Long repoKey) {
        int idx = Math.floorMod(repoKey.hashCode(), LOCK_STRIPES);
        return repoLockStripes[idx];
    }

    private Map<String, byte[]> buildLocked(ContextRequest request) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<String, String> keyOwner = new java.util.HashMap<>();
        Map<String, String> keyConnector = new java.util.HashMap<>();
        int contributed = 0;
        for (ContentSource provider : providers) {
            if (!provider.supports(request)) {
                continue;
            }
            String providerName = provider.getClass().getSimpleName();
            // Snapshot pre-call keyset AND values so we can detect both (a) brand-new keys
            // (validated for prefix below) and (b) silent overwrites — a second provider
            // calling files.put(key, newBytes) on a key already owned by another provider
            // would otherwise replace the value in-place, and the keyOwner check below
            // (gated on `if (beforeKeys.contains(key)) continue`) would miss it.
            java.util.Set<String> beforeKeys = java.util.Set.copyOf(files.keySet());
            // Reference-snapshot is enough: ContentSource#contribute is required to publish
            // a NEW byte[] for any modification (the existing arrays are interpreted as the
            // owner's immutable output), so reference equality identifies an in-place replace.
            java.util.Map<String, byte[]> beforeValues = java.util.Map.copyOf(files);
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
                    // Pre-existing key — only a re-put is a wiring bug. Reference inequality
                    // identifies a fresh put(key, newArray); the SPI mandates providers publish a
                    // NEW byte[] for any change, so this is sufficient. Note: an in-place mutation
                    // of an earlier provider's array keeps the same reference and is NOT detected —
                    // the contract relies on providers treating earlier outputs as immutable.
                    if (beforeValues.get(key) != files.get(key)) {
                        String existingOwner = keyOwner.get(key);
                        throw new IllegalStateException(
                            "Duplicate workspace key " +
                                key +
                                ": written by both " +
                                existingOwner +
                                " and " +
                                providerName
                        );
                    }
                    continue;
                }
                if (!key.startsWith(ContentSource.OUTPUT_PREFIX)) {
                    throw new IllegalStateException(
                        providerName + " wrote file outside " + ContentSource.OUTPUT_PREFIX + ": " + key
                    );
                }
                // Invariant: a brand-new key (absent from beforeKeys) cannot already be owned —
                // keyOwner only holds keys that were added to `files`, and every such key is in
                // beforeKeys on subsequent iterations. The re-put guard above is the real
                // cross-provider duplicate detector.
                assert keyOwner.get(key) == null : "brand-new key already owned: " + key;
                keyOwner.put(key, providerName);
                keyConnector.put(key, provider.originId());
            }
            contributed++;
        }
        // Telescope (ADR 0020): index every projected file with its connector + a content-addressed sha,
        // and store each blob in the CAS. Built for the agent-review flows (PR/Issue); the mentor chat
        // flow has its own context surface. Best-effort — the manifest builder never throws.
        if (manifestBuilder != null) {
            AgentJob job = reviewJob(request);
            if (job != null) {
                Long workspaceId = job.getWorkspace() != null ? job.getWorkspace().getId() : null;
                manifestBuilder.augment(files, keyConnector, String.valueOf(job.getId()), workspaceId);
            }
        }
        log.debug("Workspace context built: {} files from {} provider(s)", files.size(), contributed);
        return files;
    }

    /** The job behind a PR/Issue/conversation review request, or {@code null} for the mentor-chat flow. */
    private static @Nullable AgentJob reviewJob(ContextRequest request) {
        if (request instanceof ContextRequest.PracticeReviewRequest pr) {
            return pr.job();
        }
        if (request instanceof ContextRequest.IssueReviewRequest ir) {
            return ir.job();
        }
        if (request instanceof ContextRequest.ConversationReviewRequest cr) {
            return cr.job();
        }
        return null;
    }

    /**
     * Repository id for single-flight locking, or {@code null} for requests that don't touch git.
     * Both PR- and issue-review jobs carry {@code repository_id} in metadata; reading it for both
     * spreads concurrent issue builds across the stripes by repo instead of all colliding on stripe 0.
     */
    private static Long repoKey(ContextRequest request) {
        AgentJob job = reviewJob(request);
        if (job == null) {
            return null;
        }
        JsonNode meta = job.getMetadata();
        if (meta != null && meta.has("repository_id") && meta.get("repository_id").isNumber()) {
            return meta.get("repository_id").asLong();
        }
        return null;
    }
}
