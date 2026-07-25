package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.AdminWorkspaceLlmUsageDTO;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.WorkspaceLlmUsageReportDTO;
import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRate;
import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRateRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The configured half of the display-currency feature: with
 * {@code hephaestus.llm.display-currency=EUR} set, both month-scoped usage endpoints carry the
 * rate — and both carry the SAME rate, inverted once, with the true publication date.
 *
 * <p>Deliberately its own Spring context so the default one keeps proving the zero-regression case
 * ({@code LlmUsageControllerIntegrationTest#reportOmitsFxEntirelyWhenNoDisplayCurrencyConfigured}).
 */
@Tag("integration")
@TestPropertySource(properties = "hephaestus.llm.display-currency=EUR")
class LlmUsageFxDisplayIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String ADMIN_TOKEN = "mock-jwt-token-for-admin-user";
    private static final YearMonth CURRENT = YearMonth.now(ZoneOffset.UTC);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private LlmUsageEventRepository usageRepository;

    @Autowired
    private FxRateRepository fxRateRepository;

    private Workspace setupWorkspaceWithAdmin(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "Fx " + slug, slug + "-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private void seedEvent(Workspace workspace, String cost) {
        LlmUsageEvent event = new LlmUsageEvent();
        event.setId(UUID.randomUUID());
        event.setWorkspace(workspace);
        event.setJobType(LlmUsageJobType.PULL_REQUEST_REVIEW);
        event.setSourceType(LlmUsageSourceType.AGENT_JOB);
        event.setSourceId(UUID.randomUUID());
        event.setModel("claude-sonnet-5");
        event.setCostUsd(new BigDecimal(cost));
        event.setPricingState(PricingState.PRICED);
        event.setFundingSource(FundingSource.INSTANCE);
        event.setOccurredAt(CURRENT.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600));
        usageRepository.save(event);
    }

    private LocalDate seedTodaysRate() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        FxRate rate = new FxRate();
        rate.setRateDate(today);
        rate.setUsdPerEur(new BigDecimal("1.1377"));
        rate.setFetchedAt(Instant.now());
        fxRateRepository.save(rate);
        return today;
    }

    @Test
    @WithAdminUser
    void workspaceReportCarriesTheInvertedRateAndItsPublicationDate() {
        Workspace workspace = setupWorkspaceWithAdmin("fx-workspace");
        seedEvent(workspace, "10.00");
        LocalDate today = seedTodaysRate();

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
        assertThat(report.fx()).isNotNull();
        assertThat(report.fx().currencyCode()).isEqualTo("EUR");
        // EUR per USD — the inversion of the ECB's 1.1377 USD per EUR, done exactly once.
        assertThat(report.fx().ratePerUsd()).isEqualByComparingTo("0.878966");
        assertThat(report.fx().rateDate()).isEqualTo(today);
        // The amounts themselves stay USD: conversion is the client's, and it is labelled as an estimate.
        assertThat(report.pricedTotalCostUsd()).isEqualByComparingTo("10.00");
    }

    @Test
    void adminRollupCarriesTheSameRateOnEveryRow() {
        Workspace first = setupWorkspaceWithAdmin("fx-admin-a");
        Workspace second = setupWorkspaceWithAdmin("fx-admin-b");
        seedEvent(first, "1.00");
        seedEvent(second, "2.00");
        LocalDate today = seedTodaysRate();

        List<AdminWorkspaceLlmUsageDTO> rollups = webTestClient
            .get()
            .uri("/admin/llm-usage")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<AdminWorkspaceLlmUsageDTO>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(rollups).hasSize(2);
        // One month resolves to exactly one rate, so a client may read it off any row.
        assertThat(rollups).allSatisfy(row -> {
            assertThat(row.fx()).isNotNull();
            assertThat(row.fx().currencyCode()).isEqualTo("EUR");
            assertThat(row.fx().ratePerUsd()).isEqualByComparingTo("0.878966");
            assertThat(row.fx().rateDate()).isEqualTo(today);
        });
    }

    @Test
    @WithAdminUser
    void reportOmitsFxWhenTheStoredRateHasGoneStale() {
        Workspace workspace = setupWorkspaceWithAdmin("fx-stale");
        seedEvent(workspace, "10.00");
        FxRate stale = new FxRate();
        stale.setRateDate(LocalDate.now(ZoneOffset.UTC).minusDays(8));
        stale.setUsdPerEur(new BigDecimal("1.1377"));
        stale.setFetchedAt(Instant.now());
        fxRateRepository.save(stale);

        // A conversion drifting a week behind reality is worse than none: the UI falls back to USD.
        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm-usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.fx")
            .doesNotExist();
    }
}
