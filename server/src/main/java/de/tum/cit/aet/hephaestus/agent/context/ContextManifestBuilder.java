package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceContract;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceManifest;
import de.tum.cit.aet.hephaestus.evidence.CompletenessBasis;
import de.tum.cit.aet.hephaestus.evidence.EvidenceAssessment;
import de.tum.cit.aet.hephaestus.evidence.EvidenceViewTransformation;
import de.tum.cit.aet.hephaestus.evidence.FreshnessMode;
import de.tum.cit.aet.hephaestus.evidence.MissingnessKind;
import de.tum.cit.aet.hephaestus.evidence.PracticeReadinessDecision;
import de.tum.cit.aet.hephaestus.evidence.PracticeReadinessReport;
import de.tum.cit.aet.hephaestus.evidence.RepresentationFidelity;
import de.tum.cit.aet.hephaestus.evidence.SourceArtifact;
import de.tum.cit.aet.hephaestus.evidence.SourceAuthority;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureFacts;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceFreshness;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUseAudience;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.practices.EvidenceCompletenessRequirement;
import de.tum.cit.aet.hephaestus.practices.EvidenceFreshnessRequirement;
import de.tum.cit.aet.hephaestus.practices.PracticeObservability;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ContextManifestBuilder {

    static final String INTERNAL_MANIFEST_FILE = "artifact-source-manifest.json";
    static final String READINESS_REPORT_FILE = "practice-readiness-report.json";

    public record PreparedReadiness(List<Practice> readyPractices, PracticeReadinessReport report) {
        public PreparedReadiness {
            readyPractices = List.copyOf(readyPractices);
            Objects.requireNonNull(report, "report");
        }
    }

    public record CaptureMetadata(
        Map<SourceKind, SourceCompleteness> reportedCompleteness,
        Map<SourceKind, SourceContentState> reportedContentStates,
        Map<SourceKind, String> immutableIdentities,
        Map<SourceKind, Instant> observedAt,
        Map<SourceKind, Instant> sourceEffectiveAt,
        Map<SourceKind, SourceCaptureState> stateOverrides,
        Set<SourceKind> attemptedKinds
    ) {
        public CaptureMetadata(
            Map<SourceKind, SourceCompleteness> reportedCompleteness,
            Map<SourceKind, String> immutableIdentities,
            Map<SourceKind, Instant> observedAt,
            Map<SourceKind, Instant> sourceEffectiveAt,
            Map<SourceKind, SourceCaptureState> stateOverrides,
            Set<SourceKind> attemptedKinds
        ) {
            this(
                reportedCompleteness,
                Map.of(),
                immutableIdentities,
                observedAt,
                sourceEffectiveAt,
                stateOverrides,
                attemptedKinds
            );
        }
    }

    private final ContentAddressedStore cas;
    private final FabricLayout layout;
    private final JsonMapper objectMapper;
    private final ArtifactSourceCatalogRegistry catalogs;
    private final Clock clock;

    public ContextManifestBuilder(
        ContentAddressedStore cas,
        FabricLayout layout,
        JsonMapper objectMapper,
        ArtifactSourceCatalogRegistry catalogs,
        Clock clock
    ) {
        this.cas = cas;
        this.layout = layout;
        this.objectMapper = objectMapper;
        this.catalogs = catalogs;
        this.clock = clock;
    }

    void validateEvidenceSources(List<ContentSource> providers) {
        Set<SourceKind> seen = new HashSet<>();
        for (ContentSource provider : providers) {
            if (!(provider instanceof EvidenceSource evidenceSource)) continue;
            for (SourceKind kind : evidenceSource.sourceKinds()) {
                catalogs.requireSource(catalogs.current().version(), kind);
                if (!seen.add(kind)) {
                    throw new IllegalStateException("Multiple evidence providers declare source kind " + kind);
                }
            }
        }
    }

    public ArtifactSourceManifest augment(
        Map<String, byte[]> files,
        Map<String, SourceKind> pathKinds,
        String jobId,
        EvidencePlan plan,
        CaptureMetadata metadata
    ) {
        Instant capturedAt = clock.instant();
        var profile = catalogs.requireProfile(plan.contractVersion(), plan.profileId());
        if (!profile.allowedSources().containsAll(plan.selectedSources())) {
            Set<SourceKind> disallowed = new HashSet<>(plan.selectedSources());
            disallowed.removeAll(profile.allowedSources());
            throw new IllegalArgumentException("Evidence plan contains sources outside profile: " + disallowed);
        }
        List<SourceCapture> captures = profile
            .allowedSources()
            .stream()
            .sorted()
            .map(kind ->
                capture(
                    kind,
                    files,
                    pathKinds,
                    plan,
                    capturedAt,
                    metadata.reportedCompleteness(),
                    metadata.reportedContentStates(),
                    metadata.immutableIdentities(),
                    metadata.observedAt(),
                    metadata.sourceEffectiveAt(),
                    metadata.stateOverrides(),
                    metadata.attemptedKinds()
                )
            )
            .toList();
        ArtifactSourceManifest manifest = new ArtifactSourceManifest(
            plan.contractVersion(),
            catalogs.catalogDigest(),
            plan.profileId(),
            capturedAt,
            captures,
            List.<EvidenceViewTransformation>of()
        );
        try {
            byte[] internalBytes = objectMapper.writeValueAsBytes(manifest);
            persistInternalManifest(jobId, internalBytes);
            files.put(SandboxLayout.MANIFEST_PATH, modelVisibleIndex(manifest));
            return manifest;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Artifact-source manifest generation failed", e);
        }
    }

    boolean isSourceUsePermitted(SourceContractVersion version, SourceKind kind) {
        return catalogs.isSourceUsePermitted(version, kind, SourceUseAudience.PRACTICE_DETECTION);
    }

    public PreparedReadiness prepareReadiness(
        ArtifactSourceManifest manifest,
        List<Practice> practices,
        String jobId,
        Instant temporalAnchor
    ) {
        PracticeReadinessResult result = assessPractices(manifest, practices, temporalAnchor);
        if (result.decisions().isEmpty()) {
            throw new IllegalArgumentException("Cannot persist an empty practice readiness report");
        }
        Instant decidedAt = result.decisions().getFirst().decidedAt();
        PracticeReadinessReport report = new PracticeReadinessReport(
            manifest.contractVersion(),
            manifest.catalogDigest(),
            manifest.profileId(),
            manifest.capturedAt(),
            decidedAt,
            result.decisions()
        );
        persistInternalJson(jobId, READINESS_REPORT_FILE, objectMapper.writeValueAsBytes(report));
        return new PreparedReadiness(result.readyPractices(), report);
    }

    PreparedEvidence restrictTo(PreparedEvidence prepared, EvidencePlan plan) {
        List<SourceCapture> sources = prepared
            .manifest()
            .sources()
            .stream()
            .filter(source -> plan.selectedSources().contains(source.kind()))
            .toList();
        Set<String> retainedArtifacts = sources
            .stream()
            .flatMap(source -> source.artifacts().stream())
            .map(SourceArtifact::path)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> allArtifacts = prepared
            .manifest()
            .sources()
            .stream()
            .flatMap(source -> source.artifacts().stream())
            .map(SourceArtifact::path)
            .collect(java.util.stream.Collectors.toSet());
        Map<String, byte[]> files = new LinkedHashMap<>(prepared.files());
        allArtifacts
            .stream()
            .filter(path -> !retainedArtifacts.contains(path))
            .forEach(files::remove);
        ArtifactSourceManifest restricted = new ArtifactSourceManifest(
            prepared.manifest().contractVersion(),
            prepared.manifest().catalogDigest(),
            prepared.manifest().profileId(),
            prepared.manifest().capturedAt(),
            sources,
            prepared.manifest().viewTransformations()
        );
        files.put(SandboxLayout.MANIFEST_PATH, modelVisibleIndex(restricted));
        return new PreparedEvidence(files, restricted);
    }

    public PracticeReadinessResult assessPractices(ArtifactSourceManifest manifest, List<Practice> practices) {
        return assessPractices(manifest, practices, clock.instant());
    }

    public PracticeReadinessResult assessPractices(
        ArtifactSourceManifest manifest,
        List<Practice> practices,
        Instant temporalAnchor
    ) {
        Objects.requireNonNull(temporalAnchor, "temporalAnchor");
        if (!manifest.viewTransformations().isEmpty()) {
            throw new IllegalArgumentException("Ablated evidence views are not valid for product readiness");
        }
        if (
            !catalogs.current().version().equals(manifest.contractVersion()) ||
            !catalogs.catalogDigest().equals(manifest.catalogDigest())
        ) {
            throw new IllegalArgumentException("Manifest does not reference the runtime's exact source contract");
        }
        Map<SourceKind, SourceCapture> captures = new HashMap<>();
        manifest.sources().forEach(capture -> captures.put(capture.kind(), capture));
        Instant assessedAt = clock.instant();
        List<Practice> ready = new ArrayList<>();
        List<PracticeReadinessDecision> decisions = new ArrayList<>();
        for (Practice practice : practices) {
            var declaration = practice.getEvidence();
            if (declaration == null) {
                throw new IllegalArgumentException("Practice has no evidence declaration: " + practice.getSlug());
            }
            if (
                !declaration.sourceContractVersion().equals(manifest.contractVersion()) ||
                !declaration.profile().equals(manifest.profileId())
            ) {
                throw new IllegalArgumentException(
                    "Practice evidence contract does not match manifest: " + practice.getSlug()
                );
            }
            List<EvidenceAssessment> assessments = new ArrayList<>();
            for (var requirement : declaration.required()) {
                SourceCapture capture = captures.get(requirement.sourceKind());
                boolean available = capture != null && capture.state() instanceof SourceCaptureState.Available;
                SourceCompleteness completeness = available
                    ? ((SourceCaptureState.Available) capture.state()).completeness()
                    : SourceCompleteness.UNKNOWN;
                SourceFreshness freshness = available
                    ? assessFreshness(
                          catalogs.requireSource(manifest.contractVersion(), requirement.sourceKind()),
                          (SourceCaptureState.Available) capture.state(),
                          temporalAnchor,
                          manifest.capturedAt()
                      )
                    : SourceFreshness.UNKNOWN;
                List<String> reasons = new ArrayList<>();
                if (declaration.observability() == PracticeObservability.UNOBSERVABLE) {
                    reasons.add("PRACTICE_UNOBSERVABLE");
                }
                if (!available) reasons.add("SOURCE_NOT_AVAILABLE");
                if (
                    requirement.completeness() == EvidenceCompletenessRequirement.COMPLETE &&
                    completeness != SourceCompleteness.COMPLETE
                ) reasons.add("COMPLETENESS_UNSATISFIED");
                if (
                    requirement.freshness() == EvidenceFreshnessRequirement.CURRENT &&
                    freshness != SourceFreshness.CURRENT
                ) reasons.add("FRESHNESS_UNSATISFIED");
                assessments.add(
                    new EvidenceAssessment(
                        requirement.sourceKind(),
                        manifest.contractVersion(),
                        assessedAt,
                        temporalAnchor,
                        freshness,
                        reasons.isEmpty(),
                        reasons
                    )
                );
            }
            PracticeReadinessDecision decision = new PracticeReadinessDecision(
                practice.getSlug(),
                assessedAt,
                assessments.stream().allMatch(EvidenceAssessment::acceptable),
                assessments
            );
            decisions.add(decision);
            if (decision.ready()) ready.add(practice);
        }
        return new PracticeReadinessResult(ready, decisions);
    }

    private SourceFreshness assessFreshness(
        ArtifactSourceContract contract,
        SourceCaptureState.Available capture,
        Instant temporalAnchor,
        Instant capturedAt
    ) {
        var policy = contract.freshnessPolicy();
        if (policy.mode() == FreshnessMode.NOT_APPLICABLE) return SourceFreshness.UNKNOWN;
        if (policy.mode() == FreshnessMode.PINNED_IDENTITY) {
            return capture.facts().immutableIdentity() != null ? SourceFreshness.CURRENT : SourceFreshness.UNKNOWN;
        }
        if (policy.mode() == FreshnessMode.MAX_AGE) {
            Instant observed = capture.facts().observedAt();
            if (observed == null || observed.isAfter(capturedAt)) return SourceFreshness.UNKNOWN;
            return observed.plusSeconds(policy.maxAgeSeconds()).isBefore(temporalAnchor)
                ? SourceFreshness.STALE
                : SourceFreshness.CURRENT;
        }
        Instant eventTime = capture.facts().sourceEffectiveAt();
        if (eventTime == null || eventTime.isAfter(capturedAt)) return SourceFreshness.UNKNOWN;
        return eventTime.isAfter(temporalAnchor) ? SourceFreshness.UNKNOWN : SourceFreshness.CURRENT;
    }

    private SourceCapture capture(
        SourceKind kind,
        Map<String, byte[]> files,
        Map<String, SourceKind> pathKinds,
        EvidencePlan plan,
        Instant capturedAt,
        Map<SourceKind, SourceCompleteness> reportedCompleteness,
        Map<SourceKind, SourceContentState> reportedContentStates,
        Map<SourceKind, String> immutableIdentities,
        Map<SourceKind, Instant> observedAt,
        Map<SourceKind, Instant> sourceEffectiveAt,
        Map<SourceKind, SourceCaptureState> stateOverrides,
        Set<SourceKind> attemptedKinds
    ) {
        ArtifactSourceContract contract = catalogs.requireSource(plan.contractVersion(), kind);
        if (!plan.selectedSources().contains(kind)) {
            return missingCapture(contract, new SourceCaptureState.NotCollected("MINIMIZED"));
        }
        SourceCaptureState override = stateOverrides.get(kind);
        if (override != null) {
            return missingCapture(contract, override);
        }
        if (!attemptedKinds.contains(kind)) {
            return missingCapture(contract, new SourceCaptureState.Unavailable("NO_PROVIDER"));
        }
        List<SourceArtifact> artifacts = pathKinds
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().equals(kind))
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> artifact(entry.getKey(), files.get(entry.getKey())))
            .toList();
        if (artifacts.isEmpty() && !contract.completenessPolicy().supportsEmpty()) {
            return missingCapture(contract, new SourceCaptureState.Unavailable("EMPTY_NOT_VALID"));
        }
        SourceCompleteness completeness = reportedCompleteness.getOrDefault(
            kind,
            inferredCompleteness(contract, artifacts, files)
        );
        if (
            (completeness == SourceCompleteness.COMPLETE && !contract.completenessPolicy().supportsComplete()) ||
            (completeness == SourceCompleteness.PARTIAL && !contract.completenessPolicy().supportsPartial())
        ) {
            throw new IllegalStateException(
                kind + " reported completeness forbidden by its source contract: " + completeness
            );
        }
        SourceContentState content = reportedContentStates.getOrDefault(
            kind,
            artifacts.isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY
        );
        if (content == SourceContentState.EMPTY && !contract.completenessPolicy().supportsEmpty()) {
            throw new IllegalStateException(kind + " reported EMPTY although its source contract forbids it");
        }
        SourceCaptureFacts facts = new SourceCaptureFacts(
            capturedAt,
            sourceEffectiveAt.get(kind),
            observedAt.get(kind),
            immutableIdentities.get(kind),
            contract.selectionScope(),
            completenessBasis(completeness, contract),
            fidelity(contract.authority())
        );
        return new SourceCapture(kind, new SourceCaptureState.Available(content, completeness, facts), artifacts);
    }

    private static SourceCapture missingCapture(ArtifactSourceContract contract, SourceCaptureState state) {
        MissingnessKind missingness = missingness(state);
        if (!contract.supportedMissingness().contains(missingness)) {
            throw new IllegalArgumentException(contract.kind() + " does not support missingness state " + missingness);
        }
        return new SourceCapture(contract.kind(), state, List.of());
    }

    private SourceCompleteness inferredCompleteness(
        ArtifactSourceContract contract,
        List<SourceArtifact> artifacts,
        Map<String, byte[]> files
    ) {
        if (artifacts.isEmpty()) {
            if (!contract.completenessPolicy().supportsEmpty()) return SourceCompleteness.UNKNOWN;
            if (contract.completenessPolicy().supportsComplete()) return SourceCompleteness.COMPLETE;
            if (contract.completenessPolicy().supportsPartial()) return SourceCompleteness.PARTIAL;
            return SourceCompleteness.UNKNOWN;
        }
        List<Boolean> truncationMarkers = artifacts
            .stream()
            .map(artifact -> truncationMarker(files.get(artifact.path())))
            .flatMap(Optional::stream)
            .toList();
        if (truncationMarkers.stream().anyMatch(Boolean.TRUE::equals)) {
            return SourceCompleteness.PARTIAL;
        }
        if (!truncationMarkers.isEmpty() && contract.completenessPolicy().supportsComplete()) {
            return SourceCompleteness.COMPLETE;
        }
        return contract.completenessPolicy().supportsPartial()
            ? SourceCompleteness.PARTIAL
            : SourceCompleteness.UNKNOWN;
    }

    private Optional<Boolean> truncationMarker(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes[0] != '{') {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(bytes);
            return node.has("truncated") && node.path("truncated").isBoolean()
                ? Optional.of(node.path("truncated").asBoolean())
                : Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private SourceArtifact artifact(String path, byte[] bytes) {
        if (bytes == null) {
            throw new IllegalStateException("Evidence artifact has null bytes: " + path);
        }
        return new SourceArtifact(path, mediaType(path), cas.put(bytes), bytes.length);
    }

    private byte[] modelVisibleIndex(ArtifactSourceManifest manifest) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("contractVersion", manifest.contractVersion().value());
        root.put("profile", manifest.profileId().value());
        ArrayNode sources = root.putArray("sources");
        for (SourceCapture capture : manifest.sources()) {
            ObjectNode node = sources.addObject();
            node.put("kind", capture.kind().value());
            node.put("availability", availability(capture.state()));
            if (capture.state() instanceof SourceCaptureState.Available available) {
                node.put("content", available.content().name());
                node.put("completeness", available.completeness().name());
                ArrayNode paths = node.putArray("paths");
                capture.artifacts().stream().map(SourceArtifact::path).sorted().forEach(paths::add);
                ArrayNode artifacts = node.putArray("artifacts");
                capture
                    .artifacts()
                    .stream()
                    .sorted(Comparator.comparing(SourceArtifact::path))
                    .forEach(artifact -> {
                        ObjectNode artifactNode = artifacts.addObject();
                        artifactNode.put("path", artifact.path());
                        artifactNode.put("sha256", artifact.sha256());
                    });
            }
        }
        return objectMapper.writeValueAsBytes(root);
    }

    private static String availability(SourceCaptureState state) {
        if (state instanceof SourceCaptureState.Available) return "AVAILABLE";
        if (state instanceof SourceCaptureState.NotCollected) return "NOT_COLLECTED";
        if (state instanceof SourceCaptureState.Unavailable) return "UNAVAILABLE";
        if (state instanceof SourceCaptureState.Redacted) return "REDACTED";
        return "COLLECTION_ERROR";
    }

    private static MissingnessKind missingness(SourceCaptureState state) {
        if (state instanceof SourceCaptureState.NotCollected) return MissingnessKind.NOT_COLLECTED;
        if (state instanceof SourceCaptureState.Unavailable) return MissingnessKind.UNAVAILABLE;
        if (state instanceof SourceCaptureState.Redacted) return MissingnessKind.REDACTED;
        if (state instanceof SourceCaptureState.CollectionError) return MissingnessKind.COLLECTION_ERROR;
        throw new IllegalArgumentException("AVAILABLE is not a missingness override");
    }

    private void persistInternalManifest(String jobId, byte[] bytes) {
        try {
            Path dir = layout.jobDir(jobId);
            Files.createDirectories(dir);
            writeAtomically(dir, INTERNAL_MANIFEST_FILE, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist artifact-source manifest for job " + jobId, e);
        }
    }

    private void persistInternalJson(String jobId, String fileName, byte[] bytes) {
        try {
            Path dir = layout.jobDir(jobId);
            Files.createDirectories(dir);
            writeAtomically(dir, fileName, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist " + fileName + " for job " + jobId, e);
        }
    }

    private static void writeAtomically(Path dir, String fileName, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(dir, fileName, ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(
                temporary,
                dir.resolve(fileName),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static CompletenessBasis completenessBasis(
        SourceCompleteness completeness,
        ArtifactSourceContract contract
    ) {
        if (completeness == SourceCompleteness.UNKNOWN) {
            return CompletenessBasis.UNKNOWN;
        }
        if (completeness == SourceCompleteness.PARTIAL) {
            return CompletenessBasis.BOUNDED_SCOPE;
        }
        return switch (contract.captureTime()) {
            case PINNED_IMMUTABLE_IDENTITY -> CompletenessBasis.IMMUTABLE_OBJECT;
            default -> CompletenessBasis.BOUNDED_SCOPE;
        };
    }

    private static RepresentationFidelity fidelity(SourceAuthority authority) {
        return switch (authority) {
            case UPSTREAM_SNAPSHOT, SYNCHRONIZED_MIRROR -> RepresentationFidelity.EXACT;
            case DETERMINISTIC_DERIVATION -> RepresentationFidelity.LOSSLESS_DERIVATION;
            case LOSSY_DERIVATION -> RepresentationFidelity.LOSSY_DERIVATION;
        };
    }

    private static String mediaType(String path) {
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".md")) return "text/markdown";
        if (path.endsWith(".patch")) return "text/x-diff";
        return "application/octet-stream";
    }
}
