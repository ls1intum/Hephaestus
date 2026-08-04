package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceContract;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceManifest;
import de.tum.cit.aet.hephaestus.evidence.AutomatedAssessmentReadinessDecision;
import de.tum.cit.aet.hephaestus.evidence.AutomatedAssessmentReadinessReason;
import de.tum.cit.aet.hephaestus.evidence.AutomatedAssessmentReadinessReport;
import de.tum.cit.aet.hephaestus.evidence.CompletenessBasis;
import de.tum.cit.aet.hephaestus.evidence.EvidenceViewTransformation;
import de.tum.cit.aet.hephaestus.evidence.FreshnessMode;
import de.tum.cit.aet.hephaestus.evidence.RepresentationFidelity;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceState;
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
import de.tum.cit.aet.hephaestus.evidence.SourceReadinessCheck;
import de.tum.cit.aet.hephaestus.evidence.SourceReadinessReason;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.practices.EvidenceCompletenessRequirement;
import de.tum.cit.aet.hephaestus.practices.EvidenceFreshnessRequirement;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedAssessmentMode;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceSufficiency;
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

@Component
public class ContextManifestBuilder {

    static final String INTERNAL_MANIFEST_FILE = "artifact-source-manifest.json";
    static final String AUTOMATED_ASSESSMENT_READINESS_REPORT_FILE = "automated-assessment-readiness-report.json";

    public record PreparedAutomatedAssessmentReadiness(
        List<Practice> readyPractices,
        AutomatedAssessmentReadinessReport report
    ) {
        public PreparedAutomatedAssessmentReadiness {
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
        var profile = catalogs.requireProfile(plan.contractVersion(), plan.evidenceProfile());
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
            plan.evidenceProfile(),
            capturedAt,
            captures,
            List.<EvidenceViewTransformation>of()
        );
        try {
            byte[] internalBytes = objectMapper.writeValueAsBytes(manifest);
            persistInternalManifest(jobId, internalBytes);
            files.put(SandboxLayout.MANIFEST_PATH, internalBytes);
            return manifest;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Artifact-source manifest generation failed", e);
        }
    }

    boolean isSourceUsePermitted(SourceContractVersion version, SourceKind kind) {
        return catalogs.isSourceUsePermitted(version, kind, SourceUsePurpose.AUTOMATED_PRACTICE_ASSESSMENT);
    }

    public PreparedAutomatedAssessmentReadiness prepareAutomatedAssessmentReadiness(
        ArtifactSourceManifest manifest,
        List<Practice> practices,
        String jobId,
        Instant temporalAnchor
    ) {
        AutomatedAssessmentReadinessResult result = checkAutomatedAssessmentReadiness(
            manifest,
            practices,
            temporalAnchor
        );
        if (result.decisions().isEmpty()) {
            throw new IllegalArgumentException("Cannot persist an empty automated-assessment readiness report");
        }
        Instant decidedAt = result.decisions().getFirst().decidedAt();
        AutomatedAssessmentReadinessReport report = new AutomatedAssessmentReadinessReport(
            manifest.contractVersion(),
            manifest.catalogDigest(),
            manifest.evidenceProfile(),
            manifest.capturedAt(),
            decidedAt,
            result.decisions()
        );
        persistInternalJson(jobId, AUTOMATED_ASSESSMENT_READINESS_REPORT_FILE, objectMapper.writeValueAsBytes(report));
        return new PreparedAutomatedAssessmentReadiness(result.readyPractices(), report);
    }

