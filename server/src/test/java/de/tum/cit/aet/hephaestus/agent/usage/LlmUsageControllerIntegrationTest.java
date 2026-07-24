package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.WorkspaceLlmUsageReportDTO;
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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
        assertThat(report.monthlyBudgetUsd()).isEqualByComparingTo("5.00");
        assertThat(report.pricedTotalCostUsd()).isEqualByComparingTo("2.50");
        assertThat(report.byoTotalCostUsd()).isEqualByComparingTo("0");
        assertThat(report.verdict()).isEqualTo(LlmBudgetVerdict.WITHIN);
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
            .jsonPath("$.verdict")
            .isEqualTo("EXHAUSTED")
            // #1368 fix wave: EXHAUSTED always pauses, regardless of the instance's unpriced-usage policy.
            .jsonPath("$.usagePaused")
            .isEqualTo(true);
    }

    /**
     * #1368: {@code usagePaused} is the webapp's only reliable signal that new AI work is currently
     * paused. An unverifiable month on a capped workspace pauses — a cap whose true spend can't be
     * confirmed is treated as reached.
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
            .jsonPath("$.verdict")
            .isEqualTo("UNVERIFIABLE")
            .jsonPath("$.usagePaused")
            .isEqualTo(true);
    }

    @Test
    @WithAdminUser
    void usagePausedIsFalseForAPastMonthEvenIfTheCurrentMonthIsExhausted() {
        // usagePaused is a LIVE gate (always evaluated against "now"), never scoped to the requested
        // report month — a closed past month can't still be "pausing" new work.
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
            .jsonPath("$.usagePaused")
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
