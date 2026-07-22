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
        LlmUsageEvent event = new LlmUsageEvent();
        event.setId(UUID.randomUUID());
        event.setWorkspace(workspace);
        event.setJobType(LlmUsageJobType.PULL_REQUEST_REVIEW);
        event.setSourceType(LlmUsageSourceType.AGENT_JOB);
        event.setSourceId(UUID.randomUUID());
        event.setCostUsd(new BigDecimal(cost));
        // Budgeted spend only counts PRICED + INSTANCE-funded rows (#1368 slice 6) — both are the
        // entity defaults, but set them explicitly so this fixture keeps meaning that if the
        // defaults ever change.
        event.setPricingState(PricingState.PRICED);
        event.setFundingSource(FundingSource.INSTANCE);
        event.setOccurredAt(Instant.now());
        usageRepository.save(event);
    }

    @Test
    void adminSeesPerWorkspaceSpendIncludingZeroSpendWorkspaces() {
        Workspace spender = setupWorkspace("adm-spender");
        spender.setMonthlyLlmBudgetUsd(new BigDecimal("2.00"));
        workspaceRepository.save(spender);
        Workspace idle = setupWorkspace("adm-idle");
        seedEvent(spender, "3.00");

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
        var spenderRow = rows
            .stream()
            .filter(r -> r.workspaceId().equals(spender.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(spenderRow.pricedTotalCostUsd()).isEqualByComparingTo("3.00");
        assertThat(spenderRow.byoTotalCostUsd()).isEqualByComparingTo("0");
        assertThat(spenderRow.monthlyBudgetUsd()).isEqualByComparingTo("2.00");
        assertThat(spenderRow.verdict()).isEqualTo(LlmBudgetVerdict.EXHAUSTED);
        var idleRow = rows
            .stream()
            .filter(r -> r.workspaceId().equals(idle.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(idleRow.pricedTotalCostUsd()).isEqualByComparingTo("0");
        assertThat(idleRow.verdict()).isEqualTo(LlmBudgetVerdict.WITHIN);
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