    PreparedEvidence restrictTo(PreparedEvidence prepared, EvidencePlan plan) {
        List<SourceCapture> sources = prepared
            .manifest()
            .sources()
            .stream()
            .map(source ->
                plan.selectedSources().contains(source.kind())
                    ? source
                    : new SourceCapture(source.kind(), new SourceCaptureState.NotCollected("MINIMIZED"), List.of())
            )
            .toList();
        Set<String> retainedArtifacts = sources
            .stream()
            .filter(source -> plan.selectedSources().contains(source.kind()))
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
            prepared.manifest().evidenceProfile(),
            prepared.manifest().capturedAt(),
            sources,
            prepared.manifest().viewTransformations()
        );
        files.put(SandboxLayout.MANIFEST_PATH, objectMapper.writeValueAsBytes(restricted));
        return new PreparedEvidence(files, restricted);
    }

    public AutomatedAssessmentReadinessResult checkAutomatedAssessmentReadiness(
        ArtifactSourceManifest manifest,
        List<Practice> practices
    ) {
        return checkAutomatedAssessmentReadiness(manifest, practices, clock.instant());
    }

    public AutomatedAssessmentReadinessResult checkAutomatedAssessmentReadiness(
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
        Set<SourceKind> expectedKinds = catalogs
            .requireProfile(manifest.contractVersion(), manifest.evidenceProfile())
            .allowedSources();
        Set<SourceKind> capturedKinds = manifest
            .sources()
            .stream()
            .map(SourceCapture::kind)
            .collect(java.util.stream.Collectors.toSet());
        if (!capturedKinds.equals(expectedKinds)) {
            throw new IllegalArgumentException("Manifest source captures do not match its evidence profile");
        }
        Map<SourceKind, SourceCapture> captures = new HashMap<>();
        manifest.sources().forEach(capture -> captures.put(capture.kind(), capture));
        Instant checkedAt = clock.instant();
        List<Practice> ready = new ArrayList<>();
        List<AutomatedAssessmentReadinessDecision> decisions = new ArrayList<>();
        for (Practice practice : practices) {
            var requirements = practice.getAutomatedAssessmentPolicy();
            if (requirements == null) {
                throw new IllegalArgumentException("Practice has no evidence requirements: " + practice.getSlug());
            }
            if (
                !requirements.sourceContractVersion().equals(manifest.contractVersion()) ||
                !requirements.evidenceProfile().equals(manifest.evidenceProfile())
            ) {
                throw new IllegalArgumentException(
                    "Practice evidence contract does not match manifest: " + practice.getSlug()
                );
            }
            List<AutomatedAssessmentReadinessReason> decisionReasons = new ArrayList<>();
            switch (requirements.automatedAssessment().mode()) {
                case NONE -> decisionReasons.add(AutomatedAssessmentReadinessReason.NO_AUTOMATED_ASSESSMENT);
                case LANGUAGE_MODEL -> {
                }
            }
            switch (requirements.automatedAssessment().evidenceSufficiency()) {
                case DECLARED_EVIDENCE_INSUFFICIENT -> decisionReasons.add(
                    AutomatedAssessmentReadinessReason.DECLARED_EVIDENCE_INSUFFICIENT
                );
                case SUFFICIENT_WHEN_REQUIREMENTS_MET, NONE -> {
                }
            }
            List<SourceReadinessCheck> sourceChecks = new ArrayList<>();
            for (var requirement : requirements.requiredEvidence()) {
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
                List<SourceReadinessReason> reasons = new ArrayList<>();
                if (!available) reasons.add(SourceReadinessReason.SOURCE_NOT_AVAILABLE);
                if (
                    requirement.completeness() == EvidenceCompletenessRequirement.COMPLETE &&
                    completeness != SourceCompleteness.COMPLETE
                ) reasons.add(SourceReadinessReason.SOURCE_INCOMPLETE);
                if (
                    requirement.freshness() == EvidenceFreshnessRequirement.CURRENT &&
                    freshness != SourceFreshness.CURRENT
                ) reasons.add(SourceReadinessReason.SOURCE_NOT_CURRENT);
                sourceChecks.add(
                    new SourceReadinessCheck(
                        requirement.sourceKind(),
                        manifest.contractVersion(),
                        checkedAt,
                        temporalAnchor,
                        freshness,
                        reasons.isEmpty(),
                        reasons
                    )
                );
            }
            AutomatedAssessmentReadinessDecision decision = new AutomatedAssessmentReadinessDecision(
                practice.getSlug(),
                checkedAt,
                decisionReasons.isEmpty() && sourceChecks.stream().allMatch(SourceReadinessCheck::meetsRequirements),
                decisionReasons,
                sourceChecks
            );
            decisions.add(decision);
            if (decision.ready()) ready.add(practice);
        }
        return new AutomatedAssessmentReadinessResult(ready, decisions);
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
        SourceAbsenceState absenceState = absenceState(state);
        if (!contract.supportedAbsenceStates().contains(absenceState)) {
            throw new IllegalArgumentException(contract.kind() + " does not support absence state " + absenceState);
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

    private static SourceAbsenceState absenceState(SourceCaptureState state) {
        if (state instanceof SourceCaptureState.NotCollected) return SourceAbsenceState.NOT_COLLECTED;
        if (state instanceof SourceCaptureState.Unavailable) return SourceAbsenceState.UNAVAILABLE;
        if (state instanceof SourceCaptureState.Redacted) return SourceAbsenceState.REDACTED;
        if (state instanceof SourceCaptureState.CollectionError) return SourceAbsenceState.COLLECTION_ERROR;
        throw new IllegalArgumentException("AVAILABLE is not an absence state");
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
        return switch (contract.captureTimeBasis()) {
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
