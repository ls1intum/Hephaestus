package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import de.tum.cit.aet.hephaestus.agent.adapter.EvidenceFolderPersonDataCatalog;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ResultHistoryConsentGateIntegrationTest extends AbstractSlackConsentGateIntegrationTest {

    private static final String EVIDENCE = evidence("scm.pull_request");
    private static final String NO_CITATIONS = "{\"citations\":[]}";

    @Autowired
    private ResultHistoryContentSource contentSource;

    @Autowired
    private PracticeResultRepository practiceResultRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository practiceRevisionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Practice practice;
    private AgentJob job;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setUpWorkspaceAndRecipient("obs-consent-gate-test");
        practice = new Practice();
        PracticeTestEvidence.configure(practice, ArtifactKinds.CONVERSATION_THREAD);
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.conversationThread());
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCriteria("Test description");
        PracticeTestEvidence.configure(practice, ScmSignals.PULL_REQUEST_OPENED);
        practice = practiceRepository.saveAndFlush(practice);
        PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(practice, 1));
        practice.setCurrentRevision(revision);
        practice = practiceRepository.saveAndFlush(practice);
        job = captureCopiedPeople(newJob(), recipient);
    }

    @Test
    @DisplayName("consent gate: only an ACTIVE-channel conversation result surfaces; PAUSED/REVOKED are withheld")
    void onlyActiveChannelConversationResultSurfaces() {
        long activeThreadId = seedThread("C-active", "100.0", ConsentState.ACTIVE);
        long pausedThreadId = seedThread("C-paused", "200.0", ConsentState.PAUSED);
        long revokedThreadId = seedThread("C-revoked", "300.0", ConsentState.REVOKED);

        PracticeResult activeObs = saveResult("occ-active", "chat.conversation_thread", activeThreadId);
        saveResult("occ-paused", "chat.conversation_thread", pausedThreadId);
        saveResult("occ-revoked", "chat.conversation_thread", revokedThreadId);
        PracticeResult prObs = saveResult("occ-pr", "scm.pull_request", 4242L);

        JsonNode root = contribute();

        assertThat(root.get("_meta").get("trustLevel").asString()).isEqualTo("UNTRUSTED_EXTERNAL");

        List<String> ids = practiceResultIds(root);
        assertThat(ids)
                .containsExactlyInAnyOrder(
                        activeObs.getId().toString(), prObs.getId().toString());
        assertThat(ids).doesNotContainNull();
        assertThat(ids).hasSize(2);
    }

    @Test
    @DisplayName("Slack consent does not suppress otherwise-authorized PR/issue results")
    void prIssueOnlyPayloadPassesThroughWithoutEnvelope() {
        PracticeResult prObs = saveResult("occ-pr", "scm.pull_request", 555L);
        PracticeResult issueObs = saveResult("occ-issue", "scm.issue", 777L);

        JsonNode root = contribute();

        assertThat(root.has("_meta")).isFalse();
        assertThat(practiceResultIds(root))
                .containsExactlyInAnyOrder(
                        prObs.getId().toString(), issueObs.getId().toString());
    }

    @Test
    @DisplayName("a revoked conversation result does not suppress an authorized PR result")
    void prSurvivesWhenAllConversationRevoked() {
        long revokedThreadId = seedThread("C-revoked", "300.0", ConsentState.REVOKED);
        saveResult("occ-revoked", "chat.conversation_thread", revokedThreadId);
        PracticeResult prObs = saveResult("occ-pr", "scm.pull_request", 909L);

        JsonNode root = contribute();

        assertThat(root.has("_meta")).isFalse();
        assertThat(practiceResultIds(root)).containsExactly(prObs.getId().toString());
    }

    @Test
    void shouldKeepUnvouchedObservationFactsWithoutEvidenceBesideTrustedHistory() {
        PracticeResult trusted = saveResult("trusted", "scm.pull_request", 909L);
        job = newJob();
        PracticeResult unvouched = saveResult("unvouched", "scm.pull_request", 910L);

        JsonNode root = contribute();
        assertThat(practiceResultIds(root))
                .containsExactlyInAnyOrder(
                        trusted.getId().toString(), unvouched.getId().toString());
        assertThat(root.path("wordsCoverage").asString()).isEqualTo("PARTIAL");
        ObjectNode detail = contentSource.inspect(workspace.getId(), recipient.getId(), unvouched.getId());
        JsonNode row = detail.path("result");
        assertThat(row.path("wordsUnavailable").asString())
                .isEqualTo(EvidenceFolderPersonDataCatalog.COPY_AUTHORITY_UNAVAILABLE);
        assertThat(row.has("summary")).isFalse();
        assertThat(row.has("evidenceRationale")).isFalse();
        assertThat(row.has("evidence")).isFalse();
        assertThat(contentSource
                        .inspect(workspace.getId(), recipient.getId(), trusted.getId())
                        .path("result")
                        .path("evidence")
                        .path("citations"))
                .hasSize(1);
    }

    @Test
    @DisplayName("an earlier review stays beside the latest one, abstentions are listed, and no other row is")
    void shouldKeepEarlierReviewsAndAbstentionsBesideEachClaimsLatestRun() {
        Instant base = Instant.now().minus(1, ChronoUnit.DAYS);
        UUID earlier = observe(
                captureCopiedPeople(newJob(), recipient),
                practice,
                3L,
                recipient.getId(),
                "NOT_MET",
                "MAJOR",
                EVIDENCE,
                base);
        AgentJob repair = captureCopiedPeople(newJob(), recipient);
        UUID latest = observe(repair, practice, 3L, recipient.getId(), "MET", null, EVIDENCE, base.plusSeconds(60));
        UUID abstention = observe(
                captureCopiedPeople(newJob(), recipient),
                practice,
                4L,
                recipient.getId(),
                "NOT_APPLICABLE",
                null,
                EVIDENCE,
                base.plusSeconds(60));
        // A later run this conversation may not use hides its claim entirely rather than exposing its earlier run.
        observe(
                captureCopiedPeople(newJob(), recipient),
                practice,
                5L,
                recipient.getId(),
                "NOT_MET",
                "MAJOR",
                EVIDENCE,
                base);
        observe(
                captureCopiedPeople(newJob(), recipient),
                practice,
                5L,
                recipient.getId(),
                "MET",
                null,
                NO_CITATIONS,
                base.plusSeconds(60));
        User colleague = userRepository.save(TestUserFactory.createUser(101L, "colleague", recipient.getProvider()));
        observe(repair, practice, 3L, colleague.getId(), "NOT_MET", "MAJOR", EVIDENCE, base.plusSeconds(120));
        Workspace own = workspace;
        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("obs-history-other"));
        observe(repair, practice("test-practice"), 3L, recipient.getId(), "NOT_MET", "MAJOR", EVIDENCE, base);
        workspace = own;

        JsonNode root = contribute();

        assertThat(ids(root.get("recentResults"))).containsExactly(latest.toString());
        assertThat(root.get("earlierResults"))
                .extracting(
                        o -> o.get("practiceResultId").asString(),
                        o -> o.get("severity").asString(),
                        o -> o.get("origin").asString())
                .containsExactly(tuple(earlier.toString(), "MAJOR", "LIVE"));
        assertThat(root.get("abstentions"))
                .extracting(
                        o -> o.get("practiceResultId").asString(),
                        o -> o.get("outcome").asString())
                .containsExactly(tuple(abstention.toString(), "NOT_APPLICABLE"));
        assertThat(root.get("summary").get("byOutcome").get("NOT_MET").asLong()).isZero();
        assertThat(root.get("summary").get("bySeverity").get("MAJOR").asLong()).isZero();
        assertThat(root.get("coverage").get("maxEntries").get("earlierResults").asInt())
                .isPositive();
    }

    @Test
    @DisplayName("rows this conversation may not use change nothing in the file")
    void shouldAnswerAlikeWhetherOrNotWithheldRowsExist() {
        Instant base = Instant.now().minus(1, ChronoUnit.DAYS);
        for (int i = 0; i < 60; i++) {
            observe(
                    captureCopiedPeople(newJob(), recipient),
                    practice,
                    i,
                    recipient.getId(),
                    "NOT_MET",
                    "MAJOR",
                    NO_CITATIONS,
                    base.plusSeconds(i));
            observe(
                    captureCopiedPeople(newJob(), recipient),
                    practice,
                    i,
                    recipient.getId(),
                    "UNDETERMINED",
                    null,
                    NO_CITATIONS,
                    base.plusSeconds(100 + i));
        }
        User newcomer = userRepository.save(TestUserFactory.createUser(102L, "newcomer", recipient.getProvider()));

        ObjectNode withheld = (ObjectNode) objectMapper.readTree(contributed(recipient.getId()));
        ObjectNode none = (ObjectNode) objectMapper.readTree(contributed(newcomer.getId()));
        for (ObjectNode payload : List.of(withheld, none)) {
            payload.remove("user");
            ((ObjectNode) payload.get("coverage")).remove("preparedAt");
        }

        assertThat(withheld).isEqualTo(none);
    }

    @Test
    @DisplayName("however long the stored text, the overview fits its bound with every row, and a detail fits its own")
    void shouldFitTheOverviewAndDetailBounds() {
        Instant base = Instant.now().minus(1, ChronoUnit.DAYS);
        String hostile = "\"\\\u0001x".repeat(2_500);
        UUID newest = null;
        for (int i = 0; i < 30; i++) {
            observe(
                    captureCopiedPeople(newJob(), recipient),
                    practice,
                    i,
                    recipient.getId(),
                    "NOT_MET",
                    "MAJOR",
                    EVIDENCE,
                    base.plusSeconds(i),
                    hostile);
            newest = observe(
                    captureCopiedPeople(newJob(), recipient),
                    practice,
                    i,
                    recipient.getId(),
                    "MET",
                    null,
                    EVIDENCE,
                    base.plusSeconds(100 + i),
                    hostile);
        }

        String file = contributed(recipient.getId());
        JsonNode root = objectMapper.readTree(file);

        assertThat(file).hasSizeLessThanOrEqualTo(ResultHistoryContentSource.OVERVIEW_MAX_CHARS);
        assertThat(root.get("recentResults")).hasSize(30);
        assertThat(ids(root.get("recentResults"))).first().isEqualTo(String.valueOf(newest));
        assertThat(root.get("earlierResults")).hasSize(20);
        assertThat(root.findValues("evidenceRationale")).isEmpty();
        assertThat(root.has("omittedForSize")).isFalse();

        ObjectNode detail = contentSource.inspect(
                workspace.getId(),
                recipient.getId(),
                UUID.fromString(ids(root.get("recentResults")).getFirst()));
        assertThat(objectMapper.writeValueAsString(detail))
                .hasSizeLessThanOrEqualTo(ResultHistoryContentSource.DETAIL_MAX_CHARS);
        assertThat(detail.get("result")
                        .get("evidence")
                        .get("citations")
                        .get(0)
                        .get("path")
                        .asString())
                .isEqualTo("source.json");
        assertThat(detail.get("result").get("evidenceRationale").asString()).contains("x");
    }

    @Test
    @DisplayName("a detail answers only for a result the overview lists, and alike for every other")
    void detailAnswersOnlyForListedResults() {
        Instant base = Instant.now().minus(1, ChronoUnit.DAYS);
        UUID earlier = observe(
                captureCopiedPeople(newJob(), recipient),
                practice,
                3L,
                recipient.getId(),
                "NOT_MET",
                "MAJOR",
                EVIDENCE,
                base);
        AgentJob repair = captureCopiedPeople(newJob(), recipient);
        UUID latest = observe(repair, practice, 3L, recipient.getId(), "MET", null, EVIDENCE, base.plusSeconds(60));
        // A later run this conversation may not use hides its claim's earlier run too.
        UUID orphaned = observe(
                captureCopiedPeople(newJob(), recipient),
                practice,
                5L,
                recipient.getId(),
                "NOT_MET",
                "MAJOR",
                EVIDENCE,
                base);
        UUID unusable = observe(
                captureCopiedPeople(newJob(), recipient),
                practice,
                5L,
                recipient.getId(),
                "MET",
                null,
                NO_CITATIONS,
                base.plusSeconds(60));
        User colleague = userRepository.save(TestUserFactory.createUser(101L, "colleague", recipient.getProvider()));
        UUID colleagues =
                observe(repair, practice, 3L, colleague.getId(), "NOT_MET", "MAJOR", EVIDENCE, base.plusSeconds(120));
        Workspace own = workspace;
        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("obs-history-detail-other"));
        UUID foreign =
                observe(repair, practice("test-practice"), 3L, recipient.getId(), "NOT_MET", "MAJOR", EVIDENCE, base);
        workspace = own;

        ObjectNode current = contentSource.inspect(workspace.getId(), recipient.getId(), latest);
        assertThat(current.get("list").asString()).isEqualTo("recentResults");
        assertThat(current.get("result").get("practiceRevisionId").asLong())
                .isEqualTo(Objects.requireNonNull(practice.getCurrentRevision()).getId());
        assertThat(current.get("result").get("criteria").asString()).isEqualTo(practice.getCriteria());
        assertThat(current.get("result").get("evidence").get("citations")).hasSize(1);
        assertThat(contentSource
                        .inspect(workspace.getId(), recipient.getId(), earlier)
                        .get("list")
                        .asString())
                .isEqualTo("earlierResults");

        ObjectNode unknown = notFound(contentSource.inspect(workspace.getId(), recipient.getId(), UUID.randomUUID()));
        for (UUID hidden : List.of(orphaned, unusable, colleagues, foreign)) {
            assertThat(notFound(contentSource.inspect(workspace.getId(), recipient.getId(), hidden)))
                    .as("result %s", hidden)
                    .isEqualTo(unknown);
        }
        // Another developer's own history does not answer for this developer's result either.
        assertThat(notFound(contentSource.inspect(workspace.getId(), colleague.getId(), latest)))
                .isEqualTo(unknown);
    }

    @Test
    @DisplayName("a conversation's detail carries the untrusted envelope, and none once its consent is revoked")
    void conversationDetailFollowsCurrentConsent() {
        long threadId = seedThread("C-detail", "400.0", ConsentState.ACTIVE);
        PracticeResult conversation = saveResult("occ-detail", "chat.conversation_thread", threadId);
        assertThat(ids(contribute().get("recentResults")))
                .contains(conversation.getId().toString());

        ObjectNode active = contentSource.inspect(workspace.getId(), recipient.getId(), conversation.getId());
        assertThat(active.get("_meta").get("trustLevel").asString()).isEqualTo("UNTRUSTED_EXTERNAL");
        assertThat(active.get("result").get("criteria").asString()).isEqualTo("Test description");
        assertThat(active.get("result").get("evidence").get("citations")).hasSize(1);

        slackMonitoredChannelRepository.findAll().stream()
                .filter(channel -> "C-detail".equals(channel.getSlackChannelId()))
                .forEach(channel -> {
                    channel.setConsentState(ConsentState.REVOKED);
                    slackMonitoredChannelRepository.save(channel);
                });

        ObjectNode revoked = contentSource.inspect(workspace.getId(), recipient.getId(), conversation.getId());
        assertThat(revoked.get("status").asString()).isEqualTo("NOT_FOUND");
        assertThat(revoked.has("_meta")).isFalse();
        assertThat(revoked.has("result")).isFalse();
    }

    private static ObjectNode notFound(ObjectNode detail) {
        assertThat(detail.get("status").asString()).isEqualTo("NOT_FOUND");
        detail.remove("readAt");
        return detail;
    }

    private JsonNode contribute() {
        return objectMapper.readTree(contributed(recipient.getId()));
    }

    private String contributed(long developerId) {
        Map<String, byte[]> files = new HashMap<>();
        contentSource.contribute(
                new ContextRequest.MentorChatRequest(workspace.getId(), developerId, UUID.randomUUID()), files);
        return new String(files.get(ResultHistoryContentSource.OUTPUT_KEY), StandardCharsets.UTF_8);
    }

    private static List<String> practiceResultIds(JsonNode root) {
        List<String> ids = new ArrayList<>();
        for (JsonNode node : root.get("recentResults")) {
            ids.add(node.get("practiceResultId").asString());
        }
        return ids;
    }

    private PracticeResult saveResult(String occurrenceKey, String artifactKind, long artifactId) {
        UUID id = UUID.randomUUID();
        practiceResultRepository.insertIfAbsent(
                id,
                occurrenceKey,
                job.getId(),
                job.getWorkspace().getId(),
                practice.getId(),
                Objects.requireNonNull(practice.getCurrentRevision()).getId(),
                artifactKind,
                artifactId,
                recipient.getId(),
                "Result title",
                "NOT_MET",
                "MAJOR",
                evidence(artifactKind),
                null,
                "test-scope",
                null,
                null,
                null,
                Instant.now(),
                "LIVE");
        return practiceResultRepository.findById(id).orElseThrow();
    }

    private static String evidence(String artifactKind) {
        String sourceKind =
                "chat.conversation_thread".equals(artifactKind) ? "slack.conversation.thread" : "scm.pull-request.core";
        return """
        {"citations":[{"sourceKind":"%s","artifactPath":"inputs/context/source.json",\
        "path":"source.json","startLine":1,"endLine":1,"quote":"evidence",\
        "quoteRedacted":false}]}
        """.formatted(sourceKind);
    }

    private static List<String> ids(JsonNode practiceResults) {
        List<String> ids = new ArrayList<>();
        practiceResults.forEach(node -> ids.add(node.get("practiceResultId").asString()));
        return ids;
    }

    private UUID observe(
            AgentJob review,
            Practice practice,
            long mergeRequestId,
            long aboutUserId,
            String outcome,
            @Nullable String severity,
            String evidence,
            Instant observedAt) {
        return observe(review, practice, mergeRequestId, aboutUserId, outcome, severity, evidence, observedAt, "");
    }

    /** One result on a merge request; with its explicit outcome. */
    private UUID observe(
            AgentJob review,
            Practice practice,
            long mergeRequestId,
            long aboutUserId,
            String outcome,
            @Nullable String severity,
            String evidence,
            Instant observedAt,
            String rationale) {
        UUID id = UUID.randomUUID();
        practiceResultRepository.insertIfAbsent(
                id,
                "occ-" + id,
                review.getId(),
                workspace.getId(),
                practice.getId(),
                Objects.requireNonNull(practice.getCurrentRevision()).getId(),
                ArtifactKinds.PULL_REQUEST.value(),
                mergeRequestId,
                aboutUserId,
                "Result " + id,
                outcome,
                severity,
                evidence,
                rationale,
                "test-scope",
                null,
                null,
                null,
                observedAt,
                "LIVE");
        return id;
    }

    private Practice practice(String slug) {
        Practice created = new Practice();
        created.setWorkspace(workspace);
        created.setSlug(slug);
        created.setName(slug);
        created.setCriteria("Criteria");
        created.setAutomatedReviewPolicy(PracticeTestEvidence.conversationThread());
        PracticeTestEvidence.configure(created, ScmSignals.PULL_REQUEST_OPENED);
        created = practiceRepository.saveAndFlush(created);
        created.setCurrentRevision(practiceRevisionRepository.save(new PracticeRevision(created, 1)));
        return practiceRepository.saveAndFlush(created);
    }
}
