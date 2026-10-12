package de.tum.cit.aet.hephaestus.agent.practice.live;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.CreateWorkspaceLlmConnectionRequestDTO;
import de.tum.cit.aet.hephaestus.agent.catalog.CreateWorkspaceLlmModelRequestDTO;
import de.tum.cit.aet.hephaestus.agent.catalog.InstanceLlmSettings;
import de.tum.cit.aet.hephaestus.agent.catalog.InstanceLlmSettingsRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmApiProtocol;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmAuthMode;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import de.tum.cit.aet.hephaestus.agent.catalog.PricingMode;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnectionService;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelService;
import de.tum.cit.aet.hephaestus.agent.config.AgentBindingRequestDTO;
import de.tum.cit.aet.hephaestus.agent.config.AgentBindingService;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceDirectory;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.PreparedJobInputsFixtures;
import de.tum.cit.aet.hephaestus.agent.context.ReviewedWork;
import de.tum.cit.aet.hephaestus.agent.context.providers.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.agent.gateway.SandboxGatewayProperties;
import de.tum.cit.aet.hephaestus.agent.handler.EvidenceSnapshotFixtures;
import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobLifecycleService;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobPracticeScriptRun;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobPracticeScriptRunRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobPracticeScriptUsage;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.job.PracticeScriptKindTotal;
import de.tum.cit.aet.hephaestus.agent.job.PracticeScriptRunRecorderFixtures;
import de.tum.cit.aet.hephaestus.agent.job.ReviewMemberAiPolicy;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeAgentRequest;
import de.tum.cit.aet.hephaestus.agent.practice.PracticePiAdapter;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeSandboxSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerProperties;
import de.tum.cit.aet.hephaestus.agent.usage.LlmAdmissionService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEvent;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEventRepository;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtIssuer;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeProgramKind;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptRunStatus;
import de.tum.cit.aet.hephaestus.testconfig.LiveLlmCredentials;
import de.tum.cit.aet.hephaestus.testconfig.LiveLlmTest;
import de.tum.cit.aet.hephaestus.testconfig.PiSdkInstallation;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextHolder;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Practice programs end to end against real models: workspace bindings, the frozen snapshot, the one job token, the
 * spec that {@link PracticePiAdapter} builds, the real LLM proxy on the sandbox gateway connector, the real result
 * admission, the per-attempt usage rows, the execution report and the ledger rows at the end of the attempt. The real
 * {@code pi-runner.ts} hosts {@code fixtures/every-slot.ts}, which calls every bound direct slot and then
 * {@code ctx.review()}, whose review session runs on the bound review model through the same proxy.
 *
 * <p>{@code docs/contributor/testing.mdx} § Live models for practice scripts names the variables. The worker role is on
 * so that the proxy and its gateway connector exist. The agent stays off, so no poller claims the job.
 *
 * <p>The job captures the pull request's record, change, comments and checkout once; the runner's folder index and the
 * job's snapshot are the same manifest, so admission verifies every quote the session may make, code included. The
 * change is {@code fixtures/cart-change.diff}, as in {@code live.test.ts}: its added comments are what every slot reads.
 */
@LiveLlmTest
class PracticeScriptLiveLlmTest extends AbstractWorkspaceIntegrationTest {

    private static final Path EVERY_SLOT = Path.of(
                    "..", "..", "docker", "agents", "practice-scripts", "fixtures", "every-slot.ts")
            .toAbsolutePath()
            .normalize();

    private static final String SLUG = "every-slot";

    private static final String TITLE = "Add the login service";

    private static final String BODY = "Adds the login service the app signs in with.";

    private static final String SOURCE_BRANCH = "feat/login";

    private static final String CRITERIA = """
            # Comments explain why

            ## Standard
            An added comment states a constraint the code cannot express, and no added comment restates the code or
            marks unfinished work that is merged anyway.
            """;

    private static final int GATEWAY_PORT = freePort();

