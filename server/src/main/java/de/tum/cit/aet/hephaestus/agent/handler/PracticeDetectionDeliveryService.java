package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.EvidencePlan;
import de.tum.cit.aet.hephaestus.agent.context.providers.DocumentContentSource;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationSourceLiveness;
import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.EvidenceStance;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeDetectionCompletedEvent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PracticeDetectionDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(PracticeDetectionDeliveryService.class);

    private final PracticeRevisionRepository practiceRevisionRepository;
    private final ObservationRepository observationRepository;
    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final ConversationSourceLiveness conversationSourceLiveness;
    private final DocumentProjection documentProjection;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ContentAddressedStore cas;
    private final ArtifactSourceCatalogRegistry sourceCatalogs;

    public PracticeDetectionDeliveryService(
        PracticeRevisionRepository practiceRevisionRepository,
        ObservationRepository observationRepository,
        PullRequestRepository pullRequestRepository,
        IssueRepository issueRepository,
        ConversationSourceLiveness conversationSourceLiveness,
        DocumentProjection documentProjection,
        ApplicationEventPublisher eventPublisher,
        ObjectMapper objectMapper,
        ContentAddressedStore cas,
        ArtifactSourceCatalogRegistry sourceCatalogs
    ) {
        this.practiceRevisionRepository = practiceRevisionRepository;
        this.observationRepository = observationRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.conversationSourceLiveness = conversationSourceLiveness;
        this.documentProjection = documentProjection;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.cas = cas;
        this.sourceCatalogs = sourceCatalogs;
    }

    /**
     * Job-metadata key carrying {@link ObservationOrigin}. Written at submission and read back here rather
     * than re-derived: by delivery time, what occasioned the run is no longer reconstructable from the job
     * row, and a scheduled sweep over live threads looks exactly like a backfill sweep over old ones.
     */
    public static final String ORIGIN_METADATA_KEY = "observation_origin";

    private record Target(ArtifactKind type, Long id, Long aboutUserId) {}

    /**
     * The origin stamped on this job, or {@link ObservationOrigin#LIVE} for a job submitted before the key
     * existed. Falling back rather than failing is right for exactly one reason: every job that predates the
     * key came from the event-driven path, so LIVE is the true value and not a guess.
     */
    public static ObservationOrigin originOf(@Nullable JsonNode metadata) {
        JsonNode node = metadata == null ? null : metadata.get(ORIGIN_METADATA_KEY);
        if (node == null || !node.isString()) {
            return ObservationOrigin.LIVE;
        }
        try {
            return ObservationOrigin.valueOf(node.asString());
        } catch (IllegalArgumentException unknown) {
            // A value this build does not know is a newer writer, not a licence to guess: refuse rather
            // than silently file the run under LIVE and pollute the only population we treat as unbiased.
            throw new JobDeliveryException("Unknown observation origin in job metadata: " + node.asString());
        }
    }

    @Transactional
    public DeliveryResult deliver(AgentJob job, List<ValidatedFinding> validFindings) {
        Long workspaceId = job.getWorkspace().getId();
        JsonNode metadata = job.getMetadata();
        if (metadata == null) {
            throw new JobDeliveryException("Missing job metadata: jobId=" + job.getId());
        }

        EvidenceBoundary evidenceBoundary = evidenceBoundary(job);
        for (SourceKind kind : evidenceBoundary.allowedSources()) {
            if (
                !sourceCatalogs.isSourceUsePermitted(
                    evidenceBoundary.contractVersion(),
                    kind,
                    SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY
                )
            ) {
                throw new JobDeliveryException(
                    "Evidence source authorization was withdrawn before delivery: source=" +
                        kind +
                        ", jobId=" +
                        job.getId()
                );
            }
        }
        Target target = resolveTarget(job, metadata);
        Map<String, PracticeRevision> revisionsBySlug = admittedRevisions(job, workspaceId);
        for (ValidatedFinding finding : validFindings) {
            PracticeRevision revision = revisionsBySlug.get(finding.practiceSlug());
            if (revision == null) {
                throw new JobDeliveryException(
                    "Finding references a practice not admitted to the job: slug=" +
                        finding.practiceSlug() +
                        ", jobId=" +
                        job.getId()
                );
            }
            enforceEvidenceBoundary(finding, revision, evidenceBoundary, job);
        }

        ObservationOrigin origin = originOf(metadata);
        Long aboutUserId = target.aboutUserId();
        ArtifactKind artifactKind = target.type();
        Long artifactId = target.id();

        int inserted = 0;
        int discardedDuplicate = 0;
        boolean hasNegative = false;
        Instant observedAt = Instant.now();

        // Keyed by finding identity because equal findings still represent distinct occurrences.
        Map<ValidatedFinding, ObservationKeys> observationKeys = new IdentityHashMap<>();

        for (int i = 0; i < validFindings.size(); i++) {
            ValidatedFinding finding = validFindings.get(i);

            PracticeRevision revision = revisionsBySlug.get(finding.practiceSlug());
            Practice practice = revision.getPractice();

            // The index disambiguates multiple findings for the same practice on one artifact.
            String occurrenceKey =
                finding.practiceSlug() + ":" + i + ":" + artifactKind.value() + ":" + artifactId + ":" + job.getId();

            String evidenceJson = null;
            if (finding.evidence() != null) {
                try {
                    evidenceJson = objectMapper.writeValueAsString(evidenceForPersistence(finding.evidence()));
                } catch (JacksonException e) {
                    throw new JobDeliveryException("Could not serialize validated evidence: jobId=" + job.getId(), e);
                }
            }

            // Cross-run identity (ADR 0021): a content-derived key that is STABLE across re-detections —
            // so a later Feedback can supersede instead of re-post and the RQ "do practices change over time"
            // becomes answerable. Derived from what the finding is ABOUT, never from the job or line number.
            String recurrenceKey = ObservationFingerprint.compute(
                finding.practiceSlug(),
                artifactKind.value(),
                artifactId,
                aboutUserId,
                firstLocationPath(finding.evidence())
            );
            observationKeys.put(finding, new ObservationKeys(occurrenceKey, recurrenceKey));

            Long practiceRevisionId = revision.getId();

            // Self-enforce the ADR-0022 invariant that Observation.@PrePersist applies but the native
            // insertIfAbsent path bypasses: severity is an impact band for a BAD observation only, so it
            // must be null unless the assessment is BAD. Idempotent for an already-coerced finding.
            String severityName =
                finding.assessment() == Assessment.BAD && finding.severity() != null ? finding.severity().name() : null;

            int rows = observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                occurrenceKey,
                job.getId(),
                practice.getId(),
                practiceRevisionId,
                artifactKind.value(),
                artifactId,
                aboutUserId,
                finding.title(),
                finding.presence().name(),
                finding.assessment() == null ? null : finding.assessment().name(),
                severityName,
                finding.confidence(),
                evidenceJson,
                finding.reasoning(),
                recurrenceKey,
                observedAt,
                origin.name()
            );

            if (rows == 1) {
                inserted++;
            } else {
                discardedDuplicate++;
            }
            // Gate on the assessment, not the insert result: a retry's insertIfAbsent returns 0 for an
            // already-persisted finding, yet hasNegative must still reflect it for the delivery gate.
            if (finding.assessment() == Assessment.BAD) {
                hasNegative = true;
            }
        }

        log.info(
            "Practice reviews delivery: inserted={}, duplicate={}, jobId={}",
            inserted,
            discardedDuplicate,
            job.getId()
        );

        eventPublisher.publishEvent(
            new PracticeDetectionCompletedEvent(
                job.getId(),
                workspaceId,
                artifactKind,
                artifactId,
                aboutUserId, // the event's developerId field == aboutUserId (author-side subject today)
                inserted,
                discardedDuplicate,
                hasNegative
            )
        );

        return new DeliveryResult(inserted, discardedDuplicate, hasNegative, observationKeys);
    }

    private void enforceEvidenceBoundary(
        ValidatedFinding finding,
        PracticeRevision revision,
        EvidenceBoundary boundary,
        AgentJob job
    ) {
        if (revision.getAutomatedReviewPolicy() == null || revision.getBindings() == null) {
            throw new JobDeliveryException(
                "Practice has no evidence requirements: slug=" + finding.practiceSlug() + ", jobId=" + job.getId()
            );
        }
        JsonNode evidence = finding.evidence();
        JsonNode citations = evidence == null ? null : evidence.get("citations");
        if (citations == null || !citations.isArray() || citations.isEmpty()) {
            throw new JobDeliveryException(
                "Finding has no source-bound evidence citation: slug=" +
                    finding.practiceSlug() +
                    ", jobId=" +
                    job.getId()
            );
        }
        // Only what the bindings this occasion matched declared. A citation to a source another
        // binding of the same practice reads is still out of bounds here: that source was never staged
        // for this run, so a quote from it cannot have been read.
        // Plus what every run stages regardless of any binding. These are declared sources with staged
        // bytes, so a claim about an earlier observation is held to the same quote check as a claim
        // about a diff — the point of staging history as evidence rather than as loose context.
        Set<SourceKind> declared = new HashSet<>(EvidencePlan.WORKSPACE_CONTEXT_SOURCES);
        Set<SourceKind> exhaustive = new HashSet<>();
        PracticeBinding.needsFor(revision.getBindings(), PracticeCatalogInjector.signalOf(job)).forEach(need -> {
            declared.add(need.sourceKind());
            if (need.stance() == EvidenceStance.EXHAUSTIVE) {
                exhaustive.add(need.sourceKind());
            }
        });
        enforceRecordedSearch(finding, declared, exhaustive, boundary, job);
        for (JsonNode citation : citations) {
            JsonNode sourceKind = citation.path("sourceKind");
            JsonNode artifactPath = citation.path("artifactPath");
            JsonNode path = citation.path("path");
            JsonNode side = citation.path("side");
            JsonNode startLine = citation.path("startLine");
            JsonNode endLine = citation.path("endLine");
            JsonNode quote = citation.path("quote");
            JsonNode quoteSha256 = citation.path("quoteSha256");
            boolean redactedSecretCitation =
                "secret-diff-scanner".equals(evidence.path("detector").asString()) &&
                quote.isMissingNode() &&
                quoteSha256.isTextual() &&
                quoteSha256.asString().matches("[0-9a-f]{64}");
            if (
                !citation.isObject() ||
                !sourceKind.isTextual() ||
                !artifactPath.isTextual() ||
                !path.isTextual() ||
                ("scm.pull-request.diff".equals(sourceKind.asText()) &&
                    (!side.isTextual() || !("OLD".equals(side.asText()) || "NEW".equals(side.asText())))) ||
                (!"scm.pull-request.diff".equals(sourceKind.asText()) && !side.isMissingNode()) ||
                !startLine.isIntegralNumber() ||
                startLine.asInt() < 1 ||
                (!endLine.isMissingNode() && (!endLine.isIntegralNumber() || endLine.asInt() < startLine.asInt())) ||
                (!quote.isTextual() && !redactedSecretCitation)
            ) {
                throw new JobDeliveryException(
                    "Finding has an invalid evidence citation: slug=" +
                        finding.practiceSlug() +
                        ", jobId=" +
                        job.getId()
                );
            }
            SourceKind kind;
            try {
                kind = new SourceKind(sourceKind.asText());
            } catch (IllegalArgumentException e) {
                throw new JobDeliveryException(
                    "Finding has invalid evidence-source attribution: slug=" +
                        finding.practiceSlug() +
                        ", jobId=" +
                        job.getId(),
                    e
                );
            }
            SourceArtifactRef artifact = boundary.artifacts().get(artifactPath.asText());
            if (
                !declared.contains(kind) ||
                !boundary.allowedSources().contains(kind) ||
                artifact == null ||
                !artifact.kind().equals(kind)
            ) {
                throw new JobDeliveryException(
                    "Finding cited unavailable, undeclared, or misattributed evidence source " +
                        kind +
                        ": slug=" +
                        finding.practiceSlug() +
                        ", jobId=" +
                        job.getId()
                );
            }
            String exactQuote = quote.asText("");
            if (!redactedSecretCitation && exactQuote.isBlank()) {
                throw new JobDeliveryException(
                    "Finding has an empty evidence quote: slug=" + finding.practiceSlug() + ", jobId=" + job.getId()
                );
            }
            byte[] content = cas
                .get(artifact.sha256())
                .orElseThrow(() ->
                    new JobDeliveryException(
                        "Cited evidence artifact is no longer available: path=" +
                            artifactPath.asText() +
                            ", jobId=" +
                            job.getId()
                    )
                );
            String artifactContent = new String(content, StandardCharsets.UTF_8);
            if (!"scm.pull-request.diff".equals(kind.value()) && !artifactContent.contains(exactQuote)) {
                throw new JobDeliveryException(
                    "Evidence quote does not occur in the cited artifact: path=" +
                        artifactPath.asText() +
                        ", jobId=" +
                        job.getId()
                );
            }
            if (
                "scm.pull-request.diff".equals(kind.value()) &&
                !(redactedSecretCitation
                    ? diffContainsRedactedCitation(
                          artifactContent,
                          path.asText(),
                          side.asText(),
                          startLine.asInt(),
                          quoteSha256.asText()
                      )
                    : diffContainsCitation(
                          artifactContent,
                          path.asText(),
                          side.asText(),
                          startLine.asInt(),
                          endLine.isMissingNode() ? startLine.asInt() : endLine.asInt(),
                          exactQuote
                      ))
            ) {
                throw new JobDeliveryException(
                    "Evidence quote does not match the cited diff location: path=" +
                        path.asText() +
                        ", line=" +
                        startLine.asInt() +
                        ", jobId=" +
                        job.getId()
                );
            }
        }
    }

    private static boolean diffContainsRedactedCitation(
        String diff,
        String citedPath,
        String citedSide,
        int citedLine,
        String quoteSha256
    ) {
        String oldPath = null;
        String newPath = null;
        for (String storedLine : diff.split("\n", -1)) {
            String line = storedLine;
            Integer lineNumber = null;
            if (storedLine.startsWith("[L")) {
                int end = storedLine.indexOf("] ");
                if (end <= 2) continue;
                try {
                    lineNumber = Integer.parseInt(storedLine.substring(2, end));
                    line = storedLine.substring(end + 2);
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            if (line.startsWith("--- ")) oldPath = parseDiffPath(line.substring(4));
            if (line.startsWith("+++ ")) newPath = parseDiffPath(line.substring(4));
            if (lineNumber == null) continue;
            String lineSide = line.startsWith("-") ? "OLD" : "NEW";
            String linePath = "OLD".equals(lineSide) ? oldPath : newPath;
            if (lineNumber == citedLine && citedSide.equals(lineSide) && citedPath.equals(linePath)) {
                String content = line.startsWith("+") || line.startsWith("-") ? line.substring(1) : line;
                return ProvenanceDigest.sha256Hex(content.strip().getBytes(StandardCharsets.UTF_8)).equals(quoteSha256);
            }
        }
        return false;
    }

    /**
     * Holds an {@code ABSENT} observation to the search it recorded.
     *
     * <p>An absence is a universal claim, and the only claim a fragment of a corpus can never support:
     * a partial capture of the review threads is equally consistent with "nobody raised it" and "the
     * raising was in the part we did not fetch". {@link EvidenceStance#EXHAUSTIVE} is the practice
     * declaring the corpus its absence ranges over, so those sources are exactly what the search must
     * have covered.
     *
     * <p>Enforced here and not only in the sandbox because the in-sandbox normalizer runs inside the
     * thing it is checking. A runner that crashed, an older image, or a rescued text payload all reach
     * delivery without it having run; a guard that can be skipped by the party it constrains is
     * advice, not a boundary. This is the same reason the citation check below is duplicated here.
     */
    private void enforceRecordedSearch(
        ValidatedFinding finding,
        Set<SourceKind> declared,
        Set<SourceKind> exhaustive,
        EvidenceBoundary boundary,
        AgentJob job
    ) {
        if (finding.presence() != Presence.ABSENT) {
            return;
        }
        JsonNode search = finding.evidence() == null ? null : finding.evidence().get("search");
        JsonNode consulted = search == null ? null : search.get("consulted");
        if (
            search == null ||
            consulted == null ||
            !consulted.isArray() ||
            consulted.isEmpty() ||
            !search.path("lookedFor").isTextual() ||
            search.path("lookedFor").asString().isBlank() ||
            !search.path("boundary").isTextual() ||
            search.path("boundary").asString().isBlank()
        ) {
            throw new JobDeliveryException(
                "An ABSENT observation must record where it searched: slug=" +
                    finding.practiceSlug() +
                    ", jobId=" +
                    job.getId()
            );
        }
        Set<SourceKind> searched = new HashSet<>();
        for (JsonNode kind : consulted) {
            if (!kind.isTextual()) {
                throw new JobDeliveryException(
                    "Recorded search names a non-textual source: slug=" +
                        finding.practiceSlug() +
                        ", jobId=" +
                        job.getId()
                );
            }
            SourceKind sourceKind;
            try {
                sourceKind = new SourceKind(kind.asString());
            } catch (IllegalArgumentException e) {
                throw new JobDeliveryException(
                    "Recorded search names an invalid source: slug=" +
                        finding.practiceSlug() +
                        ", jobId=" +
                        job.getId(),
                    e
                );
            }
            // Same boundary the citations answer to: a source this run never staged cannot have been
            // searched, so claiming it was is the absence-shaped version of citing evidence we never had.
            if (!declared.contains(sourceKind) || !boundary.allowedSources().contains(sourceKind)) {
                throw new JobDeliveryException(
                    "Recorded search claims an undeclared or unavailable source " +
                        sourceKind +
                        ": slug=" +
                        finding.practiceSlug() +
                        ", jobId=" +
                        job.getId()
                );
            }
            searched.add(sourceKind);
        }
        if (!searched.containsAll(exhaustive)) {
            Set<SourceKind> unsearched = new HashSet<>(exhaustive);
            unsearched.removeAll(searched);
            throw new JobDeliveryException(
                "An ABSENT observation did not search the sources its practice asserts absence over " +
                    unsearched +
                    ": slug=" +
                    finding.practiceSlug() +
                    ", jobId=" +
                    job.getId()
            );
        }
    }

    private static JsonNode evidenceForPersistence(JsonNode evidence) {
        JsonNode persisted = evidence.deepCopy();
        if ("secret-diff-scanner".equals(persisted.path("detector").asString())) {
            for (JsonNode citation : persisted.path("citations")) {
                if (citation instanceof ObjectNode object) {
                    object.remove("quoteSha256");
                }
            }
        }
        return persisted;
    }

    private static boolean diffContainsCitation(
        String diff,
        String citedPath,
        String citedSide,
        int citedStartLine,
        int citedEndLine,
        String quote
    ) {
        String oldPath = null;
        String newPath = null;
        Map<Integer, String> citedLines = new HashMap<>();
        for (String storedLine : diff.split("\n", -1)) {
            String line = storedLine;
            Integer annotatedLine = null;
            if (storedLine.startsWith("[L")) {
                int end = storedLine.indexOf("] ");
                if (end > 2) {
                    try {
                        annotatedLine = Integer.parseInt(storedLine.substring(2, end));
                        line = storedLine.substring(end + 2);
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                }
            }
            if (line.startsWith("--- ")) {
                oldPath = parseDiffPath(line.substring(4));
                continue;
            }
            if (line.startsWith("+++ ")) {
                newPath = parseDiffPath(line.substring(4));
                continue;
            }
            if (annotatedLine != null) {
                String lineSide = line.startsWith("-") ? "OLD" : "NEW";
                String linePath = "OLD".equals(lineSide) ? oldPath : newPath;
                if (citedSide.equals(lineSide) && citedPath.equals(linePath)) {
                    citedLines.put(annotatedLine, line);
                }
            }
        }
        List<String> quoteLines = quote.lines().toList();
        if (quoteLines.size() != citedEndLine - citedStartLine + 1) {
            return false;
        }
        for (int i = 0; i < quoteLines.size(); i++) {
            String diffLine = citedLines.get(citedStartLine + i);
            String quoteLine = quoteLines.get(i);
            if (diffLine == null || !(diffLine.equals(quoteLine) || diffLine.substring(1).equals(quoteLine))) {
                return false;
            }
        }
        return true;
    }

    private static String parseDiffPath(String value) {
        String path = value.trim();
        if ("/dev/null".equals(path)) {
            return null;
        }
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1).replace("\\\"", "\"");
        }
        return path.startsWith("a/") || path.startsWith("b/") ? path.substring(2) : path;
    }

    private EvidenceBoundary evidenceBoundary(AgentJob job) {
        JsonNode manifest = requireEvidenceSnapshot(job).path("manifest");
        SourceContractVersion contractVersion;
        try {
            contractVersion = new SourceContractVersion(manifest.path("contractVersion").asString());
        } catch (IllegalArgumentException e) {
            throw new JobDeliveryException(
                "Job evidence snapshot has an invalid contract version: jobId=" + job.getId(),
                e
            );
        }
        JsonNode sources = manifest.path("sources");
        if (!sources.isArray()) {
            throw new JobDeliveryException("Job evidence snapshot has no source manifest: jobId=" + job.getId());
        }
        Set<SourceKind> available = new HashSet<>();
        Map<String, SourceArtifactRef> artifacts = new HashMap<>();
        for (JsonNode source : sources) {
            if ("AVAILABLE".equals(source.path("state").path("availability").asString())) {
                SourceKind kind = new SourceKind(source.path("kind").asString());
                available.add(kind);
                JsonNode sourceArtifacts = source.path("artifacts");
                if (!sourceArtifacts.isArray()) {
                    throw new JobDeliveryException("Available source has no artifact inventory: jobId=" + job.getId());
                }
                for (JsonNode artifact : sourceArtifacts) {
                    String path = artifact.path("path").asString();
                    String sha256 = artifact.path("sha256").asString();
                    if (path.isBlank() || !sha256.matches("[0-9a-f]{64}")) {
                        throw new JobDeliveryException(
                            "Available source has an invalid artifact: jobId=" + job.getId()
                        );
                    }
                    if (artifacts.put(path, new SourceArtifactRef(kind, sha256)) != null) {
                        throw new JobDeliveryException("Evidence artifact belongs to multiple sources: path=" + path);
                    }
                }
            }
        }
        return new EvidenceBoundary(contractVersion, Set.copyOf(available), Map.copyOf(artifacts));
    }

    private Map<String, PracticeRevision> admittedRevisions(AgentJob job, Long workspaceId) {
        JsonNode practices = requireEvidenceSnapshot(job).path("practices");
        if (!practices.isArray() || practices.isEmpty()) {
            throw new JobDeliveryException("Job evidence snapshot has no admitted practices: jobId=" + job.getId());
        }
        Map<String, PracticeRevision> admitted = new HashMap<>();
        for (JsonNode entry : practices) {
            String slug = entry.path("slug").asString();
            JsonNode revisionId = entry.path("revisionId");
            if (slug.isBlank() || !revisionId.isIntegralNumber()) {
                throw new JobDeliveryException("Job evidence snapshot has an invalid practice: jobId=" + job.getId());
            }
            PracticeRevision revision = practiceRevisionRepository
                .findById(revisionId.asLong())
                .orElseThrow(() ->
                    new JobDeliveryException("Admitted practice revision no longer exists: jobId=" + job.getId())
                );
            Practice practice = revision.getPractice();
            if (!slug.equals(revision.getSlug()) || !workspaceId.equals(practice.getWorkspace().getId())) {
                throw new JobDeliveryException(
                    "Admitted practice revision does not match the job: jobId=" + job.getId()
                );
            }
            if (admitted.put(slug, revision) != null) {
                throw new JobDeliveryException("Duplicate admitted practice slug: " + slug + ", jobId=" + job.getId());
            }
        }
        return admitted;
    }

    private static JsonNode requireEvidenceSnapshot(AgentJob job) {
        JsonNode snapshot = job.getEvidenceSnapshot();
        if (snapshot == null || !snapshot.isObject()) {
            throw new JobDeliveryException("Job has no evidence snapshot: jobId=" + job.getId());
        }
        return snapshot;
    }

    private record SourceArtifactRef(SourceKind kind, String sha256) {}

    private record EvidenceBoundary(
        SourceContractVersion contractVersion,
        Set<SourceKind> allowedSources,
        Map<String, SourceArtifactRef> artifacts
    ) {}

    /**
     * Route the delivery target on the job's artifact.
     *
     * <p>The discriminator is taken from the job row's own {@code artifact_kind} column first and only
     * then from its metadata. The column is set by {@code AgentJobService} for every job from the job
     * type, so it is present and authoritative even for the pull-request jobs that omit the metadata key
     * by convention; the metadata copy is kept as the fallback because it is what jobs queued by older
     * builds carry.
     *
     * <p>A kind neither source recognises is refused rather than defaulted. Falling through to
     * pull-request handling turns "this build cannot deliver that kind" into "Missing pull_request_id in
     * job metadata", which sends whoever reads it looking for the wrong bug. The pull-request default
     * applies only where it is a fact: a job that names no kind at all.
     */
    private Target resolveTarget(AgentJob job, JsonNode metadata) {
        String artifactKind =
            job.getArtifactKind() != null
                ? job.getArtifactKind().value()
                : metadata.has("artifact_kind")
                    ? metadata.get("artifact_kind").asString()
                    : null;
        if (artifactKind == null) {
            // Predates the column and the metadata key alike, which means the event-driven PR path —
            // the only producer that existed then.
            artifactKind = ArtifactKinds.PULL_REQUEST.value();
        } else if (
            !ArtifactKinds.PULL_REQUEST.value().equals(artifactKind) &&
            !ArtifactKinds.ISSUE.value().equals(artifactKind) &&
            !ArtifactKinds.CONVERSATION_THREAD.value().equals(artifactKind) &&
            !ArtifactKinds.DOCUMENT.value().equals(artifactKind)
        ) {
            throw new JobDeliveryException(
                "No delivery route for artifact kind: kind=" + artifactKind + ", jobId=" + job.getId()
            );
        }
        if (ArtifactKinds.CONVERSATION_THREAD.value().equals(artifactKind)) {
            // Repo-less: the subject user is carried EXPLICITLY in metadata (about_user_id), not resolved
            // from an SCM artifact author. artifactId is the slack_thread aggregate id.
            JsonNode threadIdNode = metadata.get("slack_thread_id");
            if (threadIdNode == null || threadIdNode.isNull() || !threadIdNode.isNumber()) {
                throw new JobDeliveryException("Missing slack_thread_id in job metadata: jobId=" + job.getId());
            }
            JsonNode aboutUserNode = metadata.get("about_user_id");
            if (aboutUserNode == null || aboutUserNode.isNull() || !aboutUserNode.isNumber()) {
                throw new JobDeliveryException("Missing about_user_id in job metadata: jobId=" + job.getId());
            }
            String channelId = requiredMetadataText(metadata, "slack_channel_id", job);
            String threadTs = requiredMetadataText(metadata, "slack_thread_ts", job);
            long threadId = threadIdNode.asLong();
            long aboutUserId = aboutUserNode.asLong();
            if (
                !conversationSourceLiveness.isDeliverableThread(
                    job.getWorkspace().getId(),
                    threadId,
                    channelId,
                    threadTs,
                    aboutUserId
                )
            ) {
                throw new JobDeliveryException(
                    "Conversation target is no longer authorized or does not match the job: jobId=" + job.getId()
                );
            }
            return new Target(ArtifactKinds.CONVERSATION_THREAD, threadId, aboutUserId);
        }
        if (ArtifactKinds.DOCUMENT.value().equals(artifactKind)) {
            // Repo-less, like a conversation: the subject is carried explicitly (about_user_id), resolved
            // once at submission from the document's author. The document is re-read here only to confirm
            // it still exists — a document erased or tombstoned while its review ran must not have
            // observations filed against it, because nothing on any surface could then explain them.
            JsonNode documentIdNode = metadata.get(DocumentContentSource.DOCUMENT_ID_METADATA_KEY);
            if (documentIdNode == null || documentIdNode.isNull() || !documentIdNode.isNumber()) {
                throw new JobDeliveryException(
                    "Missing " +
                        DocumentContentSource.DOCUMENT_ID_METADATA_KEY +
                        " in job metadata: jobId=" +
                        job.getId()
                );
            }
            JsonNode aboutUserNode = metadata.get("about_user_id");
            if (aboutUserNode == null || aboutUserNode.isNull() || !aboutUserNode.isNumber()) {
                throw new JobDeliveryException("Missing about_user_id in job metadata: jobId=" + job.getId());
            }
            long documentId = documentIdNode.asLong();
            boolean live = documentProjection
                .documentById(job.getWorkspace().getId(), documentId)
                .filter(document -> !document.deleted())
                .isPresent();
            if (!live) {
                throw new JobDeliveryException(
                    "Document target is gone: documentId=" + documentId + ", jobId=" + job.getId()
                );
            }
            return new Target(ArtifactKinds.DOCUMENT, documentId, aboutUserNode.asLong());
        }
        if (ArtifactKinds.ISSUE.value().equals(artifactKind)) {
            JsonNode issueIdNode = metadata.get("issue_id");
            if (issueIdNode == null || issueIdNode.isNull() || !issueIdNode.isNumber()) {
                throw new JobDeliveryException("Missing issue_id in job metadata: jobId=" + job.getId());
            }
            Long issueId = issueIdNode.asLong();
            Issue issue = issueRepository
                .findByIdWithAuthorAndRepository(issueId)
                .orElseThrow(() ->
                    new JobDeliveryException("Issue not found: issueId=" + issueId + ", jobId=" + job.getId())
                );
            if (issue.getAuthor() == null) {
                throw new JobDeliveryException("Issue has no author: issueId=" + issueId + ", jobId=" + job.getId());
            }
            requireMatchingArtifact(issue, metadata, "issue_number", job);
            return new Target(ArtifactKinds.ISSUE, issueId, issue.getAuthor().getId());
        }
        JsonNode pullRequestIdNode = metadata.get("pull_request_id");
        if (pullRequestIdNode == null || pullRequestIdNode.isNull() || !pullRequestIdNode.isNumber()) {
            throw new JobDeliveryException("Missing pull_request_id in job metadata: jobId=" + job.getId());
        }
        Long pullRequestId = pullRequestIdNode.asLong();
        PullRequest pullRequest = pullRequestRepository
            .findByIdWithAuthorAndRepository(pullRequestId)
            .orElseThrow(() ->
                new JobDeliveryException(
                    "Pull request not found: pullRequestId=" + pullRequestId + ", jobId=" + job.getId()
                )
            );
        if (pullRequest.getAuthor() == null) {
            throw new JobDeliveryException(
                "Pull request has no author: pullRequestId=" + pullRequestId + ", jobId=" + job.getId()
            );
        }
        requireMatchingArtifact(pullRequest, metadata, "pr_number", job);
        return new Target(ArtifactKinds.PULL_REQUEST, pullRequestId, pullRequest.getAuthor().getId());
    }

    private static String requiredMetadataText(JsonNode metadata, String field, AgentJob job) {
        String value = metadata.path(field).asString();
        if (value.isBlank()) {
            throw new JobDeliveryException("Missing " + field + " in job metadata: jobId=" + job.getId());
        }
        return value;
    }

    private static void requireMatchingArtifact(Issue artifact, JsonNode metadata, String numberKey, AgentJob job) {
        if (!PracticeFeedbackDeliveryPolicy.matchesArtifact(artifact, metadata, numberKey)) {
            throw new JobDeliveryException("Artifact metadata does not match the live target: jobId=" + job.getId());
        }
    }

    static String firstLocationPath(JsonNode evidence) {
        if (evidence == null || evidence.isNull()) {
            return null;
        }
        JsonNode citations = evidence.get("citations");
        if (citations == null || !citations.isArray() || citations.isEmpty()) {
            return null;
        }
        JsonNode first = citations.get(0);
        if (first == null || !first.isObject()) {
            return null;
        }
        JsonNode path = first.get("path");
        return path != null && path.isString() ? path.asString() : null;
    }

    /**
     * @param observationKeys the keys persisted for each delivered finding, by finding identity, so the caller
     *     stamps the SAME keys onto its deliverable findings rather than recomputing them (no drift from what
     *     was persisted). Empty when no findings were persisted.
     */
    public record DeliveryResult(
        int inserted,
        int discardedDuplicate,
        boolean hasNegative,
        Map<ValidatedFinding, ObservationKeys> observationKeys
    ) {}
}
