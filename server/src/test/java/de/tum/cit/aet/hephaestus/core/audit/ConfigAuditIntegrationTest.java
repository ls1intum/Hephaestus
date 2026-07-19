package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigDTO;
import de.tum.cit.aet.hephaestus.agent.config.CreateAgentConfigRequestDTO;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditActorKind;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end coverage of the config audit trail: that producers actually write rows, that the rows
 * say the right thing, and that a workspace admin can never read another workspace's history.
 */
class ConfigAuditIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ConfigAuditEventRepository configAuditEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @WithAdminUser
    void practiceReviewPatchWritesExactlyOneRowNamingTheFieldThatChanged() {
        Workspace workspace = setupWorkspace("audit-a");

        patchPracticeReview(workspace, Map.of("cooldownMinutes", 45));

        List<ConfigAuditEvent> rows = configAuditEventRepository.findAll();
        assertThat(rows).hasSize(1);
        ConfigAuditEvent row = rows.getFirst();
        assertThat(row.getEntityType()).isEqualTo(ConfigAuditEntityType.PRACTICE_REVIEW_SETTINGS);
        assertThat(row.getWorkspaceId()).isEqualTo(workspace.getId());
        assertThat(row.changedKeyList()).containsExactly("cooldownMinutes");
        assertThat(row.getNewValue()).contains("45");
        // Untouched fields must survive into the snapshot, or the "from what to what" is a lie.
        // Present AND null, not absent: null is "inherit the fleet default", so a serializer that
        // dropped null keys would make clearing an override indistinguishable from never setting one.
        assertThat(row.getNewValue()).contains("\"skipDrafts\":null").contains("\"cooldownMinutes\":45");
        // Attribution through the real filter chain — the JWT -> CurrentAccount -> actor seam the
        // recorder's unit test can only simulate. USER, not SYSTEM: a signed-in admin did this. (The
        // id stays null here because the test harness mints a non-numeric subject; production subjects
        // are always the account id. ConfigAuditRecorderTest covers the resolved-id case.)
        assertThat(row.getActorKind()).isEqualTo(ConfigAuditActorKind.USER);
    }

    @Test
    @WithAdminUser
    void togglingAFeatureFlagIsRecorded() {
        // Workspace-administration coverage the trail gained: enabling/disabling a feature is an admin
        // action with accountability value, now recorded alongside AI config.
        Workspace workspace = setupWorkspace("audit-features");

        webTestClient
            .patch()
            .uri("/workspaces/{slug}/features", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("mentorEnabled", true))
            .exchange()
            .expectStatus()
            .isOk();

        ConfigAuditEvent row = configAuditEventRepository
            .findAll()
            .stream()
            .filter(e -> e.getEntityType() == ConfigAuditEntityType.WORKSPACE_FEATURES)
            .findFirst()
            .orElseThrow();
        assertThat(row.getWorkspaceId()).isEqualTo(workspace.getId());
        assertThat(row.changedKeyList()).contains("mentorEnabled");
        assertThat(row.getNewValue()).contains("\"mentorEnabled\":true");
    }

    @Test
    @WithAdminUser
    void anIdempotentRepeatOfTheSamePatchAddsNoSecondRow() {
        // Otherwise a settings page that re-submits its whole form buries real changes in noise.
        Workspace workspace = setupWorkspace("audit-noop");

        patchPracticeReview(workspace, Map.of("cooldownMinutes", 45));
        patchPracticeReview(workspace, Map.of("cooldownMinutes", 45));

        assertThat(configAuditEventRepository.findAll()).hasSize(1);
    }

    @Test
    @WithAdminUser
    void clearingAnOverrideBackToInheritIsRecorded() {
        // The null-versus-absent case: under NON_NULL serialization this change would vanish.
        Workspace workspace = setupWorkspace("audit-reset");
        patchPracticeReview(workspace, Map.of("cooldownMinutes", 45));

        patchPracticeReview(workspace, Map.of("reset", List.of("COOLDOWN_MINUTES")));

        List<ConfigAuditEvent> rows = configAuditEventRepository
            .findAll()
            .stream()
            .sorted(java.util.Comparator.comparing(ConfigAuditEvent::getId))
            .toList();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).changedKeyList()).containsExactly("cooldownMinutes");
        assertThat(rows.get(1).getNewValue()).contains("\"cooldownMinutes\":null");
    }

    @Test
    @WithAdminUser
    void agentConfigCreateIsAuditedWithoutTheApiKey() {
        Workspace workspace = setupWorkspace("audit-cfg");

        createConfig(workspace, "primary");

        ConfigAuditEvent row = configAuditEventRepository
            .findAll()
            .stream()
            .filter(e -> e.getEntityType() == ConfigAuditEntityType.AGENT_CONFIG)
            .findFirst()
            .orElseThrow();
        assertThat(row.getOldValue()).isNull();
        assertThat(row.getNewValue()).contains("\"llmApiKeySet\":true").doesNotContain("sk-test-secret-key-123");
    }

    @Test
    @WithAdminUser
    void workspaceAdminSeesOnlyTheirOwnWorkspacesHistory() {
        Workspace mine = setupWorkspace("audit-mine");
        Workspace theirs = setupWorkspace("audit-theirs");
        patchPracticeReview(mine, Map.of("cooldownMinutes", 45));
        patchPracticeReview(theirs, Map.of("cooldownMinutes", 15));

        webTestClient
            .get()
            .uri("/workspaces/{slug}/config-audit", mine.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content.length()")
            .isEqualTo(1)
            .jsonPath("$.content[0].workspaceId")
            .isEqualTo(mine.getId());
    }

    @Test
    @WithAdminUser
    void filteringByChangedKeyNarrowsToOneControl() {
        // The per-control History contract (#1357). Without changed_keys this is unanswerable
        // server-side, and a client cannot filter after paging.
        Workspace workspace = setupWorkspace("audit-filter");
        patchPracticeReview(workspace, Map.of("cooldownMinutes", 45));
        patchPracticeReview(workspace, Map.of("skipDrafts", true));

        webTestClient
            .get()
            .uri(uri ->
                uri
                    .path("/workspaces/{slug}/config-audit")
                    .queryParam("changedKey", "skipDrafts")
                    .build(workspace.getWorkspaceSlug())
            )
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content.length()")
            .isEqualTo(1)
            .jsonPath("$.content[0].changedKeys[0]")
            .isEqualTo("skipDrafts");
    }

    @Test
    @WithAdminUser
    void instanceAdminSeesEveryWorkspaceAndCanNarrowToOne() {
        // First and only execution of findForAdmin: its SpEL binds and the CAST(:workspaceId AS bigint)
        // null-branch are compile-clean but runtime-fragile, so nothing else proves they work.
        Workspace a = setupWorkspace("audit-inst-a");
        Workspace b = setupWorkspace("audit-inst-b");
        patchPracticeReview(a, Map.of("cooldownMinutes", 45));
        patchPracticeReview(b, Map.of("cooldownMinutes", 15));

        webTestClient
            .get()
            .uri("/admin/config-audit")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content.length()")
            .isEqualTo(2);

        webTestClient
            .get()
            .uri(uri -> uri.path("/admin/config-audit").queryParam("workspaceId", b.getId()).build())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content.length()")
            .isEqualTo(1)
            .jsonPath("$.content[0].workspaceId")
            .isEqualTo(b.getId());
    }

    @Test
    @WithMentorUser
    void aNonInstanceAdminIsRefusedTheCrossWorkspaceView() {
        // The one endpoint in this feature that spans tenants; app_admin is the only thing between a
        // signed-in user and every workspace's configuration history.
        webTestClient
            .get()
            .uri("/admin/config-audit")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * Each predicate in FILTER_PREDICATES, matching and not matching. Every dimension carries both
     * directions because a predicate transposed so that it matches NOTHING passes every zero case on
     * its own — and a failure has to name which predicate broke, which one fat test cannot.
     */
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> filterCases() {
        String future = Instant.now().plusSeconds(60).toString();
        String past = Instant.now().minusSeconds(60).toString();
        return java.util.stream.Stream.of(
            org.junit.jupiter.params.provider.Arguments.of("entityType matches", "entityType", "AGENT_CONFIG", 1),
            org.junit.jupiter.params.provider.Arguments.of(
                "entityType matches the other kind",
                "entityType",
                "PRACTICE_REVIEW_SETTINGS",
                1
            ),
            org.junit.jupiter.params.provider.Arguments.of("action matches", "action", "CREATED", 1),
            org.junit.jupiter.params.provider.Arguments.of("action excludes", "action", "DELETED", 0),
            org.junit.jupiter.params.provider.Arguments.of("actorId excludes", "actorId", "999999", 0),
            org.junit.jupiter.params.provider.Arguments.of("from excludes the past", "from", future, 0),
            org.junit.jupiter.params.provider.Arguments.of("from includes the past", "from", past, 2),
            org.junit.jupiter.params.provider.Arguments.of("to excludes the present", "to", past, 0),
            org.junit.jupiter.params.provider.Arguments.of("to includes the present", "to", future, 2)
        );
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.MethodSource("filterCases")
    @WithAdminUser
    void eachFilterPredicateNarrowsIndependently(String name, String param, String value, int expected) {
        Workspace workspace = setupWorkspace("audit-matrix");
        patchPracticeReview(workspace, Map.of("cooldownMinutes", 45));
        createConfig(workspace, "primary");

        assertFilterYields(workspace, uri -> uri.queryParam(param, value), expected);
    }

    @Test
    @WithAdminUser
    void repeatingAFilterParameterWidensItRatherThanReplacingIt() {
        // The question a change trail is opened for is usually disjunctive ("did anyone touch either of
        // these?"), so repeated values must union. The CSV the predicate builds from them is split by
        // string_to_array in SQL, which a single-value test cannot distinguish from plain equality.
        Workspace workspace = setupWorkspace("audit-multi");
        patchPracticeReview(workspace, Map.of("cooldownMinutes", 45));
        createConfig(workspace, "primary");

        assertFilterYields(
            workspace,
            uri -> uri.queryParam("entityType", "AGENT_CONFIG").queryParam("entityType", "PRACTICE_REVIEW_SETTINGS"),
            2
        );
        assertFilterYields(workspace, uri -> uri.queryParam("action", "CREATED").queryParam("action", "DELETED"), 1);
    }

    @Test
    @WithAdminUser
    void newestRowsComeFirstEvenWhenTwoShareAnInstant() {
        // A settings form submitting twice in one second is the normal case, so the id tie-break in
        // ORDER BY occurred_at DESC, id DESC is what makes paging deterministic at all.
        Workspace workspace = setupWorkspace("audit-order");
        patchPracticeReview(workspace, Map.of("cooldownMinutes", 45));
        patchPracticeReview(workspace, Map.of("cooldownMinutes", 46));

        webTestClient
            .get()
            .uri("/workspaces/{slug}/config-audit", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content[0].newValue")
            .value(org.hamcrest.Matchers.containsString("46"))
            .jsonPath("$.content[1].newValue")
            .value(org.hamcrest.Matchers.containsString("45"));
    }

    @Test
    @WithAdminUser
    void entityIdWithoutEntityTypeIsRejected() {
        // Id spaces are per-type, so an unqualified id would match across types by accident.
        Workspace workspace = setupWorkspace("audit-badfilter");

        webTestClient
            .get()
            .uri(uri ->
                uri
                    .path("/workspaces/{slug}/config-audit")
                    .queryParam("entityId", "1")
                    .build(workspace.getWorkspaceSlug())
            )
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    @WithAdminUser
    @Transactional
    void anUnscopedReadOfTheAuditTableIsCaughtByTenancyEnforcement() {
        // Pins that the table is registered as workspace-scoped (absent from GLOBAL_TABLES) and that the
        // Pins that the table is registered workspace-scoped; isolation itself is carried by the gate and
        // by findForWorkspace, which the two tests above cover.
        assertThatThrownBy(() ->
            entityManager.createNativeQuery("SELECT * FROM config_audit_event", ConfigAuditEvent.class).getResultList()
        )
            .isInstanceOf(de.tum.cit.aet.hephaestus.core.tenancy.TenancyViolationException.class)
            .hasMessageContaining("config_audit_event");
    }

    private void assertFilterYields(
        Workspace workspace,
        java.util.function.UnaryOperator<org.springframework.web.util.UriBuilder> query,
        int expected
    ) {
        webTestClient
            .get()
            .uri(uri -> query.apply(uri.path("/workspaces/{slug}/config-audit")).build(workspace.getWorkspaceSlug()))
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content.length()")
            .isEqualTo(expected);
    }

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "Audit Workspace", slug + "-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private void patchPracticeReview(Workspace workspace, Map<String, Object> body) {
        webTestClient
            .patch()
            .uri("/workspaces/{slug}/ai-settings/practice-review", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isOk();
    }

    private AgentConfigDTO createConfig(Workspace workspace, String name) {
        return webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                CreateAgentConfigRequestDTO.builder()
                    .name(name)
                    .enabled(true)
                    .modelName("claude-sonnet-4-20250514")
                    .llmApiKey("sk-test-secret-key-123")
                    .llmProvider(LlmProvider.ANTHROPIC)
                    .timeoutSeconds(300)
                    .maxConcurrentJobs(2)
                    .allowInternet(false)
                    .build()
            )
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(AgentConfigDTO.class)
            .returnResult()
            .getResponseBody();
    }
}
