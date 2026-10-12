package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceDirectory;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.PreparedJobInputsFixtures;
import de.tum.cit.aet.hephaestus.agent.context.ReviewedWork;
import de.tum.cit.aet.hephaestus.agent.context.providers.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig.GitHubAppConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.ObservationFingerprint;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.testconfig.AdmittedReviewJobFixtures;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.GitTestFixtures;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Integration test for {@link ReviewOutputService} exercising real PostgreSQL
 * for idempotent result persistence, evidence admission and outcome classification.
 *
 * <p>No mocks required — this service layer does not call external APIs. It resolves practice
 * slugs against the DB and persists results via {@code PracticeResultRepository.insertIfAbsent()}.
 * The reviewed change is quoted from a real checkout staged in the attempt folder, the way the
 * repository-tree source stages one, so the quote is verified through JGit rather than a double.
 */
class ReviewOutputServiceIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ReviewOutputService deliveryService;

    @Autowired
    private ResultAdmissionService admission;

    @Autowired
    private PracticeResultRepository practiceResultRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository practiceRevisionRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private LlmModelRepository llmModelRepository;

    @Autowired
    private WorkspaceAgentBindingRepository workspaceAgentBindingRepository;

    @Autowired
    private LlmModelResolver llmModelResolver;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IdentityProviderRepository gitProviderRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private RepositoryToMonitorRepository monitors;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private JobEvidenceFiles evidenceFiles;

    @Autowired
    private FabricLayout evidenceLayout;

    private final List<PreparedJobInputs> preparedEvidence = new ArrayList<>();

    private static final String HEAD_PATH = SandboxLayout.REPO_MOUNT_RELATIVE + ".git/HEAD";
    private static final String REFS_PATH = SandboxLayout.REPO_MOUNT_RELATIVE + ".git/hephaestus-captured-refs";

    @TempDir
    private Path temporary;

    private Workspace workspace;
    private AgentJob agentJob;
    private User developer;
    private Long prId;
    private String baseSha;
    private String headSha;

    @AfterEach
    void releasePreparedEvidence() throws Exception {
        preparedEvidence.forEach(PreparedJobInputs::close);
        preparedEvidence.clear();
        deleteFixtureEvidence();
    }

    private void deleteFixtureEvidence() throws Exception {
        FileUtils.deleteDirectory(evidenceLayout
                .jobsRoot()
                .resolve(workspace.getId().toString())
                .resolve(agentJob.getId().toString())
                .toFile());
    }

    @BeforeEach
    void setUp() throws Exception {
        databaseTestUtils.cleanDatabase();

        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("delivery-test"));

        Practice description = createPractice("pr-description-quality", "PR Description Quality");
        Practice errors = createPractice("error-handling", "Error Handling");

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setWorkerId("test-worker");
        agentJob.setStatus(AgentJobStatus.RUNNING);
        agentJob.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(AdmittedReviewJobFixtures.snapshot(
                workspace,
                llmConnectionRepository,
                llmModelRepository,
                workspaceAgentBindingRepository,
                llmModelResolver,
                OBJECT_MAPPER));
        agentJob = agentJobRepository.save(agentJob);

        IdentityProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() -> gitProviderRepository.save(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));
        developer = TestUserFactory.createUser(200L, "test-pr-author", provider);
        developer = userRepository.save(developer);

        Repository repo = new Repository();
        repo.setNativeId(1001L);
        repo.setProvider(provider);
        repo.setName("test-repo");
        repo.setNameWithOwner("org/test-repo");
        repo.setHtmlUrl("https://github.com/org/test-repo");
        repo.setDefaultBranch("main");
        repo = repositoryRepository.save(repo);
        monitors.save(WorkspaceTestFixtures.repositoryMonitor(workspace, repo.getNameWithOwner()));
        var connection = new Connection(
                workspace,
                IntegrationKind.GITHUB,
                "1732",
                new GitHubAppConfig(1732L, workspace.getAccountLogin(), null, Set.of()));
        ReflectionTestUtils.setField(connection, "state", IntegrationState.ACTIVE);
        connections.save(connection);

        Instant now = Instant.now();
        Long providerId = Objects.requireNonNull(provider.getId());
        pullRequestRepository.upsertCore(
                5001L,
                providerId,
                42,
                "Test PR",
                "Test body",
                "OPEN",
                null,
                "https://github.com/org/test-repo/pull/42",
                false,
                null,
                0,
                now,
                now,
                now,
                developer.getId(),
                repo.getId(),
                null,
                null,
                false,
                false,
                1,
                10,
                5,
                3,
                null,
                null,
                null,
                "feature/test",
                "main",
                "abc123",
                "def456",
                null,
                null // mergeCommitSha
                );
        prId = pullRequestRepository
                .findByRepositoryIdAndNumber(repo.getId(), 42)
                .orElseThrow()
                .getId();

        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("pull_request_id", prId);
        metadata.put("repository_id", repo.getId());
        metadata.put("repository_full_name", repo.getNameWithOwner());
        metadata.put("pr_number", 42);
        agentJob.setMetadata(metadata);
        // The change: line 10 of src/Auth.java reads requireAdmin() at the base and insecure() at the head.
        Path checkout = Files.createDirectory(temporary.resolve("repo"));
        String treeSha;
        try (Git git = Git.init()
                .setInitialBranch("main")
                .setDirectory(checkout.toFile())
                .call()) {
            GitTestFixtures.disableSigning(git.getRepository());
            Files.createDirectories(checkout.resolve("src"));
            Files.writeString(checkout.resolve("src/Auth.java"), authSource("requireAdmin();"));
            baseSha = commit(git, "Base").name();
            Files.writeString(checkout.resolve("src/Auth.java"), authSource("insecure();"));
            RevCommit head = commit(git, "Head");
            headSha = head.name();
            treeSha = head.getTree().name();
        }
        Files.writeString(checkout.resolve(".git/hephaestus-captured-refs"), headSha + " HEAD\n");
        byte[] headWitness = Files.readAllBytes(checkout.resolve(".git/HEAD"));
        byte[] refsWitness = Files.readAllBytes(checkout.resolve(".git/hephaestus-captured-refs"));
        byte[] change = ("{\"base_sha\":\"" + baseSha + "\",\"head_sha\":\"" + headSha + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        ObjectNode snapshot = EvidenceSnapshotFixtures.snapshot(OBJECT_MAPPER);
        var diff = EvidenceSnapshotFixtures.availableSource(snapshot, "scm.pull-request.diff", baseSha + ":" + headSha);
        EvidenceSnapshotFixtures.artifact(
                        snapshot, diff, PullRequestContentSource.CHANGE_FILE, ProvenanceDigest.sha256Hex(change))
                .put("mediaType", "application/json")
                .put("bytes", change.length);
        var tree = EvidenceSnapshotFixtures.availableSource(snapshot, "scm.repository.tree", headSha + ":" + treeSha);
        EvidenceSnapshotFixtures.artifact(snapshot, tree, HEAD_PATH, ProvenanceDigest.sha256Hex(headWitness))
                .put("bytes", headWitness.length);
        EvidenceSnapshotFixtures.artifact(snapshot, tree, REFS_PATH, ProvenanceDigest.sha256Hex(refsWitness))
                .put("bytes", refsWitness.length);
        EvidenceSnapshotFixtures.admittedPractice(
                snapshot,
                description.getSlug(),
                Objects.requireNonNull(description.getCurrentRevision().getId()));
        EvidenceSnapshotFixtures.admittedPractice(
                snapshot,
                errors.getSlug(),
                Objects.requireNonNull(errors.getCurrentRevision().getId()));
        preparedEvidence.add(PreparedJobInputsFixtures.prepare(
                evidenceFiles,
                agentJob,
                PreparedJobInputsFixtures.inputs(
                        new PreparedEvidence(
                                Map.of(PullRequestContentSource.CHANGE_FILE, change),
                                Map.of(
                                        HEAD_PATH,
                                        checkout.resolve(".git/HEAD"),
                                        REFS_PATH,
                                        checkout.resolve(".git/hephaestus-captured-refs")),
                                List.of(),
                                null,
                                List.of(new EvidenceDirectory(SandboxLayout.REPO_MOUNT_RELATIVE, checkout))),
                        null)));
        agentJob.setEvidenceSnapshot(snapshot);
        agentJob.setStatus(AgentJobStatus.QUEUED);
        agentJob = agentJobRepository.save(agentJob);
    }

    /** Nine lines of context, then {@code line10} at line 10. */
    private static String authSource(String line10) {
        return "// context\n".repeat(9) + line10 + "\n";
    }

    private static RevCommit commit(Git git, String message) throws Exception {
        git.add().addFilepattern(".").call();
        return git.commit()
                .setSign(false)
                .setMessage(message)
                .setAuthor("Test", "test@example.com")
                .setCommitter("Test", "test@example.com")
                .call();
    }

    private Practice createPractice(String slug, String name) {
        Practice p = new Practice();
        p.setWorkspace(workspace);
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria("Test " + slug);
        p.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        PracticeTestEvidence.configure(p, ScmSignals.PULL_REQUEST_OPENED);
        p = practiceRepository.saveAndFlush(p);
        PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(p, 1));
        p.setCurrentRevision(revision);
        return practiceRepository.saveAndFlush(p);
    }

    // --- Candidates as the runner submits them -------------------------------------------------------------------

    /** A quote of the change at {@code src/Auth.java}. */
    private static ObjectNode change(String side, int line, String quote) {
        return OBJECT_MAPPER
                .createObjectNode()
                .put("sourceKind", "scm.pull-request.diff")
                .put("artifactPath", PullRequestContentSource.CHANGE_FILE)
                .put("path", "src/Auth.java")
                .put("side", side)
                .put("startLine", line)
                .put("endLine", line)
                .put("quote", quote);
    }

    /** Line 10 at the head, which the change added. */
    private static ObjectNode insecure() {
        return change("NEW", 10, "insecure();");
    }

    private static ObjectNode hostCoverage() {
        return OBJECT_MAPPER
                .createObjectNode()
                .put("source", "host")
                .put("reads", 1)
                .put("truncatedReads", 0)
                .put("failedReads", 0)
                .put("complete", true);
    }

    private static ObjectNode search(String boundary) {
        ObjectNode search = OBJECT_MAPPER.createObjectNode();
        search.putArray("consulted").add("scm.pull-request.diff");
        search.put("lookedFor", "a described rationale for the change");
        search.put("boundary", boundary);
        return search;
    }

    private static ObjectNode candidate(Outcome outcome, String slug, ObjectNode... citations) {
        ObjectNode candidate = OBJECT_MAPPER.createObjectNode();
        candidate.put("outcome", outcome.name());
        candidate.put("summary", "Test: " + slug);
        candidate.put("explanation", "The captured diff is the scope of this result.");
        ArrayNode cited = candidate.putArray("citations");
        Arrays.stream(citations).forEach(cited::add);
        candidate.putObject("evidence");
        candidate.set("coverage", hostCoverage());
        return candidate;
    }

    private static ObjectNode met(String slug) {
        return met(slug, insecure());
    }

    private static ObjectNode met(String slug, ObjectNode citation) {
        return candidate(Outcome.MET, slug, citation);
    }

    /** A NOT_MET whose problems carry its citations, with a recorded search of the change. */
    private static ObjectNode notMet(String slug, ObjectNode... problems) {
        ObjectNode candidate = candidate(Outcome.NOT_MET, slug);
        ArrayNode listed = candidate.putArray("problems");
        Arrays.stream(problems).forEach(listed::add);
        candidate.withObject("evidence").set("search", search("the diff of this pull request only"));
        return candidate;
    }

    private static ObjectNode notMet(String slug) {
        return notMet(slug, problem(Severity.MINOR, insecure()));
    }

    private static ObjectNode problem(Severity severity, ObjectNode citation) {
        ObjectNode problem = OBJECT_MAPPER.createObjectNode();
        problem.put("severity", severity.name());
        problem.put("summary", "Authorization check removed");
        problem.put("explanation", "The change replaces the admin check with a call that allows every caller.");
        problem.putArray("citations").add(citation);
        problem.putObject("basis")
                .put("type", "introduced")
                .put("head", 0)
                .put("explanation", "The pull request adds this line on the new side of the change.");
        return problem;
    }

    private static ObjectNode entry(String slug, ObjectNode candidate) {
        ObjectNode entry = OBJECT_MAPPER.createObjectNode();
        entry.put("practiceSlug", slug);
        entry.set("candidate", candidate);
        return entry;
    }

    /** The admission body's {@code results}. */
    private static ArrayNode results(ObjectNode... entries) {
        ArrayNode results = OBJECT_MAPPER.createArrayNode();
        Arrays.stream(entries).forEach(results::add);
        return results;
    }

    private ReviewOutputService.RecordedResults publishVerified(AgentJob job, JsonNode results) {
        return deliveryService.publish(job, deliveryService.prepare(job, results));
    }

    private List<PracticeResult> recordedRows() {
        return practiceResultRepository.findByAgentJobId(agentJob.getId(), workspace.getId());
    }

    private List<Observation> problemsOf(PracticeResult result) {
        return observationRepository.findByPracticeResultIds(workspace.getId(), List.of(result.getId()));
    }

    @Nested
    class EndToEnd {

        @Test
        void validResultsPersistedToDb() {
            var result = publishVerified(
                    agentJob,
                    results(
                            entry("error-handling", notMet("error-handling")),
                            entry("pr-description-quality", met("pr-description-quality"))));

            assertThat(result.inserted()).isEqualTo(2);
            List<PracticeResult> persisted = recordedRows();
            assertThat(persisted)
                    .extracting(PracticeResult::getOutcome)
                    .containsExactlyInAnyOrder(Outcome.MET, Outcome.NOT_MET);
            assertThat(persisted)
                    .allSatisfy(
                            row -> assertThat(problemsOf(row)).hasSize(row.getOutcome() == Outcome.NOT_MET ? 1 : 0));
        }

        @Test
        void shouldKeepPublishedResultsVerifiedAfterCapturedBytesAreDeleted() throws Exception {
            publishVerified(agentJob, results(entry("error-handling", notMet("error-handling"))));
            String sha = Objects.requireNonNull(agentJob.getEvidenceSnapshot())
                    .at("/manifest/sources/0/artifacts/0/sha256")
                    .asString();

            deleteFixtureEvidence();

            assertThat(evidenceFiles.inspect(
                            agentJob, PullRequestContentSource.CHANGE_FILE, sha, reader -> Boolean.TRUE))
                    .isEmpty();
            assertThatCode(() -> deliveryService.requirePublished(agentJob)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a quote of the change is read from the captured checkout at the side's revision")
        void shouldVerifyEachSideOfTheChangeAgainstTheCapturedCheckout() {
            var atHead = met("pr-description-quality");
            var atBase = met("error-handling", change("OLD", 10, "requireAdmin();"));

            var result = publishVerified(
                    agentJob, results(entry("error-handling", atBase), entry("pr-description-quality", atHead)));

            assertThat(result.inserted()).isEqualTo(2);
            assertThat(recordedRows())
                    .extracting(persisted -> Objects.requireNonNull(persisted.getEvidence())
                            .at("/citations/0/revision")
                            .asString())
                    .containsExactlyInAnyOrder(headSha, baseSha);
            assertThatCode(() -> deliveryService.requirePublished(agentJob)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a quote the checkout does not hold at the cited side is withheld, and the sound claim delivered")
        void shouldWithholdAQuoteTheCheckoutDoesNotHold() {
            var wrongSide = met("error-handling", change("OLD", 10, "insecure();"));

            var result = publishVerified(
                    agentJob,
                    results(
                            entry("error-handling", wrongSide),
                            entry("pr-description-quality", met("pr-description-quality"))));

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(recordedRows())
                    .extracting(persisted -> persisted.getPractice().getSlug())
                    .containsExactly("pr-description-quality");
        }

        @Test
        void shouldStoreTheResultAndOneObservationPerProblemWhenANegativeIsAdmitted() {
            ((ObjectNode) Objects.requireNonNull(agentJob.getEvidenceSnapshot()))
                    .putObject(ReviewedWork.SNAPSHOT_KEY)
                    .put("head", headSha);
            agentJob = agentJobRepository.save(agentJob);
            ObjectNode first = problem(Severity.MAJOR, change("NEW", 9, "// context"));
            first.put("key", "allows-every-caller");
            first.put("nextStep", "Restore the admin check.");
            ObjectNode negative = notMet("error-handling", first, problem(Severity.MINOR, insecure()));
            negative.withArray("citations").add(insecure());
            negative.putObject("example")
                    .put("note", "The file header states its purpose.")
                    .set("citation", change("NEW", 1, "// context"));
            ObjectNode coverage = negative.putObject("coverage")
                    .put("source", "claimed")
                    .put("selected", 2)
                    .put("checked", 1)
                    .put("total", 2)
                    .put("complete", false);
            coverage.putArray("omitted")
                    .addObject()
                    .put("what", "changed files beyond the selection")
                    .put("count", 1)
                    .put("reason", "selection");

            var result = publishVerified(agentJob, results(entry("error-handling", negative)));

            PracticeResult row = recordedRows().getFirst();
            assertThat(row.getOutcome()).isEqualTo(Outcome.NOT_MET);
            assertThat(row.getSeverity())
                    .as("no declared severity stores the worst problem's")
                    .isEqualTo(Severity.MAJOR);
            assertThat(row.getEvidenceRationale()).isEqualTo("The captured diff is the scope of this result.");
            assertThat(row.getScopeKey()).isEqualTo("scm.pull_request:" + prId + ":AUTHOR");
            assertThat(row.getCoverage()).isEqualTo(coverage);
            JsonNode evidence = Objects.requireNonNull(row.getEvidence());
            assertThat(evidence.path("citations"))
                    .as("the result's own citation, then each problem's that is not already listed")
                    .extracting(citation -> citation.path("startLine").asInt())
                    .containsExactly(10, 9);
            assertThat(evidence.path("search").path("boundary").asString())
                    .isEqualTo("the diff of this pull request only");
            JsonNode example = Objects.requireNonNull(row.getExample());
            assertThat(example.path("note").asString()).isEqualTo("The file header states its purpose.");
            assertThat(example.path("citation")
                            .path("verification")
                            .path("status")
                            .asString())
                    .isEqualTo("VERIFIED");

            PracticeRevision revision = practiceRepository
                    .findByWorkspaceIdAndSlug(workspace.getId(), "error-handling")
                    .orElseThrow()
                    .getCurrentRevision();
            JsonNode provenance = Objects.requireNonNull(row.getProvenance());
            assertThat(provenance.path("jobId").asString())
                    .isEqualTo(agentJob.getId().toString());
            assertThat(provenance.path("attempt").asInt(-1)).isZero();
            assertThat(provenance.path("practiceRevisionId").asLong()).isEqualTo(revision.getId());
            assertThat(provenance.path("program").path("kind").asString()).isEqualTo("builtin");
            assertThat(provenance.path("program").path("digest").isNull()).isTrue();
            assertThat(provenance.path("capturedRevision").asString()).isEqualTo(headSha);
            assertThat(provenance.path("executionRun").path("practiceSlug").asString())
                    .isEqualTo("error-handling");

            List<Observation> problems = problemsOf(row);
            assertThat(problems).extracting(Observation::getPosition).containsExactly(0, 1);
            assertThat(problems).extracting(Observation::getSeverity).containsExactly(Severity.MAJOR, Severity.MINOR);
            assertThat(problems)
                    .extracting(Observation::getObservationKey)
                    .containsExactly("allows-every-caller", null);
            assertThat(problems).extracting(Observation::getNextStep).containsExactly("Restore the admin check.", null);
            assertThat(problems)
                    .extracting(problem ->
                            problem.getCitations().path(0).path("startLine").asInt())
                    .containsExactly(9, 10);
            String recurrenceKey = ObservationFingerprint.compute(
                    "error-handling", "scm.pull_request", prId, developer.getId(), "src/Auth.java");
            assertThat(problems).allSatisfy(problem -> {
                assertThat(problem.getRecurrenceKey()).isEqualTo(recurrenceKey);
                assertThat(Objects.requireNonNull(problem.getBasis())
                                .path("type")
                                .asString())
                        .isEqualTo("introduced");
                assertThat(problem.getCitations()
                                .path(0)
                                .path("verification")
                                .path("status")
                                .asString())
                        .isEqualTo("VERIFIED");
            });
            assertThat(result.recorded().getFirst().practiceResultId()).isEqualTo(row.getId());
            assertThat(result.recorded().getFirst().problems())
                    .extracting(ReviewResultParser.ValidatedProblem::observationId)
                    .containsExactlyElementsOf(
                            problems.stream().map(Observation::getId).toList());
            assertThatCode(() -> deliveryService.requirePublished(agentJob)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("each recorded problem carries the recurrence key its stored row carries")
        void returnedRecurrenceKeysMatchPersistedObservations() {
            var result = publishVerified(
                    agentJob,
                    results(
                            entry("error-handling", notMet("error-handling")),
                            entry("pr-description-quality", met("pr-description-quality"))));

            List<String> returned = result.recorded().stream()
                    .flatMap(recorded -> recorded.problems().stream())
                    .map(ReviewResultParser.ValidatedProblem::recurrenceKey)
                    .toList();
            assertThat(returned).hasSize(1).allMatch(key -> key != null && key.matches("[0-9a-f]{64}"));
            assertThat(recordedRows().stream()
                            .flatMap(row -> problemsOf(row).stream())
                            .map(Observation::getRecurrenceKey)
                            .toList())
                    .containsExactlyInAnyOrderElementsOf(returned);
        }

        @Test
        void reorderedExactRetryKeepsOneFinalResultPerPractice() {
            var description = entry("pr-description-quality", met("pr-description-quality"));
            var errors = entry("error-handling", notMet("error-handling"));
            var first = publishVerified(agentJob, results(description, errors));

            var retry = publishVerified(agentJob, results(errors.deepCopy(), description.deepCopy()));

            assertThat(first.inserted()).isEqualTo(2);
            assertThat(retry.inserted()).isZero();
            assertThat(retry.discardedDuplicate()).isEqualTo(2);
            assertThat(recordedRows()).hasSize(2);
            assertThat(recordedRows().stream()
                            .mapToInt(row -> problemsOf(row).size())
                            .sum())
                    .as("a duplicate writes no second set of problems")
                    .isEqualTo(1);
        }

        @ParameterizedTest
        @ValueSource(strings = {"summary", "outcome", "severity", "evidence", "rationale", "problem"})
        void changedRetryIsRejectedWithoutChangingTheRecordedClaim(String field) {
            ObjectNode original = notMet("error-handling");
            publishVerified(agentJob, results(entry("error-handling", original)));
            ObjectNode changed =
                    switch (field) {
                        case "summary" -> original.deepCopy().put("summary", "Changed summary");
                        case "outcome" -> met("error-handling");
                        case "severity" -> original.deepCopy().put("severity", "MAJOR");
                        case "evidence" -> {
                            ObjectNode copy = original.deepCopy();
                            copy.withObject("evidence").set("search", search("changed claim scope"));
                            yield copy;
                        }
                        case "rationale" -> original.deepCopy().put("explanation", "Changed rationale");
                        case "problem" -> {
                            ObjectNode copy = original.deepCopy();
                            ((ObjectNode) copy.withArray("problems").get(0)).put("summary", "A different problem");
                            yield copy;
                        }
                        default -> throw new IllegalArgumentException(field);
                    };

            assertThatThrownBy(() -> publishVerified(
                            agentJob,
                            results(
                                    entry("error-handling", changed),
                                    entry("pr-description-quality", met("pr-description-quality")))))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("cannot be changed by retry");
            assertThat(recordedRows()).singleElement().satisfies(row -> {
                assertThat(row.getSummary()).isEqualTo("Test: error-handling");
                assertThat(row.getOutcome()).isEqualTo(Outcome.NOT_MET);
                assertThat(row.getSeverity()).isEqualTo(Severity.MINOR);
                assertThat(row.getEvidenceRationale()).isEqualTo("The captured diff is the scope of this result.");
                assertThat(problemsOf(row))
                        .singleElement()
                        .satisfies(
                                problem -> assertThat(problem.getSummary()).isEqualTo("Authorization check removed"));
            });
        }

        @Test
        @DisplayName("re-delivering same job creates no duplicates")
        void idempotentRedelivery() {
            var submitted = results(entry("pr-description-quality", met("pr-description-quality")));

            var first = publishVerified(agentJob, submitted);
            var admittedEvidence =
                    Objects.requireNonNull(first.recorded().getFirst().evidence());
            assertThat(recordedRows().getFirst().getEvidence())
                    .isEqualTo(OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(admittedEvidence)));
            var second = publishVerified(agentJob, submitted);

            assertThat(first.inserted()).isEqualTo(1);
            assertThat(second.inserted()).isZero();
            assertThat(second.discardedDuplicate()).isEqualTo(1);
            assertThat(recordedRows()).hasSize(1);
        }
    }

    @Nested
    class PracticeResolution {

        @Test
        void unknownSlugsFailDelivery() {
            var submitted = results(
                    entry("nonexistent-practice", met("nonexistent-practice")),
                    entry("pr-description-quality", met("pr-description-quality")));

            assertThatThrownBy(() -> publishVerified(agentJob, submitted))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode()).isEqualTo("practice_not_admitted"))
                    .hasMessageContaining("not admitted");
            assertThat(recordedRows()).isEmpty();
        }
    }

    @Nested
    class RevisionPinning {

        @Test
        @DisplayName("persisted result pins the practice's current definition revision")
        void practiceResultPinsCurrentRevision() {
            Practice practice = practiceRepository
                    .findByWorkspaceIdAndSlug(workspace.getId(), "pr-description-quality")
                    .orElseThrow();
            PracticeRevision revision = practice.getCurrentRevision();

            var result =
                    publishVerified(agentJob, results(entry("pr-description-quality", met("pr-description-quality"))));

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(recordedRows()).singleElement().satisfies(only -> {
                assertThat(only.getPracticeRevision()).isNotNull();
                assertThat(Objects.requireNonNull(only.getPracticeRevision()).getId())
                        .isEqualTo(revision.getId());
            });
        }
    }

    @Nested
    class OneResultPerPractice {

        @Test
        void shouldRefuseOpposingResultsBeforeAnyResultIsRecorded() {
            ObjectNode submitted = OBJECT_MAPPER.createObjectNode();
            submitted.put("schemaVersion", 2);
            submitted.set(
                    "results",
                    results(
                            entry("pr-description-quality", met("pr-description-quality")),
                            entry("pr-description-quality", notMet("pr-description-quality"))));

            agentJob.setStatus(AgentJobStatus.RUNNING);
            agentJob = agentJobRepository.saveAndFlush(agentJob);
            var identity = new ResultAdmissionService.AdmissionIdentity(
                    Objects.requireNonNull(agentJob.getId()),
                    Objects.requireNonNull(workspace.getId()),
                    agentJob.getRetryCount(),
                    "test-worker");
            assertThatThrownBy(() -> admission.admit(identity, submitted))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refusal -> assertThat(refusal.reasonCode()).isEqualTo("ambiguous_practice_results"));
            var storedJob = agentJobRepository.findById(identity.jobId()).orElseThrow();
            assertThat(Objects.requireNonNull(storedJob.getMetadata())
                            .path(ResultAdmissionService.REFUSAL_METADATA_KEY)
                            .path("reasonCode")
                            .asString())
                    .isEqualTo("ambiguous_practice_results");
            assertThat(ResultAdmissionService.isAdmitted(storedJob)).isFalse();
            assertThat(recordedRows()).isEmpty();
        }
    }

    @Test
    void shouldRefuseAnEmptyReviewWithoutPersistingResults() {
        assertThatThrownBy(() -> publishVerified(agentJob, results()))
                .isInstanceOfSatisfying(
                        ResultsRefusedException.class,
                        refused -> assertThat(refused.reasonCode()).isEqualTo("no_admissible_results"));
        assertThat(recordedRows()).isEmpty();
    }

    @Nested
    class NonNegativeResults {

        @Test
        void shouldPersistMetOutcomesWithoutSeverity() {
            var result = publishVerified(
                    agentJob,
                    results(
                            entry("error-handling", met("error-handling")),
                            entry("pr-description-quality", met("pr-description-quality"))));

            assertThat(result.inserted()).isEqualTo(2);
            assertThat(recordedRows()).hasSize(2).allSatisfy(row -> {
                assertThat(row.getOutcome()).isEqualTo(Outcome.MET);
                assertThat(row.getSeverity()).isNull();
                assertThat(problemsOf(row)).isEmpty();
            });
        }
    }

    @Nested
    class ErrorCases {

        @Test
        void throwsWhenPrNotFound() {
            ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
            metadata.put("pull_request_id", 999999L);
            agentJob.setMetadata(metadata);
            agentJob = agentJobRepository.save(agentJob);

            assertThatThrownBy(() -> publishVerified(
                            agentJob, results(entry("pr-description-quality", met("pr-description-quality")))))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("Pull request not found");
        }
    }
}