    private static final Path FABRIC_ROOT = temporaryDirectory("practice-script-live-fabric-");

    @DynamicPropertySource
    static void workerRole(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.runtime.worker.enabled", () -> "true");
        registry.add("hephaestus.sandbox.gateway.port", () -> Integer.toString(GATEWAY_PORT));
        // The worker's maintenance removes the containers of its owner and the ended job folders under its
        // root. Other checkouts and test runs on this host keep theirs.
        registry.add("hephaestus.sandbox.docker.owner", () -> "practice-script-live-test");
        registry.add("hephaestus.fabric.root", FABRIC_ROOT::toString);
        // No sandbox runs here, so the worker does not pull the agent image.
        registry.add("hephaestus.agent.image.pull-policy", () -> "NEVER");
    }

    @Autowired
    private InstanceLlmSettingsRepository instanceLlmSettings;

    @Autowired
    private WorkspaceLlmConnectionService connections;

    @Autowired
    private WorkspaceLlmModelService models;

    @Autowired
    private AgentBindingService bindings;

    @Autowired
    private ReviewMemberAiPolicy reviewPolicy;

    @Autowired
    private LlmModelResolver resolver;

    @Autowired
    private LlmAdmissionService admission;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private LlmUsageEventRepository usageEvents;

    @Autowired
    private WorkerJwtIssuer jwtIssuer;

    @Autowired
    private WorkerProperties workerProperties;

    @Autowired
    private SandboxGatewayProperties gatewayProperties;

    @Autowired
    private PracticePiAdapter practiceAdapter;

    @Autowired
    private AgentJobLifecycleService lifecycle;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository revisionRepository;

    @Autowired
    private PullRequestRepository pullRequests;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private JobEvidenceFiles evidenceFiles;

    @Autowired
    private PiResultParser parser;

    @Autowired
    private AgentJobPracticeScriptRunRepository runs;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper objectMapper;

    private @Nullable Path root;

    private final List<PreparedJobInputs> prepared = new ArrayList<>();

    @BeforeAll
    static void installPiSdk() throws Exception {
        PiSdkInstallation.ensureInstalled();
    }

    @AfterEach
    void cleanUp() throws IOException {
        WorkspaceContextHolder.clearContext();
        prepared.forEach(PreparedJobInputs::close);
        prepared.clear();
        if (root != null) {
            FileSystemUtils.deleteRecursively(root);
        }
    }

