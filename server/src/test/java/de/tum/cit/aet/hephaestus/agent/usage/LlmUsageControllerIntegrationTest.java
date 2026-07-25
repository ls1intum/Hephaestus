package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.WorkspaceLlmUsageReportDTO;
import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRate;
import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRateRepository;
import de.tum.cit.aet.hephaestus.core.audit.ConfigAuditEvent;
import de.tum.cit.aet.hephaestus.core.audit.ConfigAuditEventRepository;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * {@code GET /workspaces/{slug}/llm-usage} — the workspace-admin month rollup. Verifies the
 * month-window filter, by-job-type and by-day grouping, the over-budget flag, and that a plain
 * member is 403'd.
 */
@Tag("integration")
class LlmUsageControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private LlmUsageEventRepository usageRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ConfigAuditEventRepository configAuditEventRepository;

    @Autowired
    private FxRateRepository fxRateRepository;

    private static final YearMonth CURRENT = YearMonth.now(ZoneOffset.UTC);
    private static final YearMonth PREVIOUS = CURRENT.minusMonths(1);

    private Workspace setupWorkspaceWithAdmin(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "Usage " + slug, slug + "-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private void seedEvent(Workspace workspace, LlmUsageJobType type, String cost, YearMonth month, int day) {
        LlmUsageEvent event = new LlmUsageEvent();
        event.setId(UUID.randomUUID());
        event.setWorkspace(workspace);
        event.setJobType(type);
        event.setSourceType(sourceType(type));
        event.setSourceId(UUID.randomUUID());
        event.setModel("claude-sonnet-5");
        event.setInputTokens(100);
        event.setOutputTokens(20);
        event.setTotalCalls(2);
        event.setCostUsd(new BigDecimal(cost));
        // Budgeted spend only counts PRICED + INSTANCE-funded rows (#1368 slice 6) — both are the
        // entity defaults, but set them explicitly so this fixture keeps meaning that if the
        // defaults ever change.
        event.setPricingState(PricingState.PRICED);
        event.setFundingSource(FundingSource.INSTANCE);
        event.setOccurredAt(month.atDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600));
        usageRepository.save(event);
    }

    private void seedByoEvent(Workspace workspace, LlmUsageJobType type, String cost, YearMonth month, int day) {
        LlmUsageEvent event = new LlmUsageEvent();
        event.setId(UUID.randomUUID());
        event.setWorkspace(workspace);
        event.setJobType(type);
        event.setSourceType(sourceType(type));
        event.setSourceId(UUID.randomUUID());
        event.setModel("byo-model");
        event.setCostUsd(new BigDecimal(cost));
        event.setPricingState(PricingState.PRICED);
        event.setFundingSource(FundingSource.WORKSPACE);
        event.setOccurredAt(month.atDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600));
        usageRepository.save(event);
    }

    private void seedUnpricedEvent(Workspace workspace, LlmUsageJobType type, YearMonth month, int day) {
        LlmUsageEvent event = new LlmUsageEvent();
        event.setId(UUID.randomUUID());
        event.setWorkspace(workspace);
        event.setJobType(type);
        event.setSourceType(sourceType(type));
        event.setSourceId(UUID.randomUUID());
        event.setModel("no-price-model");
        event.setCostUsd(null);
        event.setPricingState(PricingState.UNPRICED);
        event.setFundingSource(FundingSource.INSTANCE);
        event.setOccurredAt(month.atDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600));
        usageRepository.save(event);
    }

    /** An own-provider call whose model has no price — the workspace admin's blind spot to clear. */
    private void seedUnpricedByoEvent(Workspace workspace, LlmUsageJobType type, YearMonth month, int day) {
        LlmUsageEvent event = new LlmUsageEvent();
        event.setId(UUID.randomUUID());
        event.setWorkspace(workspace);
        event.setJobType(type);
        event.setSourceType(sourceType(type));
        event.setSourceId(UUID.randomUUID());
        event.setModel("byo-no-price-model");
        event.setCostUsd(null);
        event.setPricingState(PricingState.UNPRICED);
        event.setFundingSource(FundingSource.WORKSPACE);
        event.setOccurredAt(month.atDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600));
        usageRepository.save(event);
    }

    private LlmUsageSourceType sourceType(LlmUsageJobType type) {
        return type == LlmUsageJobType.MENTOR_TURN ? LlmUsageSourceType.MENTOR_TURN : LlmUsageSourceType.AGENT_JOB;
    }

    @Test
    @WithAdminUser
    void byJobTypeAndByDaySplitPricedByoAndUnpricedSeparately() {
        // One job type, one day, mixing all three so a blind SUM(cost_usd) would either merge BYO
        // into the budgeted figure or silently drop the unpriced event's visibility (#1368 slice 6).
        Workspace workspace = setupWorkspaceWithAdmin("usage-breakdown");
        seedEvent(workspace, LlmUsageJobType.PULL_REQUEST_REVIEW, "2.00", CURRENT, 5);
        seedByoEvent(workspace, LlmUsageJobType.PULL_REQUEST_REVIEW, "50.00", CURRENT, 5);
        seedUnpricedEvent(workspace, LlmUsageJobType.PULL_REQUEST_REVIEW, CURRENT, 5);

        WorkspaceLlmUsageReportDTO report = webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceLlmUsageReportDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(report).isNotNull();
        var prReviews = report
            .byJobType()
            .stream()
            .filter(t -> t.jobType() == LlmUsageJobType.PULL_REQUEST_REVIEW)
            .findFirst()
            .orElseThrow();
        assertThat(prReviews.pricedTotalCostUsd()).isEqualByComparingTo("2.00");
        assertThat(prReviews.byoTotalCostUsd()).isEqualByComparingTo("50.00");
        assertThat(prReviews.unpricedEventCount()).isEqualTo(1);
        assertThat(prReviews.events()).isEqualTo(3);

        assertThat(report.byDay()).hasSize(1);
        var day = report.byDay().getFirst();
        assertThat(day.pricedTotalCostUsd()).isEqualByComparingTo("2.00");
        assertThat(day.byoTotalCostUsd()).isEqualByComparingTo("50.00");
        assertThat(day.unpricedEventCount()).isEqualTo(1);
        assertThat(day.events()).isEqualTo(3);
    }

    @Test
    @WithAdminUser
    void reportRollsUpTheRequestedMonthByJobTypeAndDay() {
        Workspace workspace = setupWorkspaceWithAdmin("usage-report");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("5.00"));
        workspaceRepository.save(workspace);
        seedEvent(workspace, LlmUsageJobType.PULL_REQUEST_REVIEW, "1.25", PREVIOUS, 3);
        seedEvent(workspace, LlmUsageJobType.PULL_REQUEST_REVIEW, "0.75", PREVIOUS, 3);
        seedEvent(workspace, LlmUsageJobType.MENTOR_TURN, "0.50", PREVIOUS, 12);
        seedEvent(workspace, LlmUsageJobType.ISSUE_REVIEW, "9.99", CURRENT, 1); // outside requested month

        WorkspaceLlmUsageReportDTO report = webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage?month={month}", workspace.getWorkspaceSlug(), PREVIOUS.toString())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceLlmUsageReportDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(report).isNotNull();
        assertThat(report.month()).isEqualTo(PREVIOUS.toString());
        assertThat(report.instanceMonthlyBudgetUsd()).isEqualByComparingTo("5.00");
        assertThat(report.pricedTotalCostUsd()).isEqualByComparingTo("2.50");
        assertThat(report.byoTotalCostUsd()).isEqualByComparingTo("0");
        assertThat(report.instanceBudgetVerdict()).isEqualTo(LlmBudgetVerdict.WITHIN);
        assertThat(report.byJobType()).hasSize(2);
        var prReviews = report
            .byJobType()
            .stream()
            .filter(t -> t.jobType() == LlmUsageJobType.PULL_REQUEST_REVIEW)
            .findFirst()
            .orElseThrow();
        assertThat(prReviews.pricedTotalCostUsd()).isEqualByComparingTo("2.00");
        assertThat(prReviews.byoTotalCostUsd()).isEqualByComparingTo("0");
        assertThat(prReviews.unpricedEventCount()).isEqualTo(0);
        assertThat(prReviews.inputTokens()).isEqualTo(200);
        assertThat(prReviews.totalCalls()).isEqualTo(4);
        assertThat(prReviews.events()).isEqualTo(2);
        assertThat(report.byDay()).hasSize(2);
        assertThat(report.byDay().getFirst().day()).isEqualTo(PREVIOUS.atDay(3));
        assertThat(report.byDay().getFirst().pricedTotalCostUsd()).isEqualByComparingTo("2.00");
    }

    @Test
    @WithAdminUser
    void verdictFlipsToExhaustedWhenSpendReachesTheCap() {
        Workspace workspace = setupWorkspaceWithAdmin("usage-over");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("1.00"));
        workspaceRepository.save(workspace);
        seedEvent(workspace, LlmUsageJobType.MENTOR_TURN, "1.00", CURRENT, 1);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.instanceBudgetVerdict")
            .isEqualTo("EXHAUSTED")
            .jsonPath("$.instanceFundedPaused")
            .isEqualTo(true)
            // The other purse is uncapped and untouched: shared-model exhaustion never pauses work
            // the workspace pays for itself.
            .jsonPath("$.byoPaused")
            .isEqualTo(false);
    }

    /**
     * #1368: {@code instanceFundedPaused} is the webapp's only reliable signal that new shared-model
     * work is currently paused. An unverifiable month on a capped workspace pauses — a cap whose true
     * spend can't be confirmed is treated as reached.
     */
    @Test
    @WithAdminUser
    void usagePausedIsTrueOnAnUnverifiableMonthForACappedWorkspace() {
        Workspace workspace = setupWorkspaceWithAdmin("usage-unverifiable-capped");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("100.00"));
        workspaceRepository.save(workspace);
        seedUnpricedEvent(workspace, LlmUsageJobType.MENTOR_TURN, CURRENT, 1);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.instanceBudgetVerdict")
            .isEqualTo("UNVERIFIABLE")
            .jsonPath("$.instanceFundedPaused")
            .isEqualTo(true);
    }

    /**
     * #1368: the report carries both caps, judged separately. An exhausted own-provider cap pauses
     * own-provider work only — the shared-model purse is a different person's money and stays open.
     */
    @Test
    @WithAdminUser
    void theTwoPursesAreReportedAndPausedIndependently() {
        Workspace workspace = setupWorkspaceWithAdmin("usage-two-purses");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("100.00"));
        workspace.setMonthlyByoLlmBudgetUsd(new BigDecimal("10.00"));
        workspaceRepository.save(workspace);
        seedEvent(workspace, LlmUsageJobType.MENTOR_TURN, "1.00", CURRENT, 1);
        seedByoEvent(workspace, LlmUsageJobType.MENTOR_TURN, "10.00", CURRENT, 1);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.instanceMonthlyBudgetUsd")
            .isEqualTo(100.0)
            .jsonPath("$.byoMonthlyBudgetUsd")
            .isEqualTo(10.0)
            .jsonPath("$.instanceBudgetVerdict")
            .isEqualTo("WITHIN")
            .jsonPath("$.byoBudgetVerdict")
            .isEqualTo("EXHAUSTED")
            .jsonPath("$.instanceFundedPaused")
            .isEqualTo(false)
            .jsonPath("$.byoPaused")
            .isEqualTo(true);
    }

    /**
     * The blind-spot rule end to end: an unpriced OWN-PROVIDER event is the workspace admin's to
     * clear, so it may pause own-provider work but must leave the shared-model purse alone.
     */
    @Test
    @WithAdminUser
    void anUnpricedOwnProviderEventPausesOnlyTheOwnProviderPurse() {
        Workspace workspace = setupWorkspaceWithAdmin("usage-byo-unpriced");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("100.00"));
        workspace.setMonthlyByoLlmBudgetUsd(new BigDecimal("100.00"));
        workspaceRepository.save(workspace);
        seedUnpricedByoEvent(workspace, LlmUsageJobType.MENTOR_TURN, CURRENT, 1);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.instanceBudgetVerdict")
            .isEqualTo("WITHIN")
            .jsonPath("$.instanceFundedPaused")
            .isEqualTo(false)
            .jsonPath("$.byoBudgetVerdict")
            .isEqualTo("UNVERIFIABLE")
            .jsonPath("$.byoPaused")
            .isEqualTo(true);
    }

    @Test
    @WithAdminUser
    void usagePausedIsFalseForAPastMonthEvenIfTheCurrentMonthIsExhausted() {
        // The paused flags are a LIVE gate (always evaluated against "now"), never scoped to the
        // requested report month — a closed past month can't still be "pausing" new work.
        Workspace workspace = setupWorkspaceWithAdmin("usage-paused-past-month");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("1.00"));
        workspaceRepository.save(workspace);
        seedEvent(workspace, LlmUsageJobType.MENTOR_TURN, "1.00", CURRENT, 1); // exhausts the CURRENT month
        seedEvent(workspace, LlmUsageJobType.MENTOR_TURN, "0.10", PREVIOUS, 1); // PREVIOUS stays well under cap

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage?month={month}", workspace.getWorkspaceSlug(), PREVIOUS.toString())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.instanceFundedPaused")
            .isEqualTo(false);
    }

    @Test
    @WithAdminUser
    void invalidMonthParamIsRejectedWith400() {
        Workspace workspace = setupWorkspaceWithAdmin("usage-badmonth");

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage?month=07-2026", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    @WithAdminUser
    void instanceAdminCanReadAWorkspaceReportWithoutMembership() {
        Workspace workspace = createWorkspace(
            "usage-instance-admin",
            "Usage instance admin",
            "usage-instance-admin-org",
            AccountType.ORG,
            persistUser("usage-instance-admin-owner")
        );
        seedEvent(workspace, LlmUsageJobType.MENTOR_TURN, "1.25", CURRENT, 1);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.pricedTotalCostUsd")
            .isEqualTo(1.25);
    }

    @Test
    @WithMentorUser
    void workspaceAdminCannotReadAnotherWorkspacesReport() {
        User admin = persistUser("mentor");
        Workspace ownWorkspace = createWorkspace(
            "usage-own-admin",
            "Usage own admin",
            "usage-own-admin-org",
            AccountType.ORG,
            admin
        );
        ensureWorkspaceMembership(ownWorkspace, admin, WorkspaceMembership.WorkspaceRole.ADMIN);
        Workspace otherWorkspace = createWorkspace(
            "usage-other-admin",
            "Usage other admin",
            "usage-other-admin-org",
            AccountType.ORG,
            persistUser("usage-other-admin-owner")
        );

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", otherWorkspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", ownWorkspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk();
    }

    /**
     * {@code PUT /workspaces/{slug}/llm-usage/byo-budget} (#1368) — the workspace admin's own cap on
     * the money the workspace itself pays. It is the exact mirror of the instance admin's cap, with
     * the ownership boundary the whole two-purse design rests on: setting it can only restrict the
     * workspace's own spending, and it grants no reach whatsoever over the shared-model budget.
     */
    @Test
    @WithAdminUser
    void workspaceAdminSetsAndClearsTheirOwnProviderCap() {
        Workspace workspace = setupWorkspaceWithAdmin("byo-budget-set");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/llm-usage/byo-budget", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyByoLlmBudgetUsd", "25.00"))
            .exchange()
            .expectStatus()
            .isNoContent();
        assertThat(
            workspaceRepository.findById(workspace.getId()).orElseThrow().getMonthlyByoLlmBudgetUsd()
        ).isEqualByComparingTo("25.00");

        // An empty body clears the cap back to uncapped — same shape as the instance-admin endpoint.
        webTestClient
            .put()
            .uri("/workspaces/{slug}/llm-usage/byo-budget", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of())
            .exchange()
            .expectStatus()
            .isNoContent();
        assertThat(workspaceRepository.findById(workspace.getId()).orElseThrow().getMonthlyByoLlmBudgetUsd()).isNull();
    }

    @Test
    @WithAdminUser
    void settingTheOwnProviderCapNeverTouchesTheSharedModelBudget() {
        Workspace workspace = setupWorkspaceWithAdmin("byo-budget-isolated");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("7.00"));
        workspaceRepository.save(workspace);

        webTestClient
            .put()
            .uri("/workspaces/{slug}/llm-usage/byo-budget", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyByoLlmBudgetUsd", "0.00"))
            .exchange()
            .expectStatus()
            .isNoContent();

        Workspace reloaded = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(reloaded.getMonthlyByoLlmBudgetUsd()).isEqualByComparingTo("0.00");
        assertThat(reloaded.getMonthlyLlmBudgetUsd()).isEqualByComparingTo("7.00");
    }

    @Test
    @WithAdminUser
    void settingTheOwnProviderCapWritesAnAuditRow() {
        Workspace workspace = setupWorkspaceWithAdmin("byo-budget-audit");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/llm-usage/byo-budget", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyByoLlmBudgetUsd", "12.50"))
            .exchange()
            .expectStatus()
            .isNoContent();

        ConfigAuditEvent row = configAuditEventRepository
            .findAll()
            .stream()
            .filter(e -> e.getEntityType() == ConfigAuditEntityType.WORKSPACE_BYO_LLM_BUDGET)
            .filter(e -> workspace.getId().equals(e.getWorkspaceId()))
            .findFirst()
            .orElseThrow();
        assertThat(row.changedKeyList()).contains("monthlyByoLlmBudgetUsd");
        assertThat(row.getNewValue()).contains("12.5");
    }

    @Test
    @WithAdminUser
    void anOwnProviderCapWithMoreThanTwoDecimalsIsRejectedWith400() {
        Workspace workspace = setupWorkspaceWithAdmin("byo-budget-precision");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/llm-usage/byo-budget", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyByoLlmBudgetUsd", "1.234"))
            .exchange()
            .expectStatus()
            .isBadRequest();
        assertThat(workspaceRepository.findById(workspace.getId()).orElseThrow().getMonthlyByoLlmBudgetUsd()).isNull();
    }

    @Test
    @WithAdminUser
    void aNegativeOwnProviderCapIsRejectedWith400() {
        Workspace workspace = setupWorkspaceWithAdmin("byo-budget-negative");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/llm-usage/byo-budget", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyByoLlmBudgetUsd", "-1.00"))
            .exchange()
            .expectStatus()
            .isBadRequest();
        assertThat(workspaceRepository.findById(workspace.getId()).orElseThrow().getMonthlyByoLlmBudgetUsd()).isNull();
    }

    @Test
    @WithMentorUser
    void aPlainMemberCannotSetTheOwnProviderCap() {
        User owner = persistUser("byo-budget-member-owner");
        Workspace workspace = createWorkspace(
            "byo-budget-member",
            "Byo budget member",
            "byo-budget-member-org",
            AccountType.ORG,
            owner
        );
        ensureWorkspaceMembership(workspace, persistUser("mentor"), WorkspaceMembership.WorkspaceRole.MEMBER);

        webTestClient
            .put()
            .uri("/workspaces/{slug}/llm-usage/byo-budget", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyByoLlmBudgetUsd", "25.00"))
            .exchange()
            .expectStatus()
            .isForbidden();
        assertThat(workspaceRepository.findById(workspace.getId()).orElseThrow().getMonthlyByoLlmBudgetUsd()).isNull();
    }

    @Test
    void anAnonymousCallerCannotSetTheOwnProviderCap() {
        Workspace workspace = setupWorkspaceWithAdmin("byo-budget-anon");
        // A valid CSRF token so the refusal is provably about authentication, not the CSRF filter
        // (which would answer 403 first and hide whether the endpoint is guarded at all).
        String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);

        webTestClient
            .put()
            .uri("/workspaces/{slug}/llm-usage/byo-budget", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCsrf(csrf))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyByoLlmBudgetUsd", "25.00"))
            .exchange()
            .expectStatus()
            .isUnauthorized();
        assertThat(workspaceRepository.findById(workspace.getId()).orElseThrow().getMonthlyByoLlmBudgetUsd()).isNull();
    }

    /**
     * The ownership boundary, stated as a test: owning the own-provider cap grants no reach over the
     * instance's cap. A workspace admin who is not an instance admin is refused there, so nothing
     * they can do loosens the host's backstop.
     */
    @Test
    @WithMentorUser
    void aWorkspaceAdminCannotTouchTheInstanceCap() {
        User admin = persistUser("mentor");
        Workspace workspace = createWorkspace(
            "byo-budget-boundary",
            "Byo budget boundary",
            "byo-budget-boundary-org",
            AccountType.ORG,
            admin
        );
        ensureWorkspaceMembership(workspace, admin, WorkspaceMembership.WorkspaceRole.ADMIN);

        // Allowed on their own cap …
        webTestClient
            .put()
            .uri("/workspaces/{slug}/llm-usage/byo-budget", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyByoLlmBudgetUsd", "25.00"))
            .exchange()
            .expectStatus()
            .isNoContent();

        // … and refused on the instance's.
        webTestClient
            .put()
            .uri("/admin/workspaces/{id}/llm-budget", workspace.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyLlmBudgetUsd", "999.00"))
            .exchange()
            .expectStatus()
            .isForbidden();
        assertThat(workspaceRepository.findById(workspace.getId()).orElseThrow().getMonthlyLlmBudgetUsd()).isNull();
    }

    /**
     * Zero-regression guard for the display-currency feature (#1368). An instance that has not set
     * {@code hephaestus.llm.display-currency} — the default, and the overwhelming majority — must get
     * back exactly the response it got before the feature existed. The fixture stores a perfectly
     * fresh rate first, so the only thing that can be suppressing {@code fx} is the unset property.
     */
    @Test
    @WithAdminUser
    void reportOmitsFxEntirelyWhenNoDisplayCurrencyConfigured() {
        Workspace workspace = setupWorkspaceWithAdmin("usage-no-fx");
        seedEvent(workspace, LlmUsageJobType.PULL_REQUEST_REVIEW, "1.00", CURRENT, 5);
        FxRate rate = new FxRate();
        rate.setRateDate(LocalDate.now(ZoneOffset.UTC));
        rate.setUsdPerEur(new BigDecimal("1.1377"));
        rate.setFetchedAt(Instant.now());
        fxRateRepository.save(rate);

        byte[] body = webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.fx")
            .doesNotExist()
            .jsonPath("$.pricedTotalCostUsd")
            .isEqualTo(1.00)
            .returnResult()
            .getResponseBody();

        // Not even as an explicit null: the key is absent from the wire bytes.
        assertThat(new String(body, StandardCharsets.UTF_8)).doesNotContain("\"fx\"");
    }

    @Test
    @WithMentorUser
    void plainMemberIsForbidden() {
        User owner = persistUser("usage-member-owner");
        Workspace workspace = createWorkspace(
            "usage-member",
            "Usage member",
            "usage-member-org",
            AccountType.ORG,
            owner
        );
        User member = persistUser("mentor");
        ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
    }
}
