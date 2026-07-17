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
        event.setSourceId(UUID.randomUUID());
        event.setModel("claude-sonnet-5");
        event.setInputTokens(100);
        event.setOutputTokens(20);
        event.setTotalCalls(2);
        event.setCostUsd(new BigDecimal(cost));
        event.setOccurredAt(month.atDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600));
        usageRepository.save(event);
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
        assertThat(report.totalCostUsd()).isEqualByComparingTo("2.50");
        assertThat(report.overBudget()).isFalse();
        assertThat(report.byJobType()).hasSize(2);
        var prReviews = report
            .byJobType()
            .stream()
            .filter(t -> t.jobType() == LlmUsageJobType.PULL_REQUEST_REVIEW)
            .findFirst()
            .orElseThrow();
        assertThat(prReviews.costUsd()).isEqualByComparingTo("2.00");
        assertThat(prReviews.inputTokens()).isEqualTo(200);
        assertThat(prReviews.totalCalls()).isEqualTo(4);
        assertThat(prReviews.events()).isEqualTo(2);
        assertThat(report.byDay()).hasSize(2);
        assertThat(report.byDay().getFirst().day()).isEqualTo(PREVIOUS.atDay(3));
        assertThat(report.byDay().getFirst().costUsd()).isEqualByComparingTo("2.00");
    }

    @Test
    @WithAdminUser
    void overBudgetFlagFlipsWhenSpendReachesTheCap() {
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
            .jsonPath("$.overBudget")
            .isEqualTo(true);
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
