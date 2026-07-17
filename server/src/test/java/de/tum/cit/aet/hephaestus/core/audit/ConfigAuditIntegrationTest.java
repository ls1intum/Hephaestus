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
        assertThat(row.getNewValue()).contains("skipDrafts");
        // Attribution through the real filter chain — the JWT -> CurrentAccount -> actor seam the
        // recorder's unit test can only simulate. USER, not SYSTEM: a signed-in admin did this. (The
        // id stays null here because the test harness mints a non-numeric subject; production subjects
        // are always the account id. ConfigAuditRecorderTest covers the resolved-id case.)
        assertThat(row.getActorKind()).isEqualTo(ConfigAuditActorKind.USER);
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

    @Test
    @WithAdminUser
    void filtersNarrowIndependently() {
        // Exercises the entityType / action / actorId / from-to predicates, none of which any other
        // test executes — a transposed column name in FILTER_PREDICATES would otherwise ship.
        Workspace workspace = setupWorkspace("audit-matrix");
        patchPracticeReview(workspace, Map.of("cooldownMinutes", 45));
        createConfig(workspace, "primary");

        assertFilterYields(workspace, uri -> uri.queryParam("entityType", "AGENT_CONFIG"), 1);
        assertFilterYields(workspace, uri -> uri.queryParam("entityType", "PRACTICE_REVIEW_SETTINGS"), 1);
        assertFilterYields(workspace, uri -> uri.queryParam("action", "CREATED"), 1);
        assertFilterYields(workspace, uri -> uri.queryParam("action", "DELETED"), 0);
        assertFilterYields(workspace, uri -> uri.queryParam("actorId", 999_999), 0);
        assertFilterYields(workspace, uri -> uri.queryParam("from", Instant.now().plusSeconds(60).toString()), 0);
        assertFilterYields(workspace, uri -> uri.queryParam("to", Instant.now().minusSeconds(60).toString()), 0);
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
        // inspector is armed for it under the test profile's enforcement=throw. It does NOT prove reads
        // are safe: the inspector only checks that the literal `workspace_id` appears somewhere in the
        // statement. Isolation is carried by @RequireAtLeastWorkspaceAdmin plus findForWorkspace's
        // required predicate — this is a backstop against a query that drops the column entirely.
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
