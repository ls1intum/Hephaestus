package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.settings.InstanceSettings;
import de.tum.cit.aet.hephaestus.core.settings.InstanceSettingsService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeDetectionCompletedEvent;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@RecordApplicationEvents
class PracticeDetectionPipelineIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JobTypeHandlerRegistry handlerRegistry;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository practiceRevisionRepository;

    @Autowired
    private ContentAddressedStore cas;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IdentityProviderRepository gitProviderRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private InstanceSettingsService instanceSettingsService;

    @MockitoBean
    private PullRequestCommentPoster commentPoster;

    @MockitoBean
    private DiffNotePoster diffNotePoster;

    private JobTypeHandler handler;
    private Workspace workspace;
    private AgentJob agentJob;
    private Long prId;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        releaseSilentMode();

        workspace = WorkspaceTestFixtures.activeWorkspace("pipeline-test");
        workspace.getFeatures().setPracticesEnabled(true);
        workspace = workspaceRepository.save(workspace);

        Practice description = createPractice("pr-description-quality", "PR Description Quality");
        Practice errors = createPractice("error-handling", "Error Handling");

        IdentityProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                gitProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );

        User developer = TestUserFactory.createUser(500L, "pipeline-author", provider);
        developer = userRepository.save(developer);

        Repository repo = new Repository();
        repo.setNativeId(4001L);
        repo.setProvider(provider);
        repo.setName("pipeline-repo");
        repo.setNameWithOwner("org/pipeline-repo");
        repo.setHtmlUrl("https://github.com/org/pipeline-repo");
        repo.setDefaultBranch("main");
        repo = repositoryRepository.save(repo);
        repositoryToMonitorRepository.save(WorkspaceTestFixtures.repositoryMonitor(workspace, repo.getNameWithOwner()));

        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
            8001L,
            provider.getId(),
            50,
            "Pipeline Test PR",
            "Test body",
            "OPEN",
            null,
            "https://github.com/org/pipeline-repo/pull/50",
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
            "feature/pipeline",
            "main",
            "pipelinesha",
            "basesha",
            null,
            null // mergeCommitSha
        );
        prId = pullRequestRepository.findByRepositoryIdAndNumber(repo.getId(), 50).orElseThrow().getId();

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setStatus(AgentJobStatus.COMPLETED);
        agentJob.setConfigSnapshot(
            OBJECT_MAPPER.valueToTree(Map.of("model", "claude-3.5", "agentType", "CLAUDE_CODE"))
        );

        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("pull_request_id", prId);
        metadata.put("repository_id", repo.getId());
        metadata.put("repository_full_name", "org/pipeline-repo");
        metadata.put("pr_number", 50);
        metadata.put("pr_url", "https://github.com/org/pipeline-repo/pull/50");
        metadata.put("commit_sha", "pipelinesha");
        metadata.put("source_branch", "feature/pipeline");
        metadata.put("target_branch", "main");
        agentJob.setMetadata(metadata);
        agentJob.setEvidenceSnapshot(evidenceSnapshot(description, errors));
        agentJob = agentJobRepository.save(agentJob);

        handler = handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW);
        when(diffNotePoster.reconcileInlineNotes(any(), any())).thenReturn(
            new DiffNotePoster.DiffNoteResult(0, 0, List.of())
        );
    }

    private Practice createPractice(String slug, String name) {
        Practice p = new Practice();
        p.setEvidence(PracticeTestEvidence.pullRequest());
        p.setWorkspace(workspace);
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria("Test " + slug);
        p.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        p = practiceRepository.saveAndFlush(p);
        PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(p, 1));
        p.setCurrentRevision(revision);
        return practiceRepository.saveAndFlush(p);
    }

    private void setJobOutput(String rawOutput) {
        ObjectNode output = OBJECT_MAPPER.createObjectNode();
        output.put("rawOutput", withEvidence(rawOutput));
        agentJob.setOutput(output);
        agentJob = agentJobRepository.save(agentJob);
    }

    private void releaseSilentMode() {
        var current = instanceSettingsService.get();
        instanceSettingsService.updateSilentMode(false, null, "pipeline-test", version(current));
    }

    private void engageSilentMode() {
        var current = instanceSettingsService.get();
        instanceSettingsService.updateSilentMode(true, "pipeline safety test", "pipeline-test", version(current));
    }

    private static EntityTagPrecondition version(InstanceSettings settings) {
        return EntityTagPrecondition.parse("\"" + settings.getVersion() + "\"");
    }

    private AgentJob newJobWithOutput(String rawOutput) {
        AgentJob next = new AgentJob();
        next.setWorkspace(workspace);
        next.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        next.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        next.setStatus(AgentJobStatus.COMPLETED);
        next.setConfigSnapshot(agentJob.getConfigSnapshot());
        next.setMetadata(agentJob.getMetadata().deepCopy());
        next.setEvidenceSnapshot(agentJob.getEvidenceSnapshot().deepCopy());
        ObjectNode output = OBJECT_MAPPER.createObjectNode();
        output.put("rawOutput", withEvidence(rawOutput));
        next.setOutput(output);
        return agentJobRepository.save(next);
    }

    private ObjectNode evidenceSnapshot(Practice... practices) {
        ObjectNode snapshot = OBJECT_MAPPER.createObjectNode();
        var sources = snapshot.putObject("manifest").put("contractVersion", "1.0.0").putArray("sources");
        addArtifact(
            sources.addObject().put("kind", "scm.pull-request.core").put("availability", "AVAILABLE"),
            "inputs/context/metadata.json",
            "{\"body\":\"Test body\"}"
        );
        addArtifact(
            sources.addObject().put("kind", "scm.pull-request.diff").put("availability", "AVAILABLE"),
            "inputs/context/diff.patch",
            "diff --git a/src/Main.java b/src/Main.java\n+++ b/src/Main.java\n@@ -10 +10 @@\n[L10] + insecure();\n"
        );
        var admitted = snapshot.putArray("practices");
        for (Practice practice : practices) {
            admitted
                .addObject()
                .put("slug", practice.getSlug())
                .put("revisionId", practice.getCurrentRevision().getId());
        }
        return snapshot;
    }

    private void addArtifact(ObjectNode source, String path, String content) {
        source
            .putArray("artifacts")
            .addObject()
            .put("path", path)
            .put("sha256", cas.put(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String withEvidence(String rawOutput) {
        try {
            var root = OBJECT_MAPPER.readTree(rawOutput);
            for (var finding : root.path("findings")) {
                if (!finding.has("evidence")) {
                    var citation = ((ObjectNode) finding).putObject("evidence").putArray("citations").addObject();
                    citation.put("sourceKind", "scm.pull-request.core");
                    citation.put("artifactPath", "inputs/context/metadata.json");
                    citation.put("path", "body");
                    citation.put("startLine", 1);
                    citation.put("endLine", 1);
                    citation.put("quote", "Test body");
                }
            }
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (RuntimeException ignored) {
            return rawOutput;
        }
    }

    private String validAgentOutput() {
        String findings = """
            {
              "findings": [
                {
                  "practiceSlug": "pr-description-quality",
                  "title": "Good PR description",
                  "presence": "PRESENT",
                  "assessment": "GOOD",
                  "severity": "INFO",
                  "confidence": 0.95
                },
                {
                  "practiceSlug": "error-handling",
                  "title": "Missing null check",
                  "presence": "ABSENT",
                  "assessment": "BAD",
                  "severity": "MAJOR",
                  "confidence": 0.85,
                  "reasoning": "The method does not check for null input.",
                  "guidance": "Add a null check at the top of the method.",
                  "suggestedDiffNotes": [
                    { "filePath": "src/Main.java", "startLine": 10, "endLine": 15,
                      "body": "Consider adding a null check here." }
                  ]
                }
              ]""";
        return findings + "\n}";
    }

    @Nested
    class HappyPath {

        @Test
        void shouldPersistWithoutExternalWritesOrReplayWhenSilentModeIsEngaged() {
            setJobOutput(validAgentOutput());
            engageSilentMode();

            handler.deliver(agentJob);

            assertThat(observationRepository.findAll()).hasSize(2);
            assertThat(feedbackRepository.findAll())
                .singleElement()
                .extracting(Feedback::getDeliveryState, Feedback::getSuppressionReason)
                .containsExactly(FeedbackDeliveryState.SUPPRESSED, FeedbackSuppressionReason.INSTANCE_SILENCED);
            verify(commentPoster, never()).postFormattedBody(any(), any());
            verify(diffNotePoster, never()).reconcileInlineNotes(any(), any());

            releaseSilentMode();
            assertThat(feedbackRepository.findAll()).noneMatch(
                feedback -> feedback.getDeliveryState() == FeedbackDeliveryState.PREPARED
            );

            AgentJob newEvent = newJobWithOutput(validAgentOutput());
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("comment-after-release");
            handler.deliver(newEvent);

            verify(commentPoster).postFormattedBody(eq(newEvent), any(String.class));
            verify(diffNotePoster).reconcileInlineNotes(eq(newEvent), any());
            assertThat(observationRepository.findAll()).hasSize(4);
            assertThat(feedbackRepository.findAll())
                .extracting(Feedback::getDeliveryState)
                .containsExactlyInAnyOrder(FeedbackDeliveryState.SUPPRESSED, FeedbackDeliveryState.DELIVERED);
        }

        @Test
        void fullPipelineFromParseToDelivery() {
            setJobOutput(validAgentOutput());
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("comment-123");
            when(diffNotePoster.reconcileInlineNotes(any(), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(1, 0, List.of())
            );

            handler.deliver(agentJob);

            List<Observation> findings = observationRepository.findAll();
            assertThat(findings).hasSize(2);
            assertThat(findings)
                .extracting(Observation::getPresence)
                .containsExactlyInAnyOrder(Presence.PRESENT, Presence.ABSENT);

            List<PracticeDetectionCompletedEvent> events = applicationEvents
                .stream(PracticeDetectionCompletedEvent.class)
                .toList();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).findingsInserted()).isEqualTo(2);
            assertThat(events.get(0).hasNegative()).isTrue();

            verify(commentPoster).postFormattedBody(eq(agentJob), any(String.class));
            verify(diffNotePoster).reconcileInlineNotes(eq(agentJob), any());

            // AgentJobExecutor persists deliveryStatus, not handler.deliver(), so it stays null here.
            assertThat(agentJob.getDeliveryCommentId()).isEqualTo("comment-123");
            assertThat(agentJob.getDeliveryStatus()).isNull();
        }

        @Test
        void allPositiveFindingsPostsApproval() {
            String output = """
                {
                  "findings": [
                    {
                      "practiceSlug": "pr-description-quality",
                      "title": "Good description",
                      "presence": "PRESENT",
                      "assessment": "GOOD",
                      "severity": "INFO",
                      "confidence": 0.9
                    },
                    {
                      "practiceSlug": "error-handling",
                      "title": "Proper error handling",
                      "presence": "PRESENT",
                      "assessment": "GOOD",
                      "severity": "INFO",
                      "confidence": 0.9
                    }
                  ]
                }""";
            setJobOutput(output);
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("comment-approval");

            handler.deliver(agentJob);

            assertThat(observationRepository.findAll()).hasSize(2);

            // A findings summary reaches this same call, so only the body text tells an approval apart.
            var body = ArgumentCaptor.forClass(String.class);
            verify(commentPoster).postFormattedBody(eq(agentJob), body.capture());
            assertThat(body.getValue()).contains("nothing to change here");

            verify(diffNotePoster).reconcileInlineNotes(eq(agentJob), eq(List.of()));
        }
    }

    @Nested
    class ErrorCases {

        @Test
        void invalidJsonOutputFailsGracefully() {
            setJobOutput("this is not valid JSON at all");

            assertThatThrownBy(() -> handler.deliver(agentJob))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("No valid findings");

            assertThat(observationRepository.findAll()).isEmpty();
            verify(commentPoster, never()).postFormattedBody(any(), any());
        }

        @Test
        void unknownSlugRejectsDeliveryAtomically() {
            String output = """
                {
                  "findings": [
                    {
                      "practiceSlug": "pr-description-quality",
                      "title": "Good description",
                      "presence": "PRESENT",
                      "assessment": "GOOD",
                      "severity": "INFO",
                      "confidence": 0.9
                    },
                    {
                      "practiceSlug": "nonexistent-practice",
                      "title": "Unknown practice",
                      "presence": "PRESENT",
                      "assessment": "GOOD",
                      "severity": "INFO",
                      "confidence": 0.9
                    },
                    {
                      "practiceSlug": "error-handling",
                      "title": "Good handling",
                      "presence": "ABSENT",
                      "assessment": "BAD",
                      "severity": "MINOR",
                      "confidence": 0.8
                    }
                  ]
                }""";
            setJobOutput(output);

            assertThatThrownBy(() -> handler.deliver(agentJob))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("practice not admitted to the job");

            assertThat(observationRepository.findAll()).isEmpty();
            verify(commentPoster, never()).postFormattedBody(any(), any());
            verify(diffNotePoster, never()).reconcileInlineNotes(any(), any());
        }

        @Test
        void closedPrSkipsDelivery() {
            var pr = pullRequestRepository.findById(prId).orElseThrow();
            pullRequestRepository.upsertCore(
                8001L,
                pr.getProvider().getId(),
                50,
                "Pipeline Test PR",
                "Test body",
                "CLOSED",
                null,
                "https://github.com/org/pipeline-repo/pull/50",
                false,
                null,
                0,
                pr.getCreatedAt(),
                Instant.now(),
                pr.getCreatedAt(),
                pr.getAuthor().getId(),
                pr.getRepository().getId(),
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
                "feature/pipeline",
                "main",
                "pipelinesha",
                "basesha",
                null,
                null // mergeCommitSha
            );

            setJobOutput(validAgentOutput());

            handler.deliver(agentJob);

            // Findings are still persisted: deliver() persists first, then posts.
            assertThat(observationRepository.findAll()).hasSize(2);

            verify(commentPoster, never()).postFormattedBody(any(), any());
            verify(diffNotePoster, never()).reconcileInlineNotes(any(), any());

            assertThat(agentJob.getDeliveryCommentId()).isNull();
            assertThat(agentJob.getDeliveryStatus()).isNull();
        }
    }

    @Nested
    class FindingIdempotency {

        @Test
        @DisplayName("re-delivering same job creates no duplicate findings")
        void redeliveryNoDuplicates() {
            setJobOutput(validAgentOutput());
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("comment-789");
            when(diffNotePoster.reconcileInlineNotes(any(), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(1, 0, List.of())
            );

            handler.deliver(agentJob);
            assertThat(observationRepository.findAll()).hasSize(2);

            handler.deliver(agentJob);
            assertThat(observationRepository.findAll()).hasSize(2);

            List<PracticeDetectionCompletedEvent> events = applicationEvents
                .stream(PracticeDetectionCompletedEvent.class)
                .toList();
            assertThat(events).hasSize(2);
            assertThat(events.get(0).findingsInserted()).isEqualTo(2);
            assertThat(events.get(1).findingsInserted()).isZero();
            assertThat(events.get(1).findingsDiscarded()).isEqualTo(2);
        }
    }
}
