package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceManifest;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Materialises workspace inputs, serialising concurrent reads of the same local repository. Planned
 * evidence builds record collection failures for readiness refusal; programming failures, undeclared paths,
 * and duplicate outputs remain fatal.
 */
@Service
public class WorkspaceContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceContextBuilder.class);
    private static final String METRIC_BUILD = "agent.context.build";
    private static final String METRIC_REQUIRED_FAILURE = "agent.context.provider.required.failure";

    /** Bounded stripes avoid retaining repository identifiers indefinitely. */
    private static final int LOCK_STRIPES = 64;

    private final List<ContentSource> providers;
    private final MeterRegistry meterRegistry;

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
        if (manifestBuilder != null) {
            manifestBuilder.validateEvidenceSources(this.providers);
        }
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

    /** @return insertion-ordered workspace-relative paths and bytes */
    public Map<String, byte[]> build(ContextRequest request) {
        return buildWithoutManifest(request);
    }

    public Map<String, byte[]> build(ContextRequest request, @Nullable EvidencePlan evidencePlan) {
        if (evidencePlan == null) {
            return buildWithoutManifest(request);
        }
        return prepare(request, evidencePlan).files();
    }

    public PreparedEvidence prepare(ContextRequest request, EvidencePlan evidencePlan) {
        Long repoKey = repoKey(request);
        ReentrantLock lock = repoKey == null ? null : stripeFor(repoKey);
        long startNs = System.nanoTime();
        if (lock != null) {
            lock.lock();
        }
        try {
            BuildResult result = buildLocked(request, evidencePlan);
            if (result.manifest() == null) {
                throw new IllegalStateException("Detector evidence was prepared without a source manifest");
            }
            return new PreparedEvidence(result.files(), result.manifest());
        } finally {
            if (lock != null) {
                lock.unlock();
            }
            meterRegistry
                .timer(METRIC_BUILD + ".duration", Tags.of("kind", request.getClass().getSimpleName()))
                .record(System.nanoTime() - startNs, TimeUnit.NANOSECONDS);
        }
    }

    public List<Practice> readyPractices(ArtifactSourceManifest manifest, List<Practice> practices) {
        if (manifestBuilder == null) {
            throw new IllegalStateException("Evidence readiness requires a manifest builder");
        }
        return manifestBuilder.readyPractices(manifest, practices);
    }

    public List<Practice> readyPractices(ArtifactSourceManifest manifest, List<Practice> practices, String jobId) {
        if (manifestBuilder == null) {
            throw new IllegalStateException("Evidence readiness requires a manifest builder");
        }
        return manifestBuilder.readyPractices(manifest, practices, jobId);
    }

    private Map<String, byte[]> buildWithoutManifest(ContextRequest request) {
        Long repoKey = repoKey(request);
        ReentrantLock lock = repoKey == null ? null : stripeFor(repoKey);
        long startNs = System.nanoTime();
        if (lock != null) lock.lock();
        try {
            return buildLocked(request, null).files();
        } finally {
            if (lock != null) lock.unlock();
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

    private record BuildResult(Map<String, byte[]> files, @Nullable ArtifactSourceManifest manifest) {}

    private BuildResult buildLocked(ContextRequest request, @Nullable EvidencePlan evidencePlan) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<String, String> keyOwner = new HashMap<>();
        Map<String, SourceKind> keySourceKind = new HashMap<>();
        Map<SourceKind, SourceCompleteness> completeness = new HashMap<>();
        Map<SourceKind, SourceContentState> contentStates = new HashMap<>();
        Map<SourceKind, String> immutableIdentities = new HashMap<>();
        Map<SourceKind, Instant> observedAt = new HashMap<>();
        Map<SourceKind, Instant> sourceEffectiveAt = new HashMap<>();
        Map<SourceKind, SourceCaptureState> stateOverrides = new HashMap<>();
        Set<SourceKind> attemptedKinds = new HashSet<>();
        int contributed = 0;
        for (ContentSource provider : providers) {
            if (!provider.supports(request)) {
                continue;
            }
            if (
                evidencePlan != null &&
                provider instanceof EvidenceSource evidenceSource &&
                evidenceSource.sourceKinds().stream().noneMatch(evidencePlan.selectedSources()::contains)
            ) {
                continue;
            }
            String providerName = provider.getClass().getSimpleName();
            if (evidencePlan != null && provider instanceof EvidenceSource evidenceSource) {
                evidenceSource
                    .sourceKinds()
                    .stream()
                    .filter(evidencePlan.selectedSources()::contains)
                    .forEach(attemptedKinds::add);
            }
            Map<String, byte[]> contributionFiles;
            try {
                if (evidencePlan != null && provider instanceof EvidenceSource evidenceSource) {
                    EvidenceContribution contribution = evidenceSource.capture(request, evidencePlan.selectedSources());
                    validateContribution(evidenceSource, evidencePlan, contribution);
                    contributionFiles = contribution.files();
                    completeness.putAll(contribution.completeness());
                    contentStates.putAll(contribution.contentStates());
                    immutableIdentities.putAll(contribution.immutableIdentities());
                    observedAt.putAll(contribution.observedAt());
                    sourceEffectiveAt.putAll(contribution.sourceEffectiveAt());
                } else {
                    Map<String, byte[]> localFiles = new LinkedHashMap<>();
                    provider.contribute(request, localFiles);
                    contributionFiles = localFiles;
                }
            } catch (JobPreparationException e) {
                if (evidencePlan == null || !(provider instanceof EvidenceSource evidenceSource)) {
                    throw e;
                }
                if (provider.required()) {
                    meterRegistry.counter(METRIC_REQUIRED_FAILURE, Tags.of("provider", providerName)).increment();
                }
                recordCollectionError(evidenceSource, evidencePlan, stateOverrides);
                log.warn("Evidence provider failed; recording collection error: {} — {}", providerName, e.getMessage());
                continue;
            } catch (RuntimeException e) {
                if (!(e instanceof EvidenceCollectionException)) {
                    throw e;
                }
                if (provider.required()) {
                    meterRegistry.counter(METRIC_REQUIRED_FAILURE, Tags.of("provider", providerName)).increment();
                }
                if (evidencePlan != null && provider instanceof EvidenceSource evidenceSource) {
                    recordCollectionError(evidenceSource, evidencePlan, stateOverrides);
                    log.warn(
                        "Evidence provider failed; recording collection error: {} — {}",
                        providerName,
                        e.getMessage()
                    );
                    continue;
                }
                if (provider.required()) {
                    throw new JobPreparationException("Required content provider failed: " + providerName, e);
                }
                log.warn("Optional content provider failed, continuing: {} — {}", providerName, e.getMessage());
                continue;
            }
            for (Map.Entry<String, byte[]> entry : contributionFiles.entrySet()) {
                String key = entry.getKey();
                if (files.containsKey(key)) {
                    throw new IllegalStateException(
                        "Duplicate workspace key " +
                            key +
                            ": written by both " +
                            keyOwner.get(key) +
                            " and " +
                            providerName
                    );
                }
                if (!provider.ownsPath(key)) {
                    throw new IllegalStateException(
                        providerName + " wrote file outside its declared input namespace: " + key
                    );
                }
                keyOwner.put(key, providerName);
                if (provider instanceof EvidenceSource evidenceSource) {
                    SourceKind kind = evidenceSource.sourceKindFor(key);
                    if (!evidenceSource.sourceKinds().contains(kind)) {
                        throw new IllegalStateException(
                            providerName + " mapped output to undeclared source kind " + kind
                        );
                    }
                    if (evidencePlan != null && !evidencePlan.selectedSources().contains(kind)) {
                        throw new IllegalStateException(providerName + " emitted unselected source kind " + kind);
                    }
                    keySourceKind.put(key, kind);
                } else if (evidencePlan != null) {
                    throw new IllegalStateException(providerName + " emitted undocumented detector input " + key);
                }
                if (entry.getValue() == null) {
                    throw new IllegalStateException(providerName + " emitted null bytes for " + key);
                }
                files.put(key, entry.getValue().clone());
            }
            contributed++;
        }
        // Manifest (ADR 0020) only for job-backed review flows; mentor chat has its own context surface.
        ArtifactSourceManifest manifest = null;
        if (manifestBuilder != null && evidencePlan != null) {
            AgentJob job = reviewJob(request);
            if (job != null) {
                manifest = manifestBuilder.augment(
                    files,
                    keySourceKind,
                    String.valueOf(job.getId()),
                    evidencePlan,
                    new ContextManifestBuilder.CaptureMetadata(
                        completeness,
                        contentStates,
                        immutableIdentities,
                        observedAt,
                        sourceEffectiveAt,
                        stateOverrides,
                        attemptedKinds
                    )
                );
            }
        }
        log.debug("Workspace context built: {} files from {} provider(s)", files.size(), contributed);
        return new BuildResult(files, manifest);
    }

    private static void recordCollectionError(
        EvidenceSource source,
        EvidencePlan plan,
        Map<SourceKind, SourceCaptureState> stateOverrides
    ) {
        source
            .sourceKinds()
            .stream()
            .filter(plan.selectedSources()::contains)
            .forEach(kind -> stateOverrides.put(kind, new SourceCaptureState.CollectionError("PROVIDER_FAILURE")));
    }

    private static void validateContribution(
        EvidenceSource source,
        EvidencePlan plan,
        EvidenceContribution contribution
    ) {
        Set<SourceKind> allowedKinds = new HashSet<>(source.sourceKinds());
        allowedKinds.retainAll(plan.selectedSources());

        Set<SourceKind> reportedKinds = new HashSet<>(contribution.completeness().keySet());
        reportedKinds.addAll(contribution.contentStates().keySet());
        reportedKinds.addAll(contribution.immutableIdentities().keySet());
        reportedKinds.addAll(contribution.observedAt().keySet());
        reportedKinds.addAll(contribution.sourceEffectiveAt().keySet());
        reportedKinds.removeAll(allowedKinds);
        if (!reportedKinds.isEmpty()) {
            throw new IllegalStateException(
                source.getClass().getSimpleName() +
                    " reported facts for undeclared or unselected sources: " +
                    reportedKinds
            );
        }
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