    @AfterAll
    static void removeFabricRoot() throws IOException {
        FileSystemUtils.deleteRecursively(FABRIC_ROOT);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void shouldServeEveryBoundSlotAndTheReviewThroughTheProxyAndAdmitWithOneJobToken() throws Exception {
        LiveLlmCredentials chat = LiveLlmCredentials.fromEnv();
        @Nullable String embeddingModel = variable("HEPHAESTUS_LIVE_EMBEDDING_MODEL");
        @Nullable String rerankingModel = variable("HEPHAESTUS_LIVE_RERANKING_MODEL");
        @Nullable String decisionBase = variable("HEPHAESTUS_LIVE_DECISION_BASE_URL");
        @Nullable String decisionKey = variable("HEPHAESTUS_LIVE_DECISION_API_KEY");
        @Nullable String decisionModel = variable("HEPHAESTUS_LIVE_DECISION_MODEL");

        User developer = persistUser("practice-script-live-developer");
        Workspace workspace = workspace(developer);
        WorkspaceContext context =
                WorkspaceContext.fromWorkspace(workspace, Set.of(WorkspaceMembership.WorkspaceRole.ADMIN), null);
        WorkspaceContextHolder.setContext(context);
        EnumSet<ModelKind> boundKinds = EnumSet.noneOf(ModelKind.class);
        bind(
                context,
                AgentPurpose.PRACTICE_REVIEW,
                LlmApiProtocol.OPENAI_COMPLETIONS,
                chat.baseUrl(),
                chat.apiKey(),
                chat.model());
        Map<String, Map<String, Object>> warmUps = new LinkedHashMap<>();
        warmUps.put(
                "chat/completions",
                Map.of(
                        "model",
                        chat.model(),
                        "messages",
                        List.of(Map.of("role", "user", "content", "Hi")),
                        "max_tokens",
                        1));
        if (embeddingModel != null) {
            bind(
                    context,
                    AgentPurpose.PRACTICE_EMBEDDING,
                    LlmApiProtocol.OPENAI_EMBEDDINGS,
                    chat.baseUrl(),
                    chat.apiKey(),
                    embeddingModel);
            boundKinds.add(ModelKind.EMBEDDING);
            warmUps.put("embeddings", Map.of("model", embeddingModel, "input", List.of("Hi")));
        }
        if (rerankingModel != null) {
            bind(
                    context,
                    AgentPurpose.PRACTICE_RERANKING,
                    LlmApiProtocol.COHERE_RERANK,
                    chat.baseUrl(),
                    chat.apiKey(),
                    rerankingModel);
            boundKinds.add(ModelKind.RERANKING);
            warmUps.put("rerank", Map.of("model", rerankingModel, "query", "Hi", "documents", List.of("Hi")));
        }
        // As live.test.ts binds it: a decision endpoint when one is named, else the chat model through its logprobs.
        if (decisionBase != null && decisionKey != null && decisionModel != null) {
            bind(
                    context,
                    AgentPurpose.PRACTICE_DECISION,
                    LlmApiProtocol.OPENAI_DECISIONS,
                    decisionBase,
                    decisionKey,
                    decisionModel);
        } else {
            bind(
                    context,
                    AgentPurpose.PRACTICE_DECISION,
                    LlmApiProtocol.OPENAI_COMPLETIONS,
                    chat.baseUrl(),
                    chat.apiKey(),
                    chat.model());
        }
        boundKinds.add(ModelKind.DECISION);
        EnumSet<ModelKind> called = EnumSet.copyOf(boundKinds);
        called.add(ModelKind.CHAT);

        Path workDir = temporaryDirectory("practice-script-live-").toRealPath();
        root = workDir;
        // The spec is staged first: the change's checkout and the job's capture are pinned to the same commit.
        Practice practice = practice(workspace, Files.readString(EVERY_SLOT));
        AgentJob draft = runningJob(workspace, practice);
        ConfigSnapshot snapshot = ConfigSnapshot.fromJson(draft.getConfigSnapshot(), objectMapper);
        Map<ModelKind, FrozenModel> frozen = snapshot.practiceScriptModels();
        assertThat(frozen.keySet()).isEqualTo(called);
        String jobToken = jwtIssuer.issueForJobUntil(
                draft.getId(),
                context.id(),
                draft.getRetryCount(),
                Instant.now().plus(Duration.ofSeconds(LiveRunnerWorkspace.TIMEOUT_SECONDS + 300L)));
        PracticeSandboxSpec spec = practiceAdapter.buildSandboxSpec(new PracticeAgentRequest(
                snapshot.apiProtocol(),
                snapshot.upstreamModelId(),
                snapshot.contextWindow(),
                snapshot.maxOutputTokens(),
                snapshot.reasoningEffort(),
                jobToken,
                LiveRunnerWorkspace.TIMEOUT_SECONDS,
                frozen));
        assertThat(spec.environment())
                .as("one job token, no second credential")
                .doesNotContainKey("PRACTICE_SCRIPT_PROXY_TOKEN");
        LiveRunnerWorkspace.stageSpec(workDir, spec.inputFiles());
        LiveRunnerWorkspace.stageCartChange(workDir);
        long revisionId = Objects.requireNonNull(practice.getCurrentRevision().getId());
        LiveRunnerWorkspace.stagePractice(workDir, SLUG, "Every slot", CRITERIA, revisionId);
        LiveRunnerWorkspace.stagePracticeInputs(
                workDir,
                ArtifactKinds.PULL_REQUEST.value(),
                Map.of(SLUG, Objects.requireNonNull(practice.getPracticeScript())));
        LiveRunnerWorkspace.stageTask(workDir, "Review merge request #1 in test/fixture against " + SLUG + ".");
        AgentJob job = capture(draft, workDir, developer);

        warmUp(chat, warmUps);
        Path log = workDir.resolve("runner.log");
        Process runner = LiveRunnerWorkspace.spawn(
                workDir,
                spec.environment(),
                "http://127.0.0.1:" + gatewayProperties.port() + "/internal/llm",
                jobToken,
                log);
        if (!runner.waitFor(LiveRunnerWorkspace.TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            runner.destroyForcibly();
            throw new AssertionError("runner did not finish:\n" + Files.readString(log));
        }
        String runnerLog = Files.readString(log);

        AgentJob ended = jobRepository.findById(job.getId()).orElseThrow();
        JsonNode metadata = Objects.requireNonNull(ended.getMetadata());
        String refusal = metadata.path(ResultAdmissionService.REFUSAL_METADATA_KEY)
                .path("reasonCode")
                .asString("");
        assertThat(!metadata.path(ResultAdmissionService.DIGEST_METADATA_KEY)
                                .asString("")
                                .isBlank()
                        || !refusal.isBlank())
                .as("the server's admission decided on the runner's submission: %s%n%s", metadata, runnerLog)
                .isTrue();
        assertThat(refusal)
                .as("the submission's envelope is the one admission reads")
                .isNotEqualTo("malformed_admission");

        Path reportFile = workDir.resolve(SandboxLayout.OUTPUT_PREFIX + SandboxLayout.PRACTICE_SCRIPT_REPORT_FILE);
        assertThat(reportFile).as(runnerLog).exists();
        transactions.executeWithoutResult(status -> PracticeScriptRunRecorderFixtures.record(
                parser, runs, meterRegistry, objectMapper, ended, readBytes(reportFile), frozen, Instant.now()));
        AgentJobPracticeScriptRun run = runs.findByWorkspaceIdAndJobIdIn(context.id(), List.of(job.getId())).stream()
                .filter(row -> row.practiceSlug().equals(SLUG))
                .findFirst()
                .orElseThrow();
        String report = Files.readString(reportFile) + "\n" + runnerLog;
        assertThat(run.getStatus()).as(report).isEqualTo(PracticeScriptRunStatus.OK);
        assertThat(run.getProgramKind()).isEqualTo(PracticeProgramKind.CUSTOM);
        assertThat(run.getProgramDigest()).isEqualTo(ProvenanceDigest.sha256Hex(Files.readAllBytes(EVERY_SLOT)));
        JsonNode calls = Objects.requireNonNull(run.getCalls());
        assertThat(calls.path("review").asInt())
                .as("one review session: %s", report)
                .isEqualTo(1);
        for (ModelKind kind : called) {
            assertThat(calls.path(kind.slot()).asInt())
                    .as("%s was called: %s", kind, report)
                    .isPositive();
        }
        for (JsonNode slot : Objects.requireNonNull(run.getModels())) {
            assertThat(slot.path("notRated").isEmpty())
                    .as("every bound slot answered every call: %s", report)
                    .isTrue();
        }

        Map<ModelKind, PracticeScriptKindTotal> usage =
                jobRepository.sumPracticeScriptUsageByKind(context.id(), job.getId(), job.getRetryCount()).stream()
                        .collect(Collectors.toMap(PracticeScriptKindTotal::modelKind, Function.identity()));
        assertThat(usage.keySet()).isEqualTo(called);

        lifecycle.cancel(context.id(), job.getId());

        Map<LlmUsageSourceType, LlmUsageEvent> ledger = usageEvents.findAll().stream()
                .filter(event -> event.getSourceId().equals(job.getId()))
                .filter(event -> event.getSourceType() != LlmUsageSourceType.AGENT_JOB)
                .collect(Collectors.toMap(LlmUsageEvent::getSourceType, Function.identity()));
        assertThat(ledger.keySet())
                .containsExactlyInAnyOrderElementsOf(boundKinds.stream()
                        .map(AgentJobPracticeScriptUsage::ledgerSourceType)
                        .toList());
        for (ModelKind kind : boundKinds) {
            LlmUsageEvent row = Objects.requireNonNull(ledger.get(AgentJobPracticeScriptUsage.ledgerSourceType(kind)));
            PracticeScriptKindTotal counted = Objects.requireNonNull(usage.get(kind));
            assertThat(counted.inputTokens()).as("%s input tokens", kind).isPositive();
            assertThat(row.getModel())
                    .isEqualTo(Objects.requireNonNull(frozen.get(kind)).upstreamModelId());
            assertThat((long) row.getTotalCalls()).isEqualTo(counted.calls());
            assertThat(row.getInputTokens()).isEqualTo(counted.inputTokens());
            assertThat(row.getSourceAttempt()).isEqualTo(job.getRetryCount());
            assertThat(row.getPricingState()).isEqualTo(PricingState.PRICED);
        }
    }

    private static @Nullable String variable(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private Workspace workspace(User developer) {
        InstanceLlmSettings settings = instanceLlmSettings.findById((short) 1).orElseGet(() -> {
            InstanceLlmSettings created = new InstanceLlmSettings();
            created.setId((short) 1);
            return created;
        });
        settings.setAllowWorkspaceConnections(true);
        instanceLlmSettings.save(settings);

        String slug = "practice-script-live-" + System.nanoTime();
        Workspace workspace = createWorkspace(
                slug, "Practice script live", slug + "-org", AccountType.ORG, persistUser(slug + "-owner"));
        ensureAdminMembership(workspace);
        workspaceMembershipService.createMembership(
                workspace, developer.getId(), WorkspaceMembership.WorkspaceRole.MEMBER);
        return workspace;
    }

    /** One workspace connection and priced model per purpose, bound to the workspace's undeclared slot. */
    private void bind(
            WorkspaceContext context,
            AgentPurpose purpose,
            LlmApiProtocol protocol,
            String baseUrl,
            String apiKey,
            String upstreamModelId) {
        String name = purpose.name().toLowerCase(Locale.ROOT).replace('_', '-');
        WorkspaceLlmConnection connection = connections.create(
                context,
                new CreateWorkspaceLlmConnectionRequestDTO(
                        null, name, baseUrl, protocol, LlmAuthMode.BEARER, apiKey, true, null));
        Long modelId = models.create(
                        context,
                        connection.getId(),
                        new CreateWorkspaceLlmModelRequestDTO(
                                null,
                                name,
                                upstreamModelId,
                                null,
                                null,
                                null,
                                null,
                                null,
                                true,
                                PricingMode.PRICED,
                                new BigDecimal("0.10"),
                                new BigDecimal("0.40"),
                                null,
                                null,
                                null,
                                null))
                .getId();
        bindings.upsertBinding(
                context,
                purpose,
                DataHandlingTier.UNDECLARED,
                new AgentBindingRequestDTO(null, modelId, null, null, null, true));
    }

    /** The practice whose program is the every-slot fixture. */
    private Practice practice(Workspace workspace, String program) {
        Practice practice = new Practice();
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        practice.setWorkspace(workspace);
        practice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        practice.setSlug(SLUG);
        practice.setName("Every slot");
        practice.setCriteria(CRITERIA);
        practice.setPracticeScript(program);
        PracticeTestEvidence.configure(practice, ScmSignals.PULL_REQUEST_OPENED);
        practice = practiceRepository.saveAndFlush(practice);
        practice.setCurrentRevision(revisionRepository.save(new PracticeRevision(practice, 1)));
        return practiceRepository.saveAndFlush(practice);
    }

    /**
     * A running review owned by this worker, so that the proxy accepts its token. Submission freezes the bound models
     * and the claim prices them; both are private to the job pipeline, so this does the same from the same bindings.
     */
    private AgentJob runningJob(Workspace workspace, Practice practice) {
        Long workspaceId = workspace.getId();
        JsonNode metadata = objectMapper.createObjectNode();
        AgentJobType type = AgentJobType.PULL_REQUEST_REVIEW;
        var review = reviewPolicy.binding(workspaceId, type, metadata).orElseThrow();
        Map<ModelKind, FrozenModel> slots = new EnumMap<>(ModelKind.class);
        for (AgentPurpose purpose : AgentPurpose.practiceScript()) {
            reviewPolicy
                    .practiceScriptBinding(workspaceId, purpose, type, metadata)
                    .ifPresent(binding -> slots.put(
                            purpose.kind(),
                            FrozenModel.from(binding, resolver)
                                    .withPriceSnapshot(admission.admit(binding).price())));
        }
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(type);
        job.setArtifactKind(ArtifactKinds.PULL_REQUEST);
        job.setMetadata(metadata);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setWorkerId(workerProperties.resolvedWorkerId());
        job.setStartedAt(Instant.now());
        job.setExecutionStartedAt(Instant.now());
        job.setConfigSnapshot(ConfigSnapshot.from(review, resolver)
                .withPriceSnapshot(admission.admit(review).price())
                .withPracticeScript(slots.isEmpty() ? null : slots)
                .toJson(objectMapper));
        ObjectNode snapshot = EvidenceSnapshotFixtures.snapshot(objectMapper);
        EvidenceSnapshotFixtures.admittedPractice(
                snapshot,
                SLUG,
                Objects.requireNonNull(practice.getCurrentRevision().getId()));
        job.setEvidenceSnapshot(snapshot);
        return jobRepository.saveAndFlush(job);
    }

    /**
     * What the server captured for the job: the pull request at the staged commit, the job's metadata naming it, and
     * the record {@code PullRequestContentSource} writes from both, staged where the runner reads it.
     */
    private AgentJob capture(AgentJob job, Path workDir, User developer) throws IOException, InterruptedException {
        Path context = workDir.resolve(SandboxLayout.CONTEXT_PREFIX);
        JsonNode change = objectMapper.readTree(context.resolve("change.json").toFile());
        String base = change.path("base_sha").asString();
        String head = change.path("head_sha").asString();
        Repository repository = new Repository();
        repository.setNativeId(System.nanoTime());
        repository.setProvider(ensureGitHubProvider());
        repository.setName("fixture");
        repository.setNameWithOwner("test/fixture");
        repository.setHtmlUrl("https://github.com/test/fixture");
        repository.setDefaultBranch("main");
        repository = repositories.save(repository);
        Instant now = Instant.now();
        pullRequests.upsertCore(
                System.nanoTime(),
                Objects.requireNonNull(ensureGitHubProvider().getId()),
                1,
                TITLE,
                BODY,
                "OPEN",
                null,
                "https://github.com/test/fixture/pull/1",
                false,
                null,
                0,
                now,
                now,
                now,
                developer.getId(),
                repository.getId(),
                null,
                null,
                false,
                false,
                1,
                10,
                0,
                1,
                null,
                null,
                null,
                SOURCE_BRANCH,
                "main",
                head,
                base,
                null,
                null);
        long pullRequestId = Objects.requireNonNull(pullRequests
                .findByRepositoryIdAndNumber(repository.getId(), 1)
                .orElseThrow()
                .getId());
        ObjectNode metadata = LiveWorkRecords.pullRequestJob(
                pullRequestId, Objects.requireNonNull(repository.getId()), 1, head, TITLE, BODY, SOURCE_BRANCH);
        @Nullable Instant admittedAt = job.getCreatedAt();
        byte[] record = Objects.requireNonNull(
                transactions.execute(status -> LiveWorkRecords.staged(PullRequestContentSource.workRecord(
                        pullRequests.findByIdForReviewContext(pullRequestId).orElseThrow(), metadata, admittedAt))));
        Files.write(context.resolve("metadata.json"), record);

        // One capture for both sides: the job's snapshot, and the folder index the runner reads.
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<String, Path> onDisk = new LinkedHashMap<>();
        ObjectNode snapshot = (ObjectNode) Objects.requireNonNull(job.getEvidenceSnapshot());
        Map<String, ObjectNode> sources = new LinkedHashMap<>();
        Map<String, String> identities = Map.of(
                "scm.pull-request.diff",
                base + ":" + head,
                "scm.repository.tree",
                LiveRunnerWorkspace.treeIdentity(workDir));
        for (Map.Entry<String, String> captured :
                LiveRunnerWorkspace.capturedArtifacts().entrySet()) {
            String path = captured.getKey();
            String kind = captured.getValue();
            ObjectNode source = sources.computeIfAbsent(
                    kind, owner -> EvidenceSnapshotFixtures.availableSource(snapshot, owner, identities.get(owner)));
            byte[] bytes = Files.readAllBytes(workDir.resolve(path));
            if (kind.equals("scm.repository.tree")) onDisk.put(path, workDir.resolve(path));
            else files.put(path, bytes);
            EvidenceSnapshotFixtures.artifact(snapshot, source, path, ProvenanceDigest.sha256Hex(bytes))
                    .put("mediaType", path.endsWith(".json") ? "application/json" : "text/plain")
                    .put("bytes", bytes.length);
        }
        Files.write(
                workDir.resolve(SandboxLayout.MANIFEST_PATH),
                objectMapper.writeValueAsBytes(snapshot.path("manifest")));
        snapshot.set(
                ReviewedWork.SNAPSHOT_KEY,
                objectMapper.valueToTree(ReviewedWork.captured(
                                objectMapper.writeValueAsBytes(snapshot.path("manifest")),
                                files,
                                pullRequestId,
                                objectMapper)
                        .orElseThrow()));
        job.setMetadata(metadata);
        job.setEvidenceSnapshot(snapshot);
        job = jobRepository.saveAndFlush(job);
        // The checkout goes with its witnesses, as RepositoryTreeContentSource stages it, so admission can verify a
        // quote of code against it.
        prepared.add(PreparedJobInputsFixtures.prepare(
                evidenceFiles,
                job,
                PreparedJobInputsFixtures.inputs(
                        new PreparedEvidence(
                                files,
                                onDisk,
                                List.of(),
                                null,
                                List.of(new EvidenceDirectory(
                                        SandboxLayout.REPO_MOUNT_RELATIVE,
                                        workDir.resolve(SandboxLayout.REPO_MOUNT_RELATIVE)))),
                        null)));
        return jobRepository.findById(job.getId()).orElseThrow();
    }

    /**
     * The model host loads a model on its first request, which can take longer than a program's deadline: the host
     * would then report the slot as unrated for its deadline. Each self-hosted model answers one request here first.
     */
    private void warmUp(LiveLlmCredentials host, Map<String, Map<String, Object>> bodies) {
        String base = host.baseUrl().replaceAll("/+$", "");
        try (HttpClient client = HttpClient.newHttpClient()) {
            CompletableFuture.allOf(bodies.entrySet().stream()
                            .map(call -> client.sendAsync(
                                    HttpRequest.newBuilder(URI.create(base + "/" + call.getKey()))
                                            .timeout(Duration.ofMinutes(4))
                                            .header("Authorization", "Bearer " + host.apiKey())
                                            .header("Content-Type", "application/json")
                                            .POST(HttpRequest.BodyPublishers.ofByteArray(
                                                    objectMapper.writeValueAsBytes(call.getValue())))
                                            .build(),
                                    HttpResponse.BodyHandlers.discarding()))
                            .toArray(CompletableFuture[]::new))
                    .join();
        }
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path temporaryDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
