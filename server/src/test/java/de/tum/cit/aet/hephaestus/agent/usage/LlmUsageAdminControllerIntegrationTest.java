package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.AdminWorkspaceLlmUsageDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Instance-admin LLM cost governance: {@code GET /admin/llm-usage} (cross-workspace month
 * rollup) and {@code PUT /admin/workspaces/{id}/llm-budget} (the cap). Verifies the app_admin
 * authority gate, the rollup values, budget set/clear, and request validation.
 */
@Tag("integration")
class LlmUsageAdminControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String ADMIN_TOKEN = "mock-jwt-token-for-admin-user";
    private static final String MENTOR_TOKEN = "mock-jwt-token-for-mentor-user";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private LlmUsageEventRepository usageRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        return createWorkspace(slug, "Admin usage " + slug, slug + "-org", AccountType.ORG, owner);
    }

    private void seedEvent(Workspace workspace, String cost) {
        seedEvent(workspace, cost, FundingSource.INSTANCE, PricingState.PRICED);
    }

    private void seedEvent(Workspace workspace, String cost, FundingSource funding, PricingState pricing) {
        LlmUsageEvent event = new LlmUsageEvent();
        event.setId(UUID.randomUUID());
        event.setWorkspace(workspace);
        event.setJobType(LlmUsageJobType.PULL_REQUEST_REVIEW);
        event.setSourceType(LlmUsageSourceType.AGENT_JOB);
        event.setSourceId(UUID.randomUUID());
        event.setCostUsd(pricing == PricingState.UNPRICED ? null : new BigDecimal(cost));
        // Budgeted spend only counts PRICED + INSTANCE-funded rows (#1368 slice 6) — both are the
        // entity defaults, but set them explicitly so this fixture keeps meaning that if the
        // defaults ever change.
        event.setPricingState(pricing);
        event.setFundingSource(funding);
        event.setOccurredAt(Instant.now());
        usageRepository.save(event);
    }

    private AdminWorkspaceLlmUsageDTO rollupFor(Workspace workspace) {
        List<AdminWorkspaceLlmUsageDTO> rows = webTestClient
            .get()
            .uri("/admin/llm-usage")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(AdminWorkspaceLlmUsageDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(rows).isNotNull();
        return rows
            .stream()
            .filter(r -> r.workspaceId().equals(workspace.getId()))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void adminSeesPerWorkspaceSpendIncludingZeroSpendWorkspaces() {
        Workspace spender = setupWorkspace("adm-spender");
        spender.setMonthlyLlmBudgetUsd(new BigDecimal("2.00"));
        workspaceRepository.save(spender);
        Workspace idle = setupWorkspace("adm-idle");
        seedEvent(spender, "3.00");

        var spenderRow = rollupFor(spender);
        assertThat(spenderRow.pricedTotalCostUsd()).isEqualByComparingTo("3.00");
        assertThat(spenderRow.byoTotalCostUsd()).isEqualByComparingTo("0");
        assertThat(spenderRow.instanceMonthlyBudgetUsd()).isEqualByComparingTo("2.00");
        assertThat(spenderRow.instanceBudgetVerdict()).isEqualTo(LlmBudgetVerdict.EXHAUSTED);
        assertThat(spenderRow.instanceFundedPaused()).isTrue();
        var idleRow = rollupFor(idle);
        assertThat(idleRow.pricedTotalCostUsd()).isEqualByComparingTo("0");
        assertThat(idleRow.instanceBudgetVerdict()).isEqualTo(LlmBudgetVerdict.WITHIN);
        assertThat(idleRow.instanceFundedPaused()).isFalse();
    }

    /**
     * #1368: the admin rollup reports the workspace's OWN cap and its own-provider verdict as a
     * separate column. Read-only here — it governs the workspace's money — and it must not bleed
     * into the instance verdict the admin acts on.
     */
    @Test
    void adminSeesTheWorkspacesOwnCapAndVerdictWithoutItAffectingTheInstanceVerdict() {
        Workspace workspace = setupWorkspace("adm-byo");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("100.00")); // host cap: nowhere near reached
        workspace.setMonthlyByoLlmBudgetUsd(new BigDecimal("5.00")); // workspace's own cap: reached
        workspaceRepository.save(workspace);
        seedEvent(workspace, "1.00");
        seedEvent(workspace, "5.00", FundingSource.WORKSPACE, PricingState.PRICED);

        var row = rollupFor(workspace);

        assertThat(row.instanceMonthlyBudgetUsd()).isEqualByComparingTo("100.00");
        assertThat(row.byoMonthlyBudgetUsd()).isEqualByComparingTo("5.00");
        assertThat(row.pricedTotalCostUsd()).isEqualByComparingTo("1.00");
        assertThat(row.byoTotalCostUsd()).isEqualByComparingTo("5.00");
        assertThat(row.instanceBudgetVerdict()).isEqualTo(LlmBudgetVerdict.WITHIN);
        assertThat(row.byoBudgetVerdict()).isEqualTo(LlmBudgetVerdict.EXHAUSTED);
        assertThat(row.instanceFundedPaused()).isFalse();
        assertThat(row.byoPaused()).isTrue();
    }

    /** Each verdict is only ever UNVERIFIABLE from a blind spot its own owner can clear. */
    @Test
    void anUnpricedOwnProviderEventLeavesTheInstanceVerdictUntouched() {
        Workspace workspace = setupWorkspace("adm-byo-unpriced");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("100.00"));
        workspace.setMonthlyByoLlmBudgetUsd(new BigDecimal("100.00"));
        workspaceRepository.save(workspace);
        seedEvent(workspace, "1.00");
        seedEvent(workspace, null, FundingSource.WORKSPACE, PricingState.UNPRICED);

        var row = rollupFor(workspace);

        assertThat(row.instanceBudgetVerdict()).isEqualTo(LlmBudgetVerdict.WITHIN);
        assertThat(row.byoBudgetVerdict()).isEqualTo(LlmBudgetVerdict.UNVERIFIABLE);
        assertThat(row.instanceFundedPaused()).isFalse();
        assertThat(row.byoPaused()).isTrue();
    }

    @Test
    void adminSetsAndClearsTheBudgetCap() {
        Workspace workspace = setupWorkspace("adm-budget");

        webTestClient
            .put()
            .uri("/admin/workspaces/{id}/llm-budget", workspace.getId())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyLlmBudgetUsd", "25.00"))
            .exchange()
            .expectStatus()
            .isNoContent();
        assertThat(
            workspaceRepository.findById(workspace.getId()).orElseThrow().getMonthlyLlmBudgetUsd()
        ).isEqualByComparingTo("25.00");

        webTestClient
            .put()
            .uri("/admin/workspaces/{id}/llm-budget", workspace.getId())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of())
            .exchange()
            .expectStatus()
            .isNoContent();
        assertThat(workspaceRepository.findById(workspace.getId()).orElseThrow().getMonthlyLlmBudgetUsd()).isNull();
    }

    @Test
    void negativeBudgetIsRejectedWith400() {
        Workspace workspace = setupWorkspace("adm-negative");

        webTestClient
            .put()
            .uri("/admin/workspaces/{id}/llm-budget", workspace.getId())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("monthlyLlmBudgetUsd", "-1.00"))
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void nonAdminIsForbidden() {
        webTestClient
            .get()
            .uri("/admin/llm-usage")
            .headers(h -> h.setBearerAuth(MENTOR_TOKEN))
            .exchange()
            .expectStatus()
            .isForbidden();
    }
}
