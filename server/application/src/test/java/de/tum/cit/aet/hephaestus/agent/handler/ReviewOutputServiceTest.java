package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.CitedSourceAccess;
import de.tum.cit.aet.hephaestus.agent.context.HistoricalGitEvidence;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.ReviewedWork;
import de.tum.cit.aet.hephaestus.agent.context.providers.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationSourceLiveness;
import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedProblem;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedResult;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.integration.scm.ReviewTargetQuery;
import de.tum.cit.aet.hephaestus.practices.EvidenceStance;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDeliveryBehavior;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceLimitation;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceRequirement;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.ObservationFingerprint;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.AutomatedReviewFence;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkChanges;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ReviewOutputServiceTest extends BaseUnitTest {
    /** The pinned change: {@code BASE..HEAD}, in a checkout whose HEAD tree is {@code TREE}. */
    private static final String BASE = "a".repeat(40);

    private static final String HEAD = "b".repeat(40);
    private static final String TREE = "c".repeat(40);
    private static final String HEAD_DIGEST = "d".repeat(64);
    private static final String REFS_DIGEST = "f".repeat(64);
    private static final String BLOB_DIGEST = "e".repeat(64);
    private static final String HEAD_PATH = SandboxLayout.REPO_MOUNT_RELATIVE + ".git/HEAD";
    private static final String REFS_PATH = SandboxLayout.REPO_MOUNT_RELATIVE + ".git/hephaestus-captured-refs";
    private static final String DIFF = "scm.pull-request.diff";

    private static final String SUMMARY = "Test result";
    private static final String EXPLANATION = "The captured change decides this practice.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PracticeRevisionRepository practiceRevisionRepository;

    @Mock
    private PracticeResultRepository practiceResultRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private ReviewTargetQuery reviewTargets;

    @Mock
    private ConversationSourceLiveness conversationSourceLiveness;

    @Mock
    private DocumentProjection documentProjection;

    @Mock
    private JobEvidenceFiles cas;

    @Mock
    private ArtifactSourceCatalogRegistry sourceCatalogs;

    @Mock
    private HistoricalGitEvidence historicalGit;

    @Mock
    private CitedSourceAccess citedSourceAccess;

    private ReviewOutputService service;

    /** A regular file the attempt's checkout holds at one revision. */
    private record Blob(String revision, String path) {}

    /** What the checkout can be quoted from: the numbered lines of each blob, as the verifier reads them. */
    private final Map<Blob, Map<Integer, String>> checkout = new HashMap<>();

    /** The paths the pinned change touches, as the checkout's own history reports them. */
    private Set<String> changedPaths = Set.of("src/Auth.java");

    private Practice testPractice;
    private PracticeRevision testRevision;
    private AgentJob testJob;

    @BeforeEach
    void setUp() {
        // The developer's AI choice permits the result unless a scenario withdraws it.
        lenient().when(citedSourceAccess.permitsReviewResult(any())).thenReturn(true);
        service = new ReviewOutputService(
                practiceRevisionRepository,
                practiceResultRepository,
                observationRepository,
                new ReviewResultTargetResolver(reviewTargets, conversationSourceLiveness, documentProjection),
                objectMapper,
                new CandidateEvidenceVerifier(objectMapper, cas, historicalGit, citedSourceAccess),
                sourceCatalogs,
                new AutomatedReviewFence(Map.of()),
                new LinkedIssueRepairAdmissionService(
                        mock(ReviewedWorkChanges.class),
                        practiceResultRepository,
                        mock(FeedbackEvidenceRepository.class),
                        mock(FeedbackRepository.class)),
                mock(PracticeSignalOptions.class),
                citedSourceAccess);

        lenient().when(sourceCatalogs.isSourceUsePermitted(any(), any(), any())).thenReturn(true);

        Workspace workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", 1L);

        testPractice = new Practice();
        ReflectionTestUtils.setField(testPractice, "id", 10L);
        testPractice.setSlug("pr-description-quality");
        PracticeTestEvidence.configure(testPractice, ArtifactKinds.PULL_REQUEST);
        testPractice.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        testPractice.setWorkspace(workspace);

        testJob = new AgentJob();
        ReflectionTestUtils.setField(testJob, "id", UUID.randomUUID());
        testJob.setWorkspace(workspace);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("pull_request_id", 456L);
        metadata.put("repository_id", 123L);
        metadata.put("repository_full_name", "owner/repo");
        metadata.put("pr_number", 42);
        testJob.setMetadata(metadata);
        ObjectNode snapshot = EvidenceSnapshotFixtures.snapshot(objectMapper);
        EvidenceSnapshotFixtures.artifact(
                snapshot,
                EvidenceSnapshotFixtures.availableSource(snapshot, DIFF, BASE + ":" + HEAD),
                PullRequestContentSource.CHANGE_FILE,
                "a".repeat(64));
        ObjectNode tree = EvidenceSnapshotFixtures.availableSource(snapshot, "scm.repository.tree", HEAD + ":" + TREE);
        EvidenceSnapshotFixtures.artifact(snapshot, tree, HEAD_PATH, HEAD_DIGEST);
        EvidenceSnapshotFixtures.artifact(snapshot, tree, REFS_PATH, REFS_DIGEST);
        EvidenceSnapshotFixtures.artifact(
                snapshot,
                EvidenceSnapshotFixtures.availableSource(snapshot, "scm.pull-request.core", null),
                "context/pull_request.json",
                "b".repeat(64));
        EvidenceSnapshotFixtures.admittedPractice(snapshot, "pr-description-quality", 11L);
        testJob.setEvidenceSnapshot(snapshot);

        testRevision = mock(PracticeRevision.class);
        lenient().when(testRevision.getId()).thenReturn(11L);
        lenient().when(testRevision.getReviewRuleFingerprint()).thenReturn("v5:" + "a".repeat(64));
        lenient().when(testRevision.getSlug()).thenReturn("pr-description-quality");
        lenient().when(testRevision.getPractice()).thenReturn(testPractice);
        lenient().when(testRevision.getAutomatedReviewPolicy()).thenReturn(testPractice.getAutomatedReviewPolicy());
        // Evidence requirements bound absence claims; every source that applies to the
        // artifact is staged for citation regardless.
        lenient().when(testRevision.getSignals()).thenReturn(testPractice.getSignals());
        lenient().when(testRevision.getEvidenceRequirements()).thenReturn(testPractice.getEvidenceRequirements());
        lenient().when(testRevision.getReviewWhen()).thenReturn(testPractice.getReviewWhen());
        lenient().when(testRevision.getSubject()).thenReturn(testPractice.getSubject());
        lenient().when(testRevision.getPrecondition()).thenReturn(testPractice.getPrecondition());
        lenient().when(testRevision.getDeliveryBehavior()).thenReturn(PracticeDeliveryBehavior.DEFAULT);
        lenient()
                .when(practiceRevisionRepository.findByIdAndWorkspaceId(11L, 1L))
                .thenReturn(Optional.of(testRevision));

        captured(HEAD, "src/Auth.java", 10, "insecure();", "allowAll();");
        lenient()
                .when(historicalGit.changedPaths(testJob, HEAD_DIGEST, REFS_DIGEST, BASE, HEAD))
                .thenAnswer(invocation -> changedPaths);
        lenient()
                .when(historicalGit.verifyAll(eq(testJob), eq(HEAD_DIGEST), eq(REFS_DIGEST), eq(HEAD), any()))
                .thenAnswer(invocation -> {
                    List<HistoricalGitEvidence.Citation> submitted = invocation.getArgument(4);
                    Map<HistoricalGitEvidence.Citation, JobEvidenceFiles.QuoteMatch> verified = new LinkedHashMap<>();
                    for (HistoricalGitEvidence.Citation citation : submitted) {
                        verified.put(citation, quoteMatch(citation));
                    }
                    return verified;
                });

        lenient()
                .when(reviewTargets.findPullRequest(456L))
                .thenReturn(Optional.of(new ReviewTargetQuery.Target(123L, "owner/repo", 42, 789L, false)));
        insertedRows(1);
    }

    /** How many rows the repository call that records one result reports, for any arguments. */
    private void insertedRows(int rows) {
        lenient()
                .when(practiceResultRepository.insertIfAbsent(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                        any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(rows);
    }

    /** Puts {@code lines} into the checkout at {@code path} in {@code revision}, the first at {@code firstLine}. */
    private void captured(String revision, String path, int firstLine, String... lines) {
        Map<Integer, String> numbered = checkout.computeIfAbsent(new Blob(revision, path), blob -> new HashMap<>());
        for (int i = 0; i < lines.length; i++) {
            numbered.put(firstLine + i, lines[i]);
        }
    }

    /** What the verifier answers for one citation: the blob is absent, or its lines do or do not read as quoted. */
    private JobEvidenceFiles.QuoteMatch quoteMatch(HistoricalGitEvidence.Citation citation) {
        Map<Integer, String> lines = checkout.get(new Blob(citation.revision(), citation.path()));
        if (lines == null) return JobEvidenceFiles.QuoteMatch.absent();
        StringBuilder text = new StringBuilder();
        for (int line = citation.startLine(); line <= citation.endLine(); line++) {
            String content = lines.get(line);
            if (content == null) return new JobEvidenceFiles.QuoteMatch(false, BLOB_DIGEST);
            if (line > citation.startLine()) text.append('\n');
            text.append(content);
        }
        return new JobEvidenceFiles.QuoteMatch(text.toString().equals(citation.quote()), BLOB_DIGEST);
    }

    // --- Candidates as the runner submits them -------------------------------------------------------------------

    /** A quote of the change at {@code src/Auth.java}. */
    private ObjectNode change(String side, int startLine, int endLine, String quote) {
        return objectMapper
                .createObjectNode()
                .put("sourceKind", DIFF)
                .put("artifactPath", PullRequestContentSource.CHANGE_FILE)
                .put("path", "src/Auth.java")
                .put("side", side)
                .put("startLine", startLine)
                .put("endLine", endLine)
                .put("quote", quote);
    }

    /** Line 10 of the head, which the change added. */
    private ObjectNode insecure() {
        return change("NEW", 10, 10, "insecure();");
    }

    /** A quote of a serialized record, whose path is its label. */
    private ObjectNode record(String sourceKind, String artifactPath, String path, int line, String quote) {
        return objectMapper
                .createObjectNode()
                .put("sourceKind", sourceKind)
                .put("artifactPath", artifactPath)
                .put("path", path)
                .put("startLine", line)
                .put("endLine", line)
                .put("quote", quote);
    }

    /** Coverage the host derived from its read receipts: a session-shaped candidate. */
    private ObjectNode hostCoverage() {
        return objectMapper
                .createObjectNode()
                .put("source", "host")
                .put("reads", 1)
                .put("truncatedReads", 0)
                .put("failedReads", 0)
                .put("complete", true);
    }

    /** Coverage typed by program code: a code-shaped candidate. */
    private ObjectNode claimedCoverage() {
        return objectMapper
                .createObjectNode()
                .put("source", "claimed")
                .put("selected", 1)
                .put("checked", 1)
                .put("total", 1)
                .put("complete", true);
    }

    private ObjectNode search(String... consulted) {
        ObjectNode search = objectMapper.createObjectNode();
        ArrayNode sources = search.putArray("consulted");
        Arrays.stream(consulted).forEach(sources::add);
        search.put("lookedFor", "a described rationale for the change");
        search.put("boundary", "the diff of this pull request only");
        return search;
    }

    private ObjectNode inapplicability(String... consulted) {
        ObjectNode inapplicability = objectMapper.createObjectNode();
        ArrayNode sources = inapplicability.putArray("consulted");
        Arrays.stream(consulted).forEach(sources::add);
        inapplicability.put("subject", "a described rationale for the change");
        inapplicability.put("ruledOutBy", "the change is a generated lockfile update with no prose to judge");
        return inapplicability;
    }

    private ObjectNode openQuestion() {
        return objectMapper
                .createObjectNode()
                .put("openQuestion", "Does the criterion include this compatibility-only change?")
                .put("wouldSettleIt", "Clarification of the practice's scope.");
    }

    /** One warrant of the given evidence key, well formed. */
    private JsonNode warrant(String key) {
        return switch (key) {
            case "search" -> search(DIFF);
            case "inapplicability" -> inapplicability(DIFF);
            case "undecidability" -> objectMapper.createArrayNode().add(openQuestion());
            default -> throw new IllegalArgumentException(key);
        };
    }

    private ObjectNode candidate(Outcome outcome, ObjectNode... citations) {
        ObjectNode candidate = objectMapper.createObjectNode();
        candidate.put("outcome", outcome.name());
        candidate.put("summary", SUMMARY);
        candidate.put("explanation", EXPLANATION);
        ArrayNode cited = candidate.putArray("citations");
        Arrays.stream(citations).forEach(cited::add);
        candidate.putObject("evidence");
        candidate.set("coverage", hostCoverage());
        return candidate;
    }

    private ObjectNode met() {
        return met(insecure());
    }

    private ObjectNode met(ObjectNode citation) {
        return candidate(Outcome.MET, citation);
    }

    private ObjectNode notApplicable(ObjectNode citation) {
        ObjectNode candidate = candidate(Outcome.NOT_APPLICABLE, citation);
        evidence(candidate).set("inapplicability", inapplicability(DIFF));
        return candidate;
    }

    private ObjectNode undetermined(ObjectNode citation) {
        ObjectNode candidate = candidate(Outcome.UNDETERMINED, citation);
        evidence(candidate).putArray("undecidability").add(openQuestion());
        return candidate;
    }

    /** A NOT_MET whose problems carry every citation, with a recorded search of the change. */
    private ObjectNode notMet(ObjectNode... problems) {
        ObjectNode candidate = candidate(Outcome.NOT_MET);
        ArrayNode listed = candidate.putArray("problems");
        Arrays.stream(problems).forEach(listed::add);
        evidence(candidate).set("search", search(DIFF));
        return candidate;
    }

    private ObjectNode notMet() {
        return notMet(problem(Severity.MINOR, insecure()));
    }

    /** One problem that the change introduced at its first citation. */
    private ObjectNode problem(Severity severity, ObjectNode... citations) {
        ObjectNode problem = objectMapper.createObjectNode();
        problem.put("severity", severity.name());
        problem.put("summary", "Authorization check removed");
        problem.put("explanation", "The change replaces the admin check with a call that allows every caller.");
        ArrayNode cited = problem.putArray("citations");
        Arrays.stream(citations).forEach(cited::add);
        problem.putObject("basis")
                .put("type", "introduced")
                .put("head", 0)
                .put("explanation", "The pull request adds this line on the new side of the change.");
        return problem;
    }

    /** A NOT_MET, MET, NOT_APPLICABLE or UNDETERMINED candidate that passes every check. */
    private ObjectNode valid(Outcome outcome) {
        return switch (outcome) {
            case MET -> met();
            case NOT_MET -> notMet();
            case NOT_APPLICABLE -> notApplicable(insecure());
            case UNDETERMINED -> undetermined(insecure());
        };
    }

    private static ObjectNode evidence(ObjectNode candidate) {
        return candidate.withObject("evidence");
    }

    private static ObjectNode firstCitation(ObjectNode candidate) {
        return (ObjectNode) candidate.withArray("citations").get(0);
    }

    private ObjectNode entry(String slug, JsonNode candidate) {
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("practiceSlug", slug);
        entry.set("candidate", candidate);
        return entry;
    }

    /** The admission body's {@code results}. */
    private ArrayNode results(ObjectNode... entries) {
        ArrayNode results = objectMapper.createArrayNode();
        Arrays.stream(entries).forEach(results::add);
        return results;
    }

    private ArrayNode submit(String slug, JsonNode candidate) {
        return results(entry(slug, candidate));
    }

    private ReviewOutputService.RecordedResults publishVerified(AgentJob job, JsonNode results) {
        return service.publish(job, service.prepare(job, results));
    }

    private JsonNode fixture(String name) throws IOException {
        try (InputStream input = new ClassPathResource("practice-results/" + name).getInputStream()) {
            return objectMapper.readTree(input);
        }
    }

    // --- What admission asked the repositories to write ----------------------------------------------------------

    /** One {@code PracticeResultRepository.insertIfAbsent} call, by its arguments. */
    private record StoredResult(
            UUID id,
            String occurrenceKey,
            Long practiceId,
            Long revisionId,
            String artifactKind,
            Long artifactId,
            Long aboutUserId,
            String summary,
            String outcome,
            @Nullable String severity,
            JsonNode evidence,
            String rationale,
            String scopeKey,
            @Nullable JsonNode example,
            JsonNode coverage,
            JsonNode provenance,
            String origin) {}

    /** One {@code ObservationRepository.insert} call, by its arguments. */
    private record StoredObservation(
            UUID id,
            Long workspaceId,
            UUID practiceResultId,
            int position,
            @Nullable String key,
            String severity,
            String summary,
            String explanation,
            JsonNode citations,
            @Nullable JsonNode basis,
            @Nullable String nextStep,
            @Nullable String recurrenceKey) {}

    private @Nullable JsonNode json(@Nullable String text) {
        return text == null ? null : objectMapper.readTree(text);
    }

    private List<StoredResult> storedResults() {
        return mockingDetails(practiceResultRepository).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("insertIfAbsent"))
                .map(call -> new StoredResult(
                        call.getArgument(0, UUID.class),
                        call.getArgument(1, String.class),
                        call.getArgument(4, Long.class),
                        call.getArgument(5, Long.class),
                        call.getArgument(6, String.class),
                        call.getArgument(7, Long.class),
                        call.getArgument(8, Long.class),
                        call.getArgument(9, String.class),
                        call.getArgument(10, String.class),
                        call.getArgument(11, String.class),
                        objectMapper.readTree(call.getArgument(12, String.class)),
                        call.getArgument(13, String.class),
                        call.getArgument(14, String.class),
                        json(call.getArgument(15, String.class)),
                        objectMapper.readTree(call.getArgument(16, String.class)),
                        objectMapper.readTree(call.getArgument(17, String.class)),
                        call.getArgument(19, String.class)))
                .toList();
    }

    private List<StoredObservation> storedObservations() {
        return mockingDetails(observationRepository).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("insert"))
                .map(call -> new StoredObservation(
                        call.getArgument(0, UUID.class),
                        call.getArgument(1, Long.class),
                        call.getArgument(2, UUID.class),
                        call.getArgument(3, Integer.class),
                        call.getArgument(4, String.class),
                        call.getArgument(5, String.class),
                        call.getArgument(6, String.class),
                        call.getArgument(7, String.class),
                        objectMapper.readTree(call.getArgument(8, String.class)),
                        json(call.getArgument(9, String.class)),
                        call.getArgument(10, String.class),
                        call.getArgument(11, String.class)))
                .toList();
    }

    /** The persisted first citation of the one result {@code result} recorded. */
    private static JsonNode storedCitation(ReviewOutputService.RecordedResults result) {
        return Objects.requireNonNull(result.recorded().getFirst().evidence())
                .path("citations")
                .get(0);
    }

    private void admit(Practice practice, long revisionId) {
        practice.setWorkspace(testPractice.getWorkspace());
        ((ObjectNode) Objects.requireNonNull(testJob.getEvidenceSnapshot()))
                .withArray("practices")
                .addObject()
                .put("slug", practice.getSlug())
                .put("revisionId", revisionId);
        PracticeRevision revision = mock(PracticeRevision.class);
        lenient().when(revision.getId()).thenReturn(revisionId);
        lenient().when(revision.getReviewRuleFingerprint()).thenReturn("v5:" + "a".repeat(64));
        lenient().when(revision.getSlug()).thenReturn(practice.getSlug());
        lenient().when(revision.getPractice()).thenReturn(practice);
        lenient().when(revision.getAutomatedReviewPolicy()).thenReturn(practice.getAutomatedReviewPolicy());
        lenient().when(revision.getSignals()).thenReturn(practice.getSignals());
        lenient().when(revision.getEvidenceRequirements()).thenReturn(practice.getEvidenceRequirements());
        lenient().when(revision.getReviewWhen()).thenReturn(practice.getReviewWhen());
        lenient().when(revision.getSubject()).thenReturn(practice.getSubject());
        lenient().when(revision.getPrecondition()).thenReturn(practice.getPrecondition());
        lenient().when(revision.getDeliveryBehavior()).thenReturn(PracticeDeliveryBehavior.DEFAULT);
        lenient()
                .when(practiceRevisionRepository.findByIdAndWorkspaceId(revisionId, 1L))
                .thenReturn(Optional.of(revision));
    }

    /** A second admitted practice, so one result can be withheld while another is delivered. */
    private Practice admitSecondPractice() {
        Practice second = new Practice();
        ReflectionTestUtils.setField(second, "id", 20L);
        second.setSlug("pr-scope");
        PracticeTestEvidence.configure(second, ArtifactKinds.PULL_REQUEST);
        second.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        admit(second, 21L);
        return second;
    }

    /** The withheld entry of one practice in a prepared admission. */
    private static JsonNode withheld(ReviewOutputService.PreparedResults prepared, String slug) {
        for (JsonNode entry : prepared.withheld()) {
            if (slug.equals(entry.path("practiceSlug").asString())) return entry;
        }
        throw new AssertionError("Not withheld: " + slug);
    }

    @Test
    void shouldRefuseAPendingReviewWhenItIsPinnedToAnEarlierReviewStandard() {
        when(testRevision.getReviewRuleFingerprint()).thenReturn("v4:" + "a".repeat(64));

        assertThatThrownBy(() -> service.prepare(testJob, submit("pr-description-quality", met())))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("incompatible review standard");
        verifyNoInteractions(practiceResultRepository);
    }

    @ParameterizedTest
    @CsvSource({
        "MET,inapplicability",
        "MET,undecidability",
        "NOT_MET,inapplicability",
        "NOT_APPLICABLE,search",
        "NOT_APPLICABLE,undecidability",
        "UNDETERMINED,inapplicability"
    })
    void shouldRefuseTheSubmissionWhenAWarrantContradictsTheOutcome(Outcome outcome, String warrant) {
        ObjectNode candidate = valid(outcome);
        evidence(candidate).set(warrant, warrant(warrant));

        assertThatThrownBy(() -> service.prepare(testJob, submit("pr-description-quality", candidate)))
                .isInstanceOfSatisfying(
                        ResultsRefusedException.class,
                        refused -> assertThat(refused.reasonCode()).isEqualTo("warrant_outcome_mismatch"));
        verifyNoInteractions(practiceResultRepository);
    }

    @Test
    void shouldRefuseAnEmptyQuoteWhenTheCandidateIsRead() {
        ObjectNode candidate = met();
        firstCitation(candidate).put("quote", "");

        assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                .isInstanceOfSatisfying(
                        ResultsRefusedException.class,
                        refused -> assertThat(refused.reasonCode()).isEqualTo("malformed_candidate"));
        verifyNoInteractions(practiceResultRepository);
    }

    @Test
    void shouldWithholdAWhitespaceQuoteWhenItIsNotTheTextAtItsLines() {
        ObjectNode candidate = met();
        firstCitation(candidate).put("quote", " \t\n");

        assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                .isInstanceOfSatisfying(
                        ResultsRefusedException.class,
                        refused -> assertThat(refused.reasonCode()).isEqualTo("no_admissible_results"));
        verifyNoInteractions(practiceResultRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {2147483648L, 4294967306L, Long.MAX_VALUE})
    void shouldRefuseCoordinatesWhenTheyWouldOverflowAnInteger(long line) {
        ObjectNode startBeyond = met();
        firstCitation(startBeyond).put("startLine", line).put("endLine", line);
        ObjectNode endBeyond = met();
        firstCitation(endBeyond).put("endLine", line);

        for (ObjectNode candidate : List.of(startBeyond, endBeyond)) {
            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("malformed_candidate"));
        }
        verifyNoInteractions(practiceResultRepository);
    }

    @Test
    void shouldAdmitAContextualAssessmentWithoutReadingTheCriteriaWhenNoProblemRestsOnADuty() {
        publishVerified(testJob, submit("pr-description-quality", met()));

        verify(testRevision, never()).getCriteria();
        assertThat(storedResults())
                .singleElement()
                .satisfies(row -> assertThat(row.outcome()).isEqualTo("MET"));
    }

    @Test
    void shouldRequireAnOpenQuestionWhenTheResultIsUndetermined() {
        ObjectNode withoutQuestion = candidate(Outcome.UNDETERMINED, insecure());

        assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", withoutQuestion)))
                .isInstanceOfSatisfying(
                        ResultsRefusedException.class,
                        refused -> assertThat(refused.reasonCode()).isEqualTo("warrant_outcome_mismatch"));
        assertThat(publishVerified(testJob, submit("pr-description-quality", undetermined(insecure())))
                        .inserted())
                .isEqualTo(1);
    }

    @Test
    void shouldRefuseTheWholeSubmissionWhenOneCandidateBreaksTheContract() throws IOException {
        admitSecondPractice();
        JsonNode refusals = fixture("invalid-refusals.json");
        assertThat(refusals.size()).isPositive();

        for (var expected : refusals.properties()) {
            String file = expected.getKey();
            String code = expected.getValue().asString();
            ArrayNode submission = results(entry("pr-description-quality", fixture(file)), entry("pr-scope", met()));

            assertThatThrownBy(() -> service.prepare(testJob, submission))
                    .as(file)
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo(code));
        }
        verifyNoInteractions(practiceResultRepository);
    }

    @Test
    void shouldRejectPreparedResultsWhenSourceAuthorizationChangesBeforePublication() {
        var prepared = service.prepare(testJob, submit("pr-description-quality", met()));
        when(sourceCatalogs.isSourceUsePermitted(any(), any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.publish(testJob, prepared)).isInstanceOf(JobDeliveryException.class);
        verifyNoInteractions(practiceResultRepository);
    }

    @Test
    void shouldRefusePublicationWhenTheJobChangedSincePreparation() {
        var prepared = service.prepare(testJob, submit("pr-description-quality", met()));
        testJob.setRetryCount(1);
        assertThatThrownBy(() -> service.publish(testJob, prepared))
                .isInstanceOf(ResultAdmissionService.StaleAttemptException.class);
        verifyNoInteractions(practiceResultRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldAdmitRepositoryTextAgainstTheCapturedRepositoryIdentity(boolean historical) {
        String revision = historical ? BASE : HEAD;
        captured(revision, "deleted.java", 3, "old source");
        ObjectNode citation = record("scm.repository.tree", HEAD_PATH, "deleted.java", 3, "old source");
        if (historical) citation.put("revision", revision);
        var requested = new HistoricalGitEvidence.Citation(revision, "deleted.java", "old source", 3, 3);

        var result = publishVerified(testJob, submit("pr-description-quality", met(citation)));

        JsonNode stored = storedCitation(result);
        assertThat(stored.path("verification").path("artifactSha256").asString())
                .isEqualTo(BLOB_DIGEST);
        assertThat(stored.path("revision").asString()).isEqualTo(revision);
        verify(historicalGit).verifyAll(testJob, HEAD_DIGEST, REFS_DIGEST, HEAD, List.of(requested));
        verify(historicalGit, never()).changedPaths(any(), any(), any(), any(), any());
    }

    @Test
    void shouldWithholdOnlyThePracticeWhosePathIsAbsentAtItsRevision() {
        captured(HEAD, "kept.java", 3, "old source");
        admitSecondPractice();
        var present = met(record("scm.repository.tree", HEAD_PATH, "kept.java", 3, "old source"));
        var missing = met(record("scm.repository.tree", HEAD_PATH, "gone.java", 3, "old source"));

        var result =
                publishVerified(testJob, results(entry("pr-description-quality", present), entry("pr-scope", missing)));

        assertThat(result.recorded())
                .extracting(ValidatedResult::practiceSlug)
                .containsExactly("pr-description-quality");
    }

    /**
     * A quote from the change is a quote from one of its two revisions at a path the change touches:
     * the side names the revision, the checkout's own history names the paths, and the verifier reads
     * the blob the same way it reads any other repository citation.
     */
    @Nested
    class ChangeCitations {

        @Test
        @DisplayName("the NEW side is verified at the head of the pinned range, which the citation then records")
        void verifiesTheNewSideAtTheHead() {
            var result = publishVerified(testJob, submit("pr-description-quality", met()));

            JsonNode stored = storedCitation(result);
            assertThat(stored.path("revision").asString()).isEqualTo(HEAD);
            assertThat(stored.path("side").asString()).isEqualTo("NEW");
            assertThat(stored.path("verification").path("artifactSha256").asString())
                    .isEqualTo(BLOB_DIGEST);
            assertThat(stored.path("verification").path("quoteSha256").asString())
                    .isEqualTo(CitationVerification.quoteDigest("insecure();"));
            verify(historicalGit).changedPaths(testJob, HEAD_DIGEST, REFS_DIGEST, BASE, HEAD);
            verify(historicalGit)
                    .verifyAll(
                            testJob,
                            HEAD_DIGEST,
                            REFS_DIGEST,
                            HEAD,
                            List.of(new HistoricalGitEvidence.Citation(HEAD, "src/Auth.java", "insecure();", 10, 10)));
        }

        @Test
        @DisplayName("the OLD side is verified at the base of the pinned range")
        void verifiesTheOldSideAtTheBase() {
            captured(BASE, "src/Auth.java", 8, "requireAdmin();");

            var result = publishVerified(
                    testJob, submit("pr-description-quality", met(change("OLD", 8, 8, "requireAdmin();"))));

            assertThat(storedCitation(result).path("revision").asString()).isEqualTo(BASE);
            verify(historicalGit)
                    .verifyAll(
                            testJob,
                            HEAD_DIGEST,
                            REFS_DIGEST,
                            HEAD,
                            List.of(new HistoricalGitEvidence.Citation(
                                    BASE, "src/Auth.java", "requireAdmin();", 8, 8)));
        }

        @Test
        @DisplayName("a quote spanning two lines of the change verifies as one")
        void verifiesAQuoteSpanningTwoLines() {
            var candidate = met(change("NEW", 10, 11, "insecure();\nallowAll();"));

            assertThat(publishVerified(testJob, submit("pr-description-quality", candidate))
                            .inserted())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("a path the change does not touch is withheld as unverified, not fatal")
        void withholdsAPathTheChangeDoesNotTouch() {
            captured(HEAD, "src/Other.java", 10, "insecure();");
            admitSecondPractice();
            var outsideChange = met();
            firstCitation(outsideChange).put("path", "src/Other.java");

            var result = publishVerified(
                    testJob, results(entry("pr-description-quality", met()), entry("pr-scope", outsideChange)));

            assertThat(result.recorded())
                    .extracting(ValidatedResult::practiceSlug)
                    .containsExactly("pr-description-quality");
            JsonNode failures = Objects.requireNonNull(testJob.getMetadata()).path("citation_verification_failures");
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0).path("practiceSlug").asString()).isEqualTo("pr-scope");
            assertThat(failures.get(0).path("citation").asString()).isEqualTo("citations[0]");
            assertThat(failures.get(0).path("reasonCode").asString()).isEqualTo("QUOTE_LOCATION_MISMATCH");
        }

        @Test
        @DisplayName("a quote that is not at the cited lines of the cited revision is withheld")
        void withholdsAQuoteThatDoesNotMatchTheRevision() {
            var wrongLine = met(change("NEW", 11, 11, "insecure();"));
            var wrongText = met(change("NEW", 10, 10, "fabricated quote"));
            var beyondTheFile = met(change("NEW", 10, 12, "insecure();\nallowAll();\n"));

            for (ObjectNode candidate : List.of(wrongLine, wrongText, beyondTheFile)) {
                assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                        .isInstanceOfSatisfying(ResultsRefusedException.class, refused -> {
                            assertThat(refused.reasonCode()).isEqualTo("no_admissible_results");
                            assertThat(refused.verificationFailures()
                                            .get(0)
                                            .path("reasonCode")
                                            .asString())
                                    .isEqualTo("QUOTE_LOCATION_MISMATCH");
                        });
            }
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("a two-line quote whose second line is altered is withheld")
        void withholdsATwoLineQuoteWhoseSecondLineIsAltered() {
            var candidate = met(change("NEW", 10, 11, "insecure();\nallowSome();"));

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("no_admissible_results"));
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("a renamed file is quoted under its new name at the head")
        void verifiesTheNewPathOfARenamedFile() {
            changedPaths = Set.of("src/Old.java", "src/New.java");
            captured(HEAD, "src/New.java", 10, "insecure();");
            var candidate = met();
            firstCitation(candidate).put("path", "src/New.java");

            assertThat(publishVerified(testJob, submit("pr-description-quality", candidate))
                            .inserted())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName(
                "the old name of a renamed file no longer exists at the head, so a NEW-side quote of it is withheld")
        void withholdsTheOldPathOfARenamedFileOnTheNewSide() {
            changedPaths = Set.of("src/Old.java", "src/New.java");
            captured(HEAD, "src/New.java", 10, "insecure();");
            var candidate = met();
            firstCitation(candidate).put("path", "src/Old.java");

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(ResultsRefusedException.class, refused -> {
                        assertThat(refused.reasonCode()).isEqualTo("no_admissible_results");
                        assertThat(refused.verificationFailures()
                                        .get(0)
                                        .path("reasonCode")
                                        .asString())
                                .isEqualTo("QUOTE_LOCATION_MISMATCH");
                    });
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        void shouldAdmitAChangeCitationWhenItNamesTheRevisionOfItsSide() {
            var candidate = met();
            firstCitation(candidate).put("revision", HEAD);

            assertThat(publishVerified(testJob, submit("pr-description-quality", candidate))
                            .inserted())
                    .isEqualTo(1);
        }

        @Test
        void shouldWithholdAChangeCitationWhenItNamesAnotherRevisionThanItsSide() {
            var candidate = met();
            firstCitation(candidate).put("revision", BASE);

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(ResultsRefusedException.class, refused -> {
                        assertThat(refused.reasonCode()).isEqualTo("no_admissible_results");
                        assertThat(refused.verificationFailures()
                                        .get(0)
                                        .path("reasonCode")
                                        .asString())
                                .isEqualTo("QUOTE_LOCATION_MISMATCH");
                    });
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        void shouldRefuseAChangeCitationWhenItNamesNoSide() {
            var candidate = met();
            firstCitation(candidate).remove("side");

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("malformed_candidate"));
            verifyNoInteractions(practiceResultRepository, historicalGit);
        }

        @Test
        @DisplayName("a change citation names the pinned change, not some other artifact")
        void rejectsAChangeCitationOfAnotherArtifact() {
            var candidate = met();
            firstCitation(candidate).put("artifactPath", "context/pull_request.json");

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("unauthorized_source"))
                    .hasMessageContaining("code citation");
            verifyNoInteractions(practiceResultRepository, historicalGit);
        }

        @Test
        @DisplayName("a change can only be quoted from a checkout the run captured")
        void rejectsAChangeCitationWithoutACapturedCheckout() {
            ObjectNode snapshot = (ObjectNode) Objects.requireNonNull(testJob.getEvidenceSnapshot());
            EvidenceSnapshotFixtures.unavailable(snapshot, (ObjectNode)
                    snapshot.withObject("manifest").withArray("sources").get(1));

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("unauthorized_source"))
                    .hasMessageContaining("scm.repository.tree");
            verifyNoInteractions(practiceResultRepository, historicalGit);
        }
    }

    @Test
    void shouldNotAdmitResultsAfterDeveloperChoosesNoAi() {
        when(citedSourceAccess.permitsReviewResult(testJob)).thenReturn(false);
        assertThatThrownBy(() -> service.prepare(testJob, submit("pr-description-quality", met())))
                .isInstanceOfSatisfying(
                        ResultsRefusedException.class,
                        refused -> assertThat(refused.reasonCode()).isEqualTo("member_ai_declined"))
                .hasMessageContaining("AI choice");
        verifyNoInteractions(practiceResultRepository);
    }

    @Nested
    class EvidenceBoundary {

        @Test
        void rejectsAPositiveWithoutACitation() {
            ObjectNode candidate = candidate(Outcome.MET);

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("result_citations_required"));
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName(
                "a side selects a revision of the change, and a revision a commit of the checkout; no record has one")
        void rejectsARecordCitationWithASideOrARevision() {
            ObjectNode withSide = met(
                    record("scm.pull-request.core", "context/pull_request.json", "pull_request.json", 1, "\"title\""));
            firstCitation(withSide).put("side", "NEW");
            ObjectNode withRevision = met(
                    record("scm.pull-request.core", "context/pull_request.json", "pull_request.json", 1, "\"title\""));
            firstCitation(withRevision).put("revision", HEAD);

            for (ObjectNode candidate : List.of(withSide, withRevision)) {
                assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                        .isInstanceOfSatisfying(
                                ResultsRefusedException.class,
                                refused -> assertThat(refused.reasonCode()).isEqualTo("malformed_candidate"));
            }
            verifyNoInteractions(practiceResultRepository, historicalGit);
        }

        @Test
        @DisplayName("a quote is always required; there is no digest-only citation")
        void rejectsACitationWithoutAQuote() {
            ObjectNode candidate = met();
            firstCitation(candidate).remove("quote");
            firstCitation(candidate)
                    .put("quoteSha256", "cc8c484344d4e1f933f0020a76f6dc3f6fa1462dfc7c1ac7b42cae840404141f");

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("malformed_candidate"));
            verifyNoInteractions(practiceResultRepository, historicalGit);
        }

        @Test
        @DisplayName("a citation to a source this run did not stage is refused")
        void rejectsSourcesTheRunNeverStaged() {
            ObjectNode candidate =
                    met(record("scm.pull-request.comments", "context/comments.json", "comments.json", 1, "LGTM"));

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("unauthorized_source"))
                    .hasMessageContaining("misattributed evidence source");
            verifyNoInteractions(practiceResultRepository);
        }

        /**
         * Every source that applies to the artifact is staged for every review, so a quote from one this
         * practice's signals never named is still a quote from bytes that were really there — refusing it
         * would throw away a result for being observant.
         */
        @Test
        @DisplayName("a citation to a staged source the practice's signals did not name is accepted")
        void acceptsAStagedSourceOutsideThePracticeDeclaration() {
            ObjectNode snapshot = (ObjectNode) Objects.requireNonNull(testJob.getEvidenceSnapshot());
            var inventory = EvidenceSnapshotFixtures.availableSource(snapshot, "workspace.project-inventory", null);
            EvidenceSnapshotFixtures.artifact(snapshot, inventory, "context/project_inventory.json", "c".repeat(64));
            when(cas.containsUtf8AtLines(
                            testJob,
                            "context/project_inventory.json",
                            "c".repeat(64),
                            "\"title\":\"Same migration\"",
                            1,
                            1))
                    .thenReturn(Optional.of(true));
            ObjectNode candidate = met(record(
                    "workspace.project-inventory",
                    "context/project_inventory.json",
                    "project_inventory.json",
                    1,
                    "\"title\":\"Same migration\""));

            assertThat(publishVerified(testJob, submit("pr-description-quality", candidate))
                            .inserted())
                    .isEqualTo(1);
        }

        @Test
        void rejectsASourceWithdrawnAfterCapture() {
            when(sourceCatalogs.isSourceUsePermitted(any(), any(), eq(SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)))
                    .thenReturn(false);

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("authorization was withdrawn");
            verifyNoInteractions(practiceResultRepository);
            verify(sourceCatalogs).isSourceUsePermitted(any(), any(), eq(SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY));
        }

        @Test
        void rejectsAnUncitedSourceWithdrawnAfterCapture() {
            when(sourceCatalogs.isSourceUsePermitted(any(), eq(new SourceKind("scm.pull-request.core")), any()))
                    .thenReturn(false);

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("scm.pull-request.core");
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("only the claim whose quote does not verify is withheld; the other is delivered")
        void withholdsOnlyTheResultWhoseQuoteDoesNotVerify() {
            admitSecondPractice();
            ObjectNode misquoted = met();
            firstCitation(misquoted).put("quote", "insecure();,");

            var result = publishVerified(
                    testJob, results(entry("pr-description-quality", met()), entry("pr-scope", misquoted)));

            assertThat(result.recorded())
                    .as("the claim that verified is the one persisted, and it is the only one")
                    .extracting(ValidatedResult::practiceSlug)
                    .containsExactly("pr-description-quality");
            assertThat(result.inserted()).isEqualTo(1);
            JsonNode failures = Objects.requireNonNull(testJob.getMetadata()).path("citation_verification_failures");
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0).path("practiceSlug").asString()).isEqualTo("pr-scope");
            assertThat(failures.get(0).path("citation").asString()).isEqualTo("citations[0]");
            assertThat(failures.get(0).path("reasonCode").asString()).isEqualTo("QUOTE_LOCATION_MISMATCH");
            JsonNode withheld =
                    Objects.requireNonNull(testJob.getMetadata()).path(ReviewOutputService.WITHHELD_METADATA_KEY);
            assertThat(withheld).hasSize(1);
            assertThat(withheld.get(0).path("reasonCode").asString()).isEqualTo("quote_unverified");
        }

        @Test
        @DisplayName("a citation to an unstaged source still fails the whole delivery, even beside a sound claim")
        void anEvidenceFailureThatIsNotAQuoteMismatchStillFailsEverything() {
            admitSecondPractice();
            ObjectNode unstaged =
                    met(record("scm.pull-request.comments", "context/comments.json", "comments.json", 1, "LGTM"));

            assertThatThrownBy(() -> publishVerified(
                            testJob, results(entry("pr-description-quality", met()), entry("pr-scope", unstaged))))
                    .as("an unstaged source impugns the run, not just the claim that cited it")
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("unauthorized_source"));
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("a batch in which no quote verifies is refused rather than retried as an unavailable server")
        void shouldRefuseResultsWhenNoQuotedEvidenceVerifies() {
            ObjectNode misquoted = met();
            firstCitation(misquoted).put("quote", "fabricated quote");

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", misquoted)))
                    .isInstanceOfSatisfying(ResultsRefusedException.class, refusal -> {
                        assertThat(refusal.reasonCode()).isEqualTo("no_admissible_results");
                        JsonNode failures = refusal.verificationFailures();
                        assertThat(failures).hasSize(1);
                        assertThat(failures.get(0).path("practiceSlug").asString())
                                .isEqualTo("pr-description-quality");
                        assertThat(failures.get(0).path("citation").asString()).isEqualTo("citations[0]");
                        assertThat(failures.get(0).path("reasonCode").asString())
                                .isEqualTo("QUOTE_LOCATION_MISMATCH");
                    })
                    .hasMessageContaining("No result survived the evidence check");
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("a change whose capture failed cannot be quoted, however the checkout looks")
        void rejectsACitationToAnUnavailableSource() {
            ObjectNode snapshot = (ObjectNode) Objects.requireNonNull(testJob.getEvidenceSnapshot());
            EvidenceSnapshotFixtures.unavailable(snapshot, (ObjectNode)
                    snapshot.path("manifest").path("sources").get(0));

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("no pinned base and head");
            verifyNoInteractions(practiceResultRepository, historicalGit);
        }
    }

    /**
     * A submitted search warrant must be verified at the delivery boundary — not just
     * the in-sandbox normalizer, which a crashed runner or a rescued text payload can bypass.
     */
    @Nested
    class RecordedSearch {

        @Test
        @DisplayName("a NOT_MET result need not make an absence claim")
        void acceptsNotMetWithoutASearch() {
            ObjectNode candidate = notMet();
            evidence(candidate).remove("search");

            assertThat(publishVerified(testJob, submit("pr-description-quality", candidate))
                            .inserted())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("a recorded search missing any of its three parts is refused")
        void rejectsAnIncompleteSearch() {
            for (String field : new String[] {"consulted", "lookedFor", "boundary"}) {
                ObjectNode candidate = notMet();
                evidence(candidate).withObject("search").remove(field);

                assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                        .as("a search warrant missing search.%s", field)
                        .isInstanceOfSatisfying(
                                ResultsRefusedException.class,
                                refused -> assertThat(refused.reasonCode()).isEqualTo("malformed_candidate"));
            }
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("a search claiming a source this run never staged is refused")
        void rejectsASearchOutsideTheBoundary() {
            // The absence-shaped twin of citing evidence we never had: the source was not staged, so it
            // cannot have been searched, and the claim of having searched it is unfalsifiable otherwise.
            ObjectNode candidate = notMet();
            evidence(candidate).set("search", search("scm.pull-request.comments"));

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("unauthorized_source"))
                    .hasMessageContaining("claims a source this run did not stage");
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("a complete search warrant is delivered")
        void acceptsAnAbsentWithARecordedSearch() {
            assertThat(publishVerified(testJob, submit("pr-description-quality", notMet()))
                            .inserted())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("a MET absence claim requires an exhaustive evidence source")
        void rejectsACleanStrengthFromAnUnboundedPractice() {
            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", cleanStrength())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("declares no EXHAUSTIVE evidence source");
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("a MET absence claim is accepted over an exhaustive corpus")
        void acceptsACleanStrengthOverABoundedCorpus() {
            exhaustiveOverTheDiff(testPractice);

            assertThat(publishVerified(testJob, submit("pr-description-quality", cleanStrength()))
                            .inserted())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("a bounded corpus still has to have been searched whole")
        void stillRejectsAPartialSearchBehindACleanStrength() {
            // Declaring the stance is what makes the claim admissible, not what makes it true: the search must
            // still cover every source held exhaustive, or the strength is a universal over unread bytes.
            exhaustiveOverTheDiff(testPractice);
            ObjectNode candidate = cleanStrength();
            evidence(candidate).set("search", search("scm.pull-request.core"));

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("did not search the sources its practice asserts absence over");
            verifyNoInteractions(practiceResultRepository);
        }

        /** A MET result supported by a bounded search for a defect. */
        private ObjectNode cleanStrength() {
            ObjectNode candidate = met();
            evidence(candidate).set("search", search(DIFF));
            return candidate;
        }

        private void exhaustiveOverTheDiff(Practice practice) {
            var requirements = practice.getEvidenceRequirements().stream()
                    .map(requirement -> requirement.sourceKind().value().equals(DIFF)
                            ? new PracticeEvidenceRequirement(requirement.sourceKind(), EvidenceStance.EXHAUSTIVE)
                            : requirement)
                    .toList();
            practice.setEvidenceRequirements(requirements);
            lenient().when(testRevision.getEvidenceRequirements()).thenReturn(requirements);
        }

        @Test
        @DisplayName("a NOT_APPLICABLE result with no stated ground is refused")
        void rejectsAnUnjustifiedNotApplicable() {
            // Sandbox output is untrusted even when its normalizer enforces the same rule.
            ObjectNode candidate = notApplicable(insecure());
            evidence(candidate).remove("inapplicability");

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("warrant_outcome_mismatch"));
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("a stated inapplicability missing any of its three parts is refused")
        void rejectsAnIncompleteInapplicability() {
            for (String field : new String[] {"consulted", "subject", "ruledOutBy"}) {
                ObjectNode candidate = notApplicable(insecure());
                evidence(candidate).withObject("inapplicability").remove(field);

                assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                        .as("a NOT_APPLICABLE result missing inapplicability.%s", field)
                        .isInstanceOfSatisfying(
                                ResultsRefusedException.class,
                                refused -> assertThat(refused.reasonCode()).isEqualTo("malformed_candidate"));
            }
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("a stated inapplicability claiming a source this run never staged is refused")
        void rejectsAnInapplicabilityOutsideTheBoundary() {
            ObjectNode candidate = notApplicable(insecure());
            evidence(candidate).set("inapplicability", inapplicability("scm.pull-request.comments"));

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("unauthorized_source"))
                    .hasMessageContaining("claims a source this run did not stage");
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("decided outcomes require no inapplicability warrant, and MET no search")
        void doesNotAskForAGroundOnDecidedOutcomes() {
            for (Outcome outcome : new Outcome[] {Outcome.MET, Outcome.NOT_MET}) {
                ObjectNode candidate = valid(outcome);
                assertThat(evidence(candidate).get("inapplicability"))
                        .as("%s carries no stated inapplicability", outcome)
                        .isNull();

                assertThatCode(() -> publishVerified(testJob, submit("pr-description-quality", candidate)))
                        .as("%s is delivered without a stated inapplicability", outcome)
                        .doesNotThrowAnyException();
            }
            assertThat(evidence(met()).get("search")).isNull();
        }

        @Test
        @DisplayName("a citation to the review history is in bounds although no binding declared it")
        void acceptsACitationToTheStagedHistory() {
            stageHistory("we raised this in the last review");

            var result = publishVerified(
                    testJob, submit("pr-description-quality", historyCiting("we raised this in the last review")));

            assertThat(result.inserted()).isEqualTo(1);
        }

        @Test
        @DisplayName("a fabricated quote from the review history is withheld like any other")
        void rejectsAnInventedPastResult() {
            stageHistory("we raised this in the last review");

            assertThatThrownBy(() -> publishVerified(
                            testJob,
                            submit("pr-description-quality", historyCiting("we raised this three times before"))))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("no_admissible_results"));
            verifyNoInteractions(practiceResultRepository);
        }

        private static final String HISTORY_SHA = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

        private void stageHistory(String body) {
            ObjectNode snapshot = (ObjectNode) Objects.requireNonNull(testJob.getEvidenceSnapshot());
            EvidenceSnapshotFixtures.artifact(
                    snapshot,
                    EvidenceSnapshotFixtures.availableSource(snapshot, "hephaestus.observation-history", null),
                    "inputs/history/observations.json",
                    HISTORY_SHA);
            when(cas.containsUtf8AtLines(
                            eq(testJob),
                            eq("inputs/history/observations.json"),
                            eq(HISTORY_SHA),
                            anyString(),
                            anyInt(),
                            anyInt()))
                    .thenAnswer(invocation -> Optional.of(body.contains(invocation.getArgument(3, String.class))));
        }

        private ObjectNode historyCiting(String quote) {
            return met(record(
                    "hephaestus.observation-history",
                    "inputs/history/observations.json",
                    "inputs/history/observations.json",
                    1,
                    quote));
        }
    }

    /** Code-shaped and session-shaped candidates, and every part of a negative, are held to the same checks. */
    @Nested
    class Admission {

        @Test
        void shouldAdmitHostAndClaimedCoverageAlikeWhenTheirQuotesVerify() {
            admitSecondPractice();
            ObjectNode session = met();
            ObjectNode code = met();
            code.set("coverage", claimedCoverage());

            var result = publishVerified(
                    testJob, results(entry("pr-description-quality", session), entry("pr-scope", code)));

            assertThat(result.inserted()).isEqualTo(2);
            assertThat(storedResults())
                    .extracting(row -> row.coverage().path("source").asString())
                    .containsExactlyInAnyOrder("host", "claimed");
            assertThat(result.recorded())
                    .allSatisfy(recorded -> assertThat(storedCitationOf(recorded)
                                    .path("verification")
                                    .path("status")
                                    .asString())
                            .isEqualTo("VERIFIED"));
        }

        @Test
        void shouldWithholdHostAndClaimedCoverageAlikeWhenTheirQuotesDoNotVerify() {
            admitSecondPractice();
            ObjectNode session = met(change("NEW", 10, 10, "fabricated quote"));
            ObjectNode code = met(change("NEW", 10, 10, "fabricated quote"));
            code.set("coverage", claimedCoverage());

            assertThatThrownBy(() -> service.prepare(
                            testJob, results(entry("pr-description-quality", session), entry("pr-scope", code))))
                    .isInstanceOfSatisfying(ResultsRefusedException.class, refused -> {
                        assertThat(refused.reasonCode()).isEqualTo("no_admissible_results");
                        assertThat(refused.verificationFailures())
                                .extracting(
                                        failure -> failure.path("practiceSlug").asString())
                                .containsExactlyInAnyOrder("pr-description-quality", "pr-scope");
                        assertThat(refused.verificationFailures())
                                .extracting(
                                        failure -> failure.path("reasonCode").asString())
                                .containsOnly("QUOTE_LOCATION_MISMATCH");
                    });
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        void shouldWithholdThePracticeWhenOneProblemQuoteDoesNotVerify() {
            admitSecondPractice();
            ObjectNode negative = notMet(
                    problem(Severity.MAJOR, insecure()),
                    problem(Severity.MINOR, change("NEW", 11, 11, "allowSome();")));

            var prepared = service.prepare(
                    testJob, results(entry("pr-description-quality", negative), entry("pr-scope", met())));

            JsonNode withheld = withheld(prepared, "pr-description-quality");
            assertThat(withheld.path("reasonCode").asString()).isEqualTo("quote_unverified");
            assertThat(withheld.path("verificationFailures")).singleElement().satisfies(failure -> {
                assertThat(failure.path("citation").asString()).isEqualTo("problems[1].citations[0]");
                assertThat(failure.path("status").asString()).isEqualTo("REJECTED");
                assertThat(failure.path("reasonCode").asString()).isEqualTo("QUOTE_LOCATION_MISMATCH");
                assertThat(failure.path("candidateQuoteSha256").asString())
                        .isEqualTo(CitationVerification.quoteDigest("allowSome();"));
            });
            assertThat(prepared.withheld()).hasSize(1);
            assertThat(service.publish(testJob, prepared).recorded())
                    .extracting(ValidatedResult::practiceSlug)
                    .containsExactly("pr-scope");
        }

        @Test
        void shouldRecordNothingForThePracticeWhenItsOnlyNegativeIsWithheld() {
            admitSecondPractice();
            ObjectNode negative = notMet(problem(Severity.MAJOR, change("NEW", 10, 10, "fabricated quote")));

            var result = publishVerified(
                    testJob, results(entry("pr-description-quality", negative), entry("pr-scope", met())));

            assertThat(result.recorded())
                    .extracting(ValidatedResult::practiceSlug)
                    .containsExactly("pr-scope");
            assertThat(storedResults())
                    .as("a withheld negative leaves no row for its practice, and certainly no MET")
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.occurrenceKey()).startsWith("pr-scope:");
                        assertThat(row.practiceId()).isEqualTo(20L);
                    });
            assertThat(storedObservations()).isEmpty();
        }

        @Test
        void shouldRefuseTheSubmissionWhenAPracticeRepeats() {
            assertThatThrownBy(() -> service.prepare(
                            testJob,
                            results(entry("pr-description-quality", met()), entry("pr-description-quality", notMet()))))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("ambiguous_practice_results"));
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        void shouldRefuseTheSubmissionWhenAPracticeWasNotAdmitted() {
            assertThatThrownBy(() -> service.prepare(
                            testJob, results(entry("pr-description-quality", met()), entry("unknown-practice", met()))))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("practice_not_admitted"))
                    .hasMessageContaining("not admitted");
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        void shouldRefuseTheSubmissionWhenAPracticeIsAboutSomebodyTheReviewCannotName() {
            when(testRevision.getSubject()).thenReturn(ActorRole.REVIEWER);

            assertThatThrownBy(() -> service.prepare(testJob, submit("pr-description-quality", met())))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("wrong_attribution"));
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        void shouldRefuseTheSubmissionWhenItIsEmpty() {
            assertThatThrownBy(() -> service.prepare(testJob, results()))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("no_admissible_results"));
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        void shouldWithholdAProblemWhenItsDutyIsNotInTheCriteria() {
            admitSecondPractice();
            when(testRevision.getCriteria())
                    .thenReturn("Every added catch block logs the failure with the invoice id.");
            ObjectNode negative = notMet(duty("every added catch block rethrows the failure"));

            var prepared = service.prepare(
                    testJob, results(entry("pr-description-quality", negative), entry("pr-scope", met())));

            JsonNode withheld = withheld(prepared, "pr-description-quality");
            assertThat(withheld.path("reasonCode").asString()).isEqualTo("duty_not_in_criteria");
            assertThat(withheld.path("verificationFailures")).isEmpty();
        }

        @Test
        void shouldAdmitAProblemWhenItsDutyIsQuotedFromTheCriteria() {
            when(testRevision.getCriteria())
                    .thenReturn("Every added catch block logs the failure with the invoice id.");
            ObjectNode negative = notMet(duty("every  added catch block LOGS the failure"));

            var prepared = service.prepare(testJob, submit("pr-description-quality", negative));

            assertThat(prepared.withheld()).isEmpty();
        }

        @Test
        void shouldWithholdAProblemWhenItRestsOnTheStateOfTheWork() {
            admitSecondPractice();
            ObjectNode state = problem(Severity.MINOR, insecure());
            state.putObject("basis").put("type", "state").put("explanation", "The module has no error handling.");

            var prepared = service.prepare(
                    testJob, results(entry("pr-description-quality", notMet(state)), entry("pr-scope", met())));

            assertThat(withheld(prepared, "pr-description-quality")
                            .path("reasonCode")
                            .asString())
                    .isEqualTo("basis_state_unsupported");
        }

        /** A problem that rests on a duty the criteria state, at its first citation. */
        private ObjectNode duty(String criterionQuote) {
            ObjectNode problem = problem(Severity.MINOR, insecure());
            problem.putObject("basis")
                    .put("type", "duty")
                    .put("criterionQuote", criterionQuote)
                    .put("event", 0)
                    .put("explanation", "The criteria require it, and this added line does not do it.");
            return problem;
        }

        private JsonNode storedCitationOf(ValidatedResult recorded) {
            return Objects.requireNonNull(recorded.evidence()).path("citations").get(0);
        }
    }

    /** What publication writes: the result's root and one child per problem. */
    @Nested
    class Publication {

        private static final String SCRIPT =
                "export default definePracticeScript({ meta: {}, run: (ctx) => ctx.review() });";

        /**
         * A negative whose own citation is line 10, whose first problem cites line 11 and whose second cites line 10
         * again, with an example at line 12 and an open question.
         */
        private ObjectNode negative() {
            captured(HEAD, "src/Auth.java", 12, "return;");
            ObjectNode candidate = notMet(
                    problem(Severity.MAJOR, change("NEW", 11, 11, "allowAll();")), problem(Severity.MINOR, insecure()));
            candidate.withArray("citations").add(insecure());
            ObjectNode first = (ObjectNode) candidate.withArray("problems").get(0);
            first.put("key", "allows-every-caller");
            first.put("nextStep", "Restore the admin check.");
            candidate
                    .putObject("example")
                    .put("note", "This early return keeps the guard simple.")
                    .set("citation", change("NEW", 12, 12, "return;"));
            evidence(candidate).putArray("undecidability").add(openQuestion());
            candidate
                    .putObject("coverage")
                    .put("source", "host")
                    .put("reads", 3)
                    .put("truncatedReads", 1)
                    .put("failedReads", 0)
                    .put("complete", false)
                    .putArray("omitted")
                    .addObject()
                    .put("what", "lines beyond the read limit")
                    .put("count", 1)
                    .put("reason", "truncated");
            return candidate;
        }

        @Test
        void shouldStoreTheResultAndOneObservationPerProblemWhenANegativeIsAdmitted() {
            ObjectNode candidate = negative();

            var result = publishVerified(testJob, submit("pr-description-quality", candidate));

            StoredResult row = storedResults().getFirst();
            assertThat(row.occurrenceKey()).isEqualTo("pr-description-quality:scm.pull_request:456:" + testJob.getId());
            assertThat(row.practiceId()).isEqualTo(10L);
            assertThat(row.revisionId()).isEqualTo(11L);
            assertThat(row.artifactKind()).isEqualTo("scm.pull_request");
            assertThat(row.artifactId()).isEqualTo(456L);
            assertThat(row.aboutUserId()).isEqualTo(789L);
            assertThat(row.summary()).isEqualTo(SUMMARY);
            assertThat(row.outcome()).isEqualTo("NOT_MET");
            assertThat(row.severity())
                    .as("no declared severity stores the worst problem's")
                    .isEqualTo("MAJOR");
            assertThat(row.rationale()).isEqualTo(EXPLANATION);
            assertThat(row.scopeKey()).isEqualTo("scm.pull_request:456:AUTHOR");
            assertThat(row.origin()).isEqualTo("LIVE");
            assertThat(row.coverage()).isEqualTo(candidate.get("coverage"));

            JsonNode citations = row.evidence().path("citations");
            assertThat(citations)
                    .as("the result's own citation, then each problem's that is not already listed")
                    .extracting(citation -> citation.path("startLine").asInt())
                    .containsExactly(10, 11);
            assertThat(citations)
                    .extracting(citation ->
                            citation.path("verification").path("status").asString())
                    .containsOnly("VERIFIED");
            assertThat(row.evidence().path("search")).isEqualTo(search(DIFF));
            assertThat(row.evidence().path("undecidability")).singleElement().isEqualTo(openQuestion());

            JsonNode example = Objects.requireNonNull(row.example());
            assertThat(example.path("note").asString()).isEqualTo("This early return keeps the guard simple.");
            assertThat(example.path("citation")
                            .path("verification")
                            .path("status")
                            .asString())
                    .isEqualTo("VERIFIED");

            JsonNode provenance = row.provenance();
            assertThat(provenance.path("jobId").asString())
                    .isEqualTo(testJob.getId().toString());
            assertThat(provenance.path("attempt").asInt(-1)).isZero();
            assertThat(provenance.path("practiceRevisionId").asLong()).isEqualTo(11L);
            assertThat(provenance.path("program").path("kind").asString()).isEqualTo("builtin");
            assertThat(provenance.path("program").path("digest").isNull()).isTrue();
            assertThat(provenance.path("capturedRevision").isNull()).isTrue();
            assertThat(provenance.path("executionRun").path("jobId").asString())
                    .isEqualTo(testJob.getId().toString());
            assertThat(provenance.path("executionRun").path("attempt").asInt(-1))
                    .isZero();
            assertThat(provenance.path("executionRun").path("practiceSlug").asString())
                    .isEqualTo("pr-description-quality");

            String recurrenceKey = ObservationFingerprint.compute(
                    "pr-description-quality", "scm.pull_request", 456L, 789L, "src/Auth.java");
            List<StoredObservation> problems = storedObservations();
            assertThat(problems).hasSize(2);
            assertThat(problems).allSatisfy(problem -> {
                assertThat(problem.workspaceId()).isEqualTo(1L);
                assertThat(problem.practiceResultId()).isEqualTo(row.id());
                assertThat(problem.summary()).isEqualTo("Authorization check removed");
                assertThat(problem.recurrenceKey()).matches("[0-9a-f]{64}").isEqualTo(recurrenceKey);
                assertThat(problem.citations())
                        .singleElement()
                        .satisfies(citation -> assertThat(citation.path("verification")
                                        .path("status")
                                        .asString())
                                .isEqualTo("VERIFIED"));
                assertThat(Objects.requireNonNull(problem.basis()).path("type").asString())
                        .isEqualTo("introduced");
            });
            StoredObservation first = problems.get(0);
            assertThat(first.position()).isZero();
            assertThat(first.key()).isEqualTo("allows-every-caller");
            assertThat(first.severity()).isEqualTo("MAJOR");
            assertThat(first.explanation())
                    .isEqualTo("The change replaces the admin check with a call that allows every caller.");
            assertThat(first.nextStep()).isEqualTo("Restore the admin check.");
            assertThat(first.citations().get(0).path("startLine").asInt()).isEqualTo(11);
            StoredObservation second = problems.get(1);
            assertThat(second.position()).isEqualTo(1);
            assertThat(second.key()).isNull();
            assertThat(second.severity()).isEqualTo("MINOR");
            assertThat(second.nextStep()).isNull();
            assertThat(second.citations().get(0).path("startLine").asInt()).isEqualTo(10);

            ValidatedResult recorded = result.recorded().getFirst();
            assertThat(recorded.practiceResultId()).isEqualTo(row.id());
            assertThat(recorded.severity()).isEqualTo(Severity.MAJOR);
            assertThat(recorded.problems())
                    .extracting(ValidatedProblem::observationId)
                    .containsExactly(first.id(), second.id());
        }

        @Test
        void shouldKeepTheDeclaredSeverityWhenTheNegativeStatesOne() {
            ObjectNode candidate = notMet(problem(Severity.MINOR, insecure()));
            candidate.put("severity", "MAJOR");

            publishVerified(testJob, submit("pr-description-quality", candidate));

            assertThat(storedResults().getFirst().severity()).isEqualTo("MAJOR");
            assertThat(storedObservations().getFirst().severity()).isEqualTo("MINOR");
        }

        @Test
        void shouldStampTheProgramDigestWhenTheRevisionHoldsACustomScript() {
            when(testRevision.getPracticeScript()).thenReturn(SCRIPT);
            ((ObjectNode) Objects.requireNonNull(testJob.getEvidenceSnapshot()))
                    .putObject(ReviewedWork.SNAPSHOT_KEY)
                    .put("head", HEAD);

            publishVerified(testJob, submit("pr-description-quality", met()));

            JsonNode provenance = storedResults().getFirst().provenance();
            assertThat(provenance.path("program").path("kind").asString()).isEqualTo("custom");
            assertThat(provenance.path("program").path("digest").asString())
                    .isEqualTo(ProvenanceDigest.sha256Hex(SCRIPT.getBytes(StandardCharsets.UTF_8)));
            assertThat(provenance.path("capturedRevision").asString()).isEqualTo(HEAD);
        }

        @Test
        void shouldDiscardAnIdenticalRetryWhenTheResultIsAlreadyRecorded() {
            ObjectNode candidate = negative();
            var first = publishVerified(testJob, submit("pr-description-quality", candidate));
            alreadyRecorded();

            var retry = publishVerified(testJob, submit("pr-description-quality", candidate));

            assertThat(retry.inserted()).isZero();
            assertThat(retry.discardedDuplicate()).isEqualTo(1);
            assertThat(retry.recorded().getFirst().practiceResultId())
                    .isEqualTo(first.recorded().getFirst().practiceResultId());
            assertThat(retry.recorded().getFirst().problems())
                    .extracting(ValidatedProblem::observationId)
                    .containsExactlyElementsOf(first.recorded().getFirst().problems().stream()
                            .map(ValidatedProblem::observationId)
                            .toList());
            assertThat(storedObservations())
                    .as("a duplicate writes no second set of problems")
                    .hasSize(2);
        }

        @Test
        void shouldRefuseARetryWhenAProblemChanged() {
            ObjectNode candidate = negative();
            publishVerified(testJob, submit("pr-description-quality", candidate));
            alreadyRecorded();
            ObjectNode changed = candidate.deepCopy();
            ((ObjectNode) changed.withArray("problems").get(1)).put("summary", "A different problem");

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", changed)))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("cannot be changed by retry");
        }

        /** The repositories now hold exactly what the first publication wrote. */
        private void alreadyRecorded() {
            StoredResult row = storedResults().getFirst();
            PracticeResult stored = PracticeResult.builder()
                    .id(row.id())
                    .occurrenceKey(row.occurrenceKey())
                    .agentJobId(testJob.getId())
                    .workspaceId(1L)
                    .practice(testPractice)
                    .practiceRevision(testRevision)
                    .artifactKind(ArtifactKinds.PULL_REQUEST)
                    .artifactId(row.artifactId())
                    .aboutUserId(row.aboutUserId())
                    .scopeKey(row.scopeKey())
                    .summary(row.summary())
                    .outcome(Outcome.valueOf(row.outcome()))
                    .severity(Severity.valueOf(Objects.requireNonNull(row.severity())))
                    .evidence(row.evidence())
                    .evidenceRationale(row.rationale())
                    .example(row.example())
                    .coverage(row.coverage())
                    .provenance(row.provenance())
                    .origin(ResultOrigin.valueOf(row.origin()))
                    .observedAt(Instant.now())
                    .build();
            List<Observation> problems = storedObservations().stream()
                    .map(problem -> Observation.builder()
                            .id(problem.id())
                            .workspaceId(problem.workspaceId())
                            .practiceResultId(problem.practiceResultId())
                            .position(problem.position())
                            .observationKey(problem.key())
                            .severity(Severity.valueOf(problem.severity()))
                            .summary(problem.summary())
                            .explanation(problem.explanation())
                            .citations(problem.citations())
                            .basis(problem.basis())
                            .nextStep(problem.nextStep())
                            .recurrenceKey(problem.recurrenceKey())
                            .build())
                    .toList();
            insertedRows(0);
            when(practiceResultRepository.findByAgentJobId(testJob.getId(), 1L)).thenReturn(List.of(stored));
            lenient()
                    .when(observationRepository.findByPracticeResultIds(eq(1L), any()))
                    .thenReturn(problems);
        }
    }

    @Nested
    class HappyPath {

        @Test
        void persistsValidResult() {
            var result = publishVerified(testJob, submit("pr-description-quality", met()));

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.discardedDuplicate()).isZero();
            assertThat(storedResults()).singleElement().satisfies(row -> {
                assertThat(row.occurrenceKey())
                        .isEqualTo("pr-description-quality:scm.pull_request:456:" + testJob.getId());
                assertThat(row.practiceId()).isEqualTo(10L);
                assertThat(row.revisionId()).isEqualTo(11L);
                assertThat(row.artifactKind()).isEqualTo("scm.pull_request");
                assertThat(row.artifactId()).isEqualTo(456L);
                assertThat(row.aboutUserId()).isEqualTo(789L);
                assertThat(row.summary()).isEqualTo(SUMMARY);
                assertThat(row.outcome()).isEqualTo("MET");
                assertThat(row.severity()).isNull();
                assertThat(row.example()).isNull();
                assertThat(row.coverage()).isEqualTo(hostCoverage());
                assertThat(row.scopeKey()).isEqualTo("scm.pull_request:456:AUTHOR");
                // An event-triggered review is the unbiased population.
                assertThat(row.origin()).isEqualTo("LIVE");
            });
            assertThat(storedObservations()).as("a MET result has no problems").isEmpty();
            assertThat(result.recorded().getFirst().practiceResultId())
                    .isEqualTo(storedResults().getFirst().id());
        }
    }

    @Nested
    class TargetResolution {

        @Test
        void shouldResolveReviewerWhenSubmittedReviewMatchesArtifactAndSubject() {
            when(reviewTargets.reviewMatchesTarget(77L, 456L, 999L)).thenReturn(true);
            ObjectNode metadata = assertInstanceOf(ObjectNode.class, testJob.getMetadata());
            metadata.put("review_id", 77L);
            metadata.put("about_user_id", 999L);
            metadata.put("subject_role", "REVIEWER");
            Practice reviewing = new Practice();
            ReflectionTestUtils.setField(reviewing, "id", 30L);
            reviewing.setSlug("reviews-with-care");
            reviewing.setSignals(List.of(PracticeTestEvidence.defaultSignal(ArtifactKinds.PULL_REQUEST)));
            reviewing.setEvidenceRequirements(PracticeTestEvidence.needsFor(ArtifactKinds.PULL_REQUEST));
            reviewing.setReviewWhen(Map.of());
            reviewing.setSubject(ActorRole.REVIEWER);
            reviewing.setPrecondition(null);
            reviewing.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
            admit(reviewing, 31L);

            publishVerified(testJob, submit("reviews-with-care", met()));

            assertThat(storedResults()).singleElement().satisfies(row -> {
                assertThat(row.practiceId()).isEqualTo(30L);
                assertThat(row.revisionId()).isEqualTo(31L);
                assertThat(row.artifactId()).isEqualTo(456L);
                assertThat(row.aboutUserId()).isEqualTo(999L);
                assertThat(row.scopeKey()).isEqualTo("scm.pull_request:456:REVIEWER");
            });
        }

        @Test
        void shouldRejectReviewerWhenSubmittedReviewDoesNotMatchSubject() {
            ObjectNode metadata = assertInstanceOf(ObjectNode.class, testJob.getMetadata());
            metadata.put("review_id", 77L);
            metadata.put("about_user_id", 999L);
            metadata.put("subject_role", "REVIEWER");

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("no longer matches");
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        @DisplayName("throws when pull request not found")
        void prNotFound() {
            when(reviewTargets.findPullRequest(456L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("Pull request not found");
        }

        @Test
        @DisplayName("throws when pull request has no author")
        void prNoAuthor() {
            when(reviewTargets.findPullRequest(456L))
                    .thenReturn(Optional.of(new ReviewTargetQuery.Target(123L, "owner/repo", 42, null, false)));

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("no author");
        }

        @Test
        void mismatchedArtifactMetadataIsRejectedBeforePersistence() {
            ObjectNode metadata = assertInstanceOf(ObjectNode.class, testJob.getMetadata());
            metadata.put("repository_id", 999L);

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("does not match the live target");
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        void conversationTargetMustMatchTheLiveWorkspaceThreadAndParticipant() {
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("artifact_kind", ArtifactKinds.CONVERSATION_THREAD.value());
            metadata.put("slack_thread_id", 77L);
            metadata.put("slack_channel_id", "C123");
            metadata.put("slack_thread_ts", "1700000000.100000");
            metadata.put("about_user_id", 789L);
            testJob.setMetadata(metadata);

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("no longer authorized");
            verify(conversationSourceLiveness).isDeliverableThread(1L, 77L, "C123", "1700000000.100000", 789L);
        }
    }

    @Nested
    class ConversationCitations {

        private static final String THREAD = "context/conversation_thread.json";
        private static final String THREAD_DIGEST = "9".repeat(64);

        /** Written as the content source writes it, with the default pretty printer. */
        private static final String THREAD_JSON = """
                {
                  "channel" : "C123",
                  "messages" : [ {
                    "ts" : "1700000000.100000",
                    "text" : "Can someone look at the failing build?",
                    "underReview" : false
                  }, {
                    "ts" : "1700000000.200000",
                    "text" : "It is broken. Fix it.",
                    "underReview" : true
                  } ]
                }
                """;

        @BeforeEach
        void conversationReview() {
            testJob.setJobType(AgentJobType.CONVERSATION_REVIEW);
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("artifact_kind", ArtifactKinds.CONVERSATION_THREAD.value());
            metadata.put("slack_thread_id", 77L);
            metadata.put("slack_channel_id", "C123");
            metadata.put("slack_thread_ts", "1700000000.100000");
            metadata.put("about_user_id", 789L);
            testJob.setMetadata(metadata);
            when(conversationSourceLiveness.isDeliverableThread(1L, 77L, "C123", "1700000000.100000", 789L))
                    .thenReturn(true);
            ObjectNode snapshot = (ObjectNode) Objects.requireNonNull(testJob.getEvidenceSnapshot());
            EvidenceSnapshotFixtures.artifact(
                    snapshot,
                    EvidenceSnapshotFixtures.availableSource(snapshot, "slack.conversation.thread", null),
                    THREAD,
                    THREAD_DIGEST);
            when(cas.containsUtf8AtLines(eq(testJob), eq(THREAD), eq(THREAD_DIGEST), anyString(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(true));
            lenient()
                    .when(cas.inspect(eq(testJob), eq(THREAD), eq(THREAD_DIGEST), any()))
                    .thenAnswer(invocation -> Optional.of(invocation
                            .<JobEvidenceFiles.TextInspection<?>>getArgument(3)
                            .inspect(new StringReader(THREAD_JSON))));
        }

        private ObjectNode turn(int line, String quote) {
            return record("slack.conversation.thread", THREAD, "conversation_thread.json", line, quote);
        }

        @Test
        @DisplayName("a NOT_MET that cites only another participant's turn is withheld with its reason")
        void shouldWithholdANotMetWhenItCitesOnlyAnotherParticipant() {
            ObjectNode candidate = notMet(problem(Severity.MINOR, turn(5, "Can someone look at the failing build?")));

            assertThatThrownBy(() -> service.prepare(testJob, submit("pr-description-quality", candidate)))
                    .isInstanceOfSatisfying(ResultsRefusedException.class, refused -> {
                        assertThat(refused.reasonCode()).isEqualTo("no_admissible_results");
                        JsonNode failure = refused.verificationFailures().get(0);
                        assertThat(failure.path("reasonCode").asString()).isEqualTo("NOT_THE_REVIEWED_PARTICIPANT");
                        assertThat(failure.path("citation").asString()).isEqualTo("problems[0].citations[0]");
                        assertThat(failure.path("practiceSlug").asString()).isEqualTo("pr-description-quality");
                    });
        }

        @Test
        @DisplayName("a NOT_MET that cites a turn of the participant under review is admitted")
        void shouldAdmitANotMetWhenItCitesTheParticipantUnderReview() {
            ObjectNode candidate = notMet(problem(Severity.MINOR, turn(9, "It is broken. Fix it.")));

            assertThatCode(() -> service.prepare(testJob, submit("pr-description-quality", candidate)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a MET may rest on another participant's turn, which is context")
        void shouldAdmitAMetThatCitesAnotherParticipant() {
            ObjectNode candidate = met(turn(5, "Can someone look at the failing build?"));

            assertThatCode(() -> service.prepare(testJob, submit("pr-description-quality", candidate)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class MetadataValidation {

        @Test
        void nullMetadata() {
            testJob.setMetadata(null);

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("Missing job metadata");
        }

        @Test
        void missingPullRequestId() {
            testJob.setMetadata(objectMapper.createObjectNode());

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("Missing pull_request_id");
        }
    }

    @Nested
    class OneResultPerPractice {

        @Test
        void shouldPersistResultsForDifferentPractices() {
            Practice otherPractice = new Practice();
            ReflectionTestUtils.setField(otherPractice, "id", 20L);
            otherPractice.setSlug("error-handling");
            PracticeTestEvidence.configure(otherPractice, ArtifactKinds.PULL_REQUEST);
            otherPractice.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
            admit(otherPractice, 22L);

            var result = publishVerified(
                    testJob, results(entry("error-handling", notMet()), entry("pr-description-quality", notMet())));

            assertThat(result.inserted()).isEqualTo(2);
            assertThat(result.discardedDuplicate()).isZero();
            assertThat(storedObservations()).hasSize(2);
        }
    }

    @Nested
    class NotApplicableResult {

        @Test
        @DisplayName("persists NOT_APPLICABLE result without counting as negative")
        void notApplicablePersisted() {
            var result = publishVerified(testJob, submit("pr-description-quality", notApplicable(insecure())));

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(storedResults().getFirst().severity()).isNull();
            assertThat(storedResults().getFirst().evidence().path("inapplicability"))
                    .isEqualTo(inapplicability(DIFF));
        }
    }

    @Nested
    class Idempotency {

        @Test
        void duplicateKeyWithoutReadableOriginalIsRejected() {
            insertedRows(0);

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", met())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("unavailable for retry");
        }
    }

    @Nested
    class IssueRouting {

        @Test
        void routesToIssueTargetAndAuthorWhenArtifactKindIsIssue() {
            when(reviewTargets.findIssue(999L))
                    .thenReturn(Optional.of(new ReviewTargetQuery.Target(123L, "owner/repo", 12, 789L, false)));

            ObjectNode meta = objectMapper.createObjectNode();
            meta.put("artifact_kind", ArtifactKinds.ISSUE.value());
            meta.put("issue_id", 999L);
            meta.put("repository_id", 123L);
            meta.put("repository_full_name", "owner/repo");
            meta.put("issue_number", 12);
            UUID reviewSnapshotId = UUID.randomUUID();
            meta.put("review_snapshot_id", reviewSnapshotId.toString());
            testJob.setMetadata(meta);
            when(practiceResultRepository.lockIssueSnapshotForReview(1L, testJob.getId(), 999L))
                    .thenReturn(Optional.of(reviewSnapshotId));

            var result = publishVerified(testJob, submit("pr-description-quality", notMet()));

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(storedResults()).singleElement().satisfies(row -> {
                assertThat(row.occurrenceKey()).isEqualTo("pr-description-quality:scm.issue:999:" + testJob.getId());
                assertThat(row.revisionId()).isEqualTo(11L);
                assertThat(row.artifactKind()).isEqualTo("scm.issue");
                assertThat(row.artifactId()).isEqualTo(999L);
                assertThat(row.aboutUserId()).isEqualTo(789L);
                assertThat(row.outcome()).isEqualTo("NOT_MET");
                assertThat(row.scopeKey()).isEqualTo("scm.issue:999:AUTHOR");
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"scm.issue.opened", "scm.issue.closed", "scm.issue.updated", "scm.issue.manual_review"})
        void shouldRefuseWhenTheIssueSnapshotChangesAfterPreparation(String signal) {
            when(reviewTargets.findIssue(999L))
                    .thenReturn(Optional.of(new ReviewTargetQuery.Target(123L, "owner/repo", 12, 789L, false)));
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("artifact_kind", ArtifactKinds.ISSUE.value());
            metadata.put("issue_id", 999L);
            metadata.put("repository_id", 123L);
            metadata.put("repository_full_name", "owner/repo");
            metadata.put("issue_number", 12);
            metadata.put("signal", signal);
            metadata.put("review_snapshot_id", UUID.randomUUID().toString());
            testJob.setMetadata(metadata);
            var prepared = service.prepare(testJob, submit("pr-description-quality", met()));
            when(practiceResultRepository.lockIssueSnapshotForReview(1L, testJob.getId(), 999L))
                    .thenReturn(Optional.of(UUID.randomUUID()));

            assertThatThrownBy(() -> service.publish(testJob, prepared))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("Issue changed after this review was submitted");
            assertThat(storedResults()).isEmpty();
        }

        @Test
        void refusesAKindWithNoDeliveryRoute() {
            ObjectNode meta = objectMapper.createObjectNode();
            meta.put("artifact_kind", "wiki.page");
            testJob.setMetadata(meta);

            assertThatThrownBy(() -> publishVerified(testJob, submit("pr-description-quality", notMet())))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("No delivery route for artifact kind: kind=wiki.page");
        }
    }

    /** An undecided review owes a captured change a reading only through a practice pinned to read it. */
    @Nested
    class TheChangeWitness {

        private static final String TITLE = "\"title\":\"Bump the lockfile\"";

        private PracticeEvidenceRequirement required(String sourceKind) {
            return new PracticeEvidenceRequirement(new SourceKind(sourceKind), EvidenceStance.REQUIRED);
        }

        /** Pins revision 11 reading {@code sourceKinds}; today's definition reads the change regardless. */
        private void pinDescriptionPractice(String... sourceKinds) {
            testPractice.setName("Describe what and why");
            testPractice.setCriteria("Judge the description.");
            testPractice.setEvidenceRequirements(
                    Arrays.stream(sourceKinds).map(this::required).toList());
            PracticeRevision pinned = new PracticeRevision(testPractice, 1);
            ReflectionTestUtils.setField(pinned, "id", 11L);
            when(practiceRevisionRepository.findByIdAndWorkspaceId(11L, 1L)).thenReturn(Optional.of(pinned));
            testPractice.setEvidenceRequirements(List.of(required("scm.pull-request.core"), required(DIFF)));
        }

        private ObjectNode fromTheRecord() {
            ObjectNode candidate = notApplicable(
                    record("scm.pull-request.core", "context/pull_request.json", "pull_request.json", 1, TITLE));
            evidence(candidate).set("inapplicability", inapplicability("scm.pull-request.core"));
            lenient()
                    .when(cas.containsUtf8AtLines(testJob, "context/pull_request.json", "b".repeat(64), TITLE, 1, 1))
                    .thenReturn(Optional.of(true));
            return candidate;
        }

        @Test
        void admitsAnUndecidedReviewWhosePinnedPracticeNeverReadTheCapturedChange() {
            pinDescriptionPractice("scm.pull-request.core");

            var result = publishVerified(testJob, submit("pr-description-quality", fromTheRecord()));

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(storedCitation(result)
                            .path("verification")
                            .path("status")
                            .asString())
                    .isEqualTo("VERIFIED");
        }

        @Test
        void refusesAnUndecidedReviewWhosePinnedPracticeReadsTheChangeWithoutAWitness() {
            pinDescriptionPractice("scm.pull-request.core", DIFF);

            assertThatThrownBy(() -> service.prepare(testJob, submit("pr-description-quality", fromTheRecord())))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            e -> assertThat(e.reasonCode()).isEqualTo("did_not_read_the_diff"))
                    .hasMessageContaining("answered without reading the change");
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        void aWithdrawnPracticeThatReadsTheChangeDoesNotBurdenTheReviewThatRemains() {
            pinDescriptionPractice("scm.pull-request.core");
            // The fixture's second practice reads the change, as a pull request practice does by default.
            admitSecondPractice().setSourceCuratedSlug("withdrawn");
            ReflectionTestUtils.setField(
                    service,
                    "fence",
                    new AutomatedReviewFence(Map.of(
                            "withdrawn",
                            new PracticeDefinition(
                                    "Withdrawn",
                                    PracticeTestEvidence.signals(ArtifactKinds.PULL_REQUEST),
                                    List.of(required(DIFF)),
                                    Map.of(),
                                    ActorRole.AUTHOR,
                                    null,
                                    "Criteria",
                                    null,
                                    PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST)
                                            .withdrawnFor(new PracticeEvidenceLimitation(
                                                    "AT_CLOSE_STATE_NOT_CAPTURED", "Nothing records the close.")),
                                    null,
                                    null,
                                    null))));

            var prepared = service.prepare(
                    testJob,
                    results(entry("pr-description-quality", fromTheRecord()), entry("pr-scope", fromTheRecord())));

            assertThat(withheld(prepared, "pr-scope").path("reasonCode").asString())
                    .isEqualTo("practice_withdrawn");
            assertThat(service.publish(testJob, prepared).recorded())
                    .extracting(ValidatedResult::practiceSlug)
                    .containsExactly("pr-description-quality");
        }

        @Test
        void aDiffQuoteThatDoesNotVerifyWitnessesNothingForAPracticeThatReadsTheChange() {
            pinDescriptionPractice("scm.pull-request.core", DIFF);
            admitSecondPractice();
            ObjectNode misquoted = notApplicable(change("NEW", 10, 10, "insecure();,"));

            var submission = results(entry("pr-description-quality", fromTheRecord()), entry("pr-scope", misquoted));

            assertThatThrownBy(() -> service.prepare(testJob, submission))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            e -> assertThat(e.reasonCode()).isEqualTo("did_not_read_the_diff"));
            verifyNoInteractions(practiceResultRepository);
        }

        @Test
        void admitsAMixedUndecidedReviewOnceThePracticeThatReadsTheChangeQuotesIt() {
            pinDescriptionPractice("scm.pull-request.core");
            Practice tests = new Practice();
            ReflectionTestUtils.setField(tests, "id", 12L);
            tests.setSlug("ships-tests-with-the-change");
            tests.setName("Include tests with the change");
            tests.setCriteria("Judge the tests.");
            PracticeTestEvidence.configure(tests, ArtifactKinds.PULL_REQUEST);
            tests.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
            tests.setEvidenceRequirements(List.of(required(DIFF)));
            tests.setWorkspace(testPractice.getWorkspace());
            PracticeRevision testsRevision = new PracticeRevision(tests, 1);
            ReflectionTestUtils.setField(testsRevision, "id", 13L);
            when(practiceRevisionRepository.findByIdAndWorkspaceId(13L, 1L)).thenReturn(Optional.of(testsRevision));
            EvidenceSnapshotFixtures.admittedPractice(
                    (ObjectNode) Objects.requireNonNull(testJob.getEvidenceSnapshot()),
                    "ships-tests-with-the-change",
                    13L);

            var result = publishVerified(
                    testJob,
                    results(
                            entry("pr-description-quality", fromTheRecord()),
                            entry("ships-tests-with-the-change", notApplicable(insecure()))));

            assertThat(result.inserted()).isEqualTo(2);
        }
    }
}
