package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceManifest;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    public Map<String, byte[]> build(ContextRequest request) {
        return buildWithoutManifest(request);
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
            return new PreparedEvidence(result.files(), result.filesOnDisk(), result.cleanups(), result.manifest());
        } finally {
            if (lock != null) {
                lock.unlock();
            }
            meterRegistry
                .timer(METRIC_BUILD + ".duration", Tags.of("kind", request.getClass().getSimpleName()))
                .record(System.nanoTime() - startNs, TimeUnit.NANOSECONDS);
        }
    }

    public ContextManifestBuilder.PreparedAutomatedReviewReadiness prepareAutomatedReviewReadiness(
        ArtifactSourceManifest manifest,
        List<Practice> practices,
        String jobId,
        Instant temporalAnchor,
        @Nullable SignalName signal
    ) {
        if (manifestBuilder == null) {
            throw new IllegalStateException("Evidence readiness requires a manifest builder");
        }
        return manifestBuilder.prepareAutomatedReviewReadiness(manifest, practices, jobId, temporalAnchor, signal);
    }

    public PreparedEvidence restrictTo(PreparedEvidence prepared, EvidencePlan plan) {
        if (manifestBuilder == null) {
            throw new IllegalStateException("Evidence restriction requires a manifest builder");
        }
        return manifestBuilder.restrictTo(prepared, plan);
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

    private ReentrantLock stripeFor(Long repoKey) {
        int idx = Math.floorMod(repoKey.hashCode(), LOCK_STRIPES);
        return repoLockStripes[idx];
    }

    private record BuildResult(
        Map<String, byte[]> files,
        Map<String, java.nio.file.Path> filesOnDisk,
        List<AutoCloseable> cleanups,
        @Nullable ArtifactSourceManifest manifest
    ) {}

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
        Map<String, java.nio.file.Path> filesOnDisk = new LinkedHashMap<>();
        List<AutoCloseable> cleanups = new ArrayList<>();
        int contributed = 0;
        for (ContentSource provider : providers) {
            if (!provider.supports(request)) {
                continue;
            }
            if (evidencePlan != null && !(provider instanceof EvidenceSource)) {
                throw new IllegalStateException(
                    "Detector context provider must declare source kinds: " + provider.getClass().getSimpleName()
                );
            }
            String providerName = provider.getClass().getSimpleName();
            Map<String, byte[]> contributionFiles;
            if (evidencePlan != null && provider instanceof EvidenceSource evidenceSource) {
                if (evidenceSource.sourceKinds().stream().noneMatch(evidencePlan.selectedSources()::contains)) {
                    continue;
                }
                contributionFiles = captureIndependently(
                    request,
                    evidencePlan,
                    evidenceSource,
                    providerName,
                    completeness,
                    contentStates,
                    immutableIdentities,
                    observedAt,
                    sourceEffectiveAt,
                    stateOverrides,
                    attemptedKinds,
                    filesOnDisk,
                    cleanups
                );
            } else {
                try {
                    Map<String, byte[]> localFiles = new LinkedHashMap<>();
                    provider.contribute(request, localFiles);
                    contributionFiles = localFiles;
                } catch (JobPreparationException e) {
                    throw e;
                } catch (RuntimeException e) {
                    if (!(e instanceof EvidenceCollectionException)) throw e;
                    if (provider.required()) {
                        meterRegistry.counter(METRIC_REQUIRED_FAILURE, Tags.of("provider", providerName)).increment();
                        throw new JobPreparationException("Required content provider failed: " + providerName, e);
                    }
                    log.warn("Optional content provider failed, continuing: {} — {}", providerName, e.getMessage());
                    continue;
                }
            }
            Set<String> contributedKeys = new LinkedHashSet<>(contributionFiles.keySet());
            for (var onDisk : filesOnDisk.entrySet()) {
                if (!keyOwner.containsKey(onDisk.getKey())) {
                    contributedKeys.add(onDisk.getKey());
                }
            }
            for (String key : contributedKeys) {
                byte[] value = contributionFiles.get(key);
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
                if (value == null) {
                    // Staged from disk: the bytes are never read by this process.
                    continue;
                }
                files.put(key, value.clone());
            }
            contributed++;
        }
        ArtifactSourceManifest manifest = null;
        if (manifestBuilder != null && evidencePlan != null) {
            AgentJob job = reviewJob(request);
            if (job != null) {
                manifest = manifestBuilder.augment(
                    files,
                    filesOnDisk,
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
        log.debug(
            "Workspace context built: {} files ({} staged from disk) from {} provider(s)",
            files.size() + filesOnDisk.size(),
            filesOnDisk.size(),
            contributed
        );
        return new BuildResult(files, filesOnDisk, cleanups, manifest);
    }

    private Map<String, byte[]> captureIndependently(
        ContextRequest request,
        EvidencePlan plan,
        EvidenceSource source,
        String providerName,
        Map<SourceKind, SourceCompleteness> completeness,
        Map<SourceKind, SourceContentState> contentStates,
        Map<SourceKind, String> immutableIdentities,
        Map<SourceKind, Instant> observedAt,
        Map<SourceKind, Instant> sourceEffectiveAt,
        Map<SourceKind, SourceCaptureState> stateOverrides,
        Set<SourceKind> attemptedKinds,
        Map<String, java.nio.file.Path> filesOnDisk,
        List<AutoCloseable> cleanups
    ) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Set<SourceKind> selectedKinds = new HashSet<>(source.sourceKinds());
        selectedKinds.retainAll(plan.selectedSources());
        if (manifestBuilder != null) {
            for (SourceKind kind : Set.copyOf(selectedKinds)) {
                if (!manifestBuilder.isSourceUsePermitted(plan.contractVersion(), kind)) {
                    stateOverrides.put(
                        kind,
                        new SourceCaptureState.NotCollected(SourceAbsenceReason.GOVERNANCE_NOT_EFFECTIVE)
                    );
                    selectedKinds.remove(kind);
                }
            }
        }
        source.prepareCapture(request, selectedKinds);
        for (SourceKind kind : source.sourceKinds()) {
            if (!selectedKinds.contains(kind)) continue;
            attemptedKinds.add(kind);
            EvidenceContribution contribution;
            try {
                contribution = source.capture(request, Set.of(kind));
            } catch (RuntimeException e) {
                // Sources are captured independently so that one failing collector costs only its
                // own source. Recording a collection error remains conservative: review readiness
                // skips any practice that required this source. Allowing the exception to propagate
                // would instead discard every source already captured for this job.
                //
                // Only failures raised by the collector are absorbed here. The checks below validate
                // the contribution against its contract and must continue to propagate.
                stateOverrides.put(kind, new SourceCaptureState.CollectionError(SourceAbsenceReason.PROVIDER_FAILURE));
                meterRegistry.counter(METRIC_REQUIRED_FAILURE, Tags.of("provider", providerName)).increment();
                log.warn(
                    "Evidence source failed; recording collection error: {} {} — {}",
                    providerName,
                    kind,
                    e.getMessage()
                );
                continue;
            }
            validateContribution(source, Set.of(kind), contribution);
            contribution
                .files()
                .forEach((path, bytes) -> {
                    if (files.put(path, bytes) != null) {
                        throw new IllegalStateException(providerName + " emitted duplicate file " + path);
                    }
                });
            contribution
                .filesOnDisk()
                .forEach((path, file) -> {
                    if (filesOnDisk.put(path, file) != null || files.containsKey(path)) {
                        throw new IllegalStateException(providerName + " emitted duplicate file " + path);
                    }
                });
            if (contribution.cleanup() != null) {
                cleanups.add(contribution.cleanup());
            }
            completeness.putAll(contribution.completeness());
            contentStates.putAll(contribution.contentStates());
            stateOverrides.putAll(contribution.stateOverrides());
            immutableIdentities.putAll(contribution.immutableIdentities());
            observedAt.putAll(contribution.observedAt());
            sourceEffectiveAt.putAll(contribution.sourceEffectiveAt());
        }
        return files;
    }

    private static void validateContribution(
        EvidenceSource source,
        Set<SourceKind> allowedKinds,
        EvidenceContribution contribution
    ) {
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
        Set<SourceKind> emittedKinds = contribution
            .files()
            .keySet()
            .stream()
            .map(source::sourceKindFor)
            .filter(kind -> !allowedKinds.contains(kind))
            .collect(java.util.stream.Collectors.toSet());
        if (!emittedKinds.isEmpty()) {
            throw new IllegalStateException(
                source.getClass().getSimpleName() + " emitted files for sources outside this capture: " + emittedKinds
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
