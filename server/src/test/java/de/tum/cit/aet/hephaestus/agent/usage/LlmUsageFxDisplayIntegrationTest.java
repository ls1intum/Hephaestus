package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRate;
import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRateRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Deliberately its own Spring context so the default one keeps proving the zero-regression case
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

    @Autowired
    private WorkspaceRepository workspaceRepository;

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
            .uri("/workspaces/{slug}/llm/usage", workspace.getWorkspaceSlug())
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
        assertThat(report.instanceTotalCostUsd()).isEqualByComparingTo("10.00");
    }

    /**
     * {@code month} and {@code fx} are facts about the REQUEST, not about any workspace in it, so with
     * TWO workspaces spending they must appear ONCE — on the envelope — and not be copied onto every
     * row, which is what forced a client to reach into {@code rows[0]} for a response-level fact.
     */
    @Test
    void theAdminRollupReportsMonthAndRateOnceOnTheEnvelopeAndNeverOnARow() {
        Workspace first = setupWorkspaceWithAdmin("fx-admin-a");
        Workspace second = setupWorkspaceWithAdmin("fx-admin-b");
        seedEvent(first, "1.00");
        seedEvent(second, "2.00");
        LocalDate today = seedTodaysRate();

        byte[] body = webTestClient
            .get()
            .uri("/admin/llm/usage?month={month}", CURRENT.toString())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.month")
            .isEqualTo(CURRENT.toString())
            .jsonPath("$.workspaces.length()")
            .isEqualTo(2)
            .jsonPath("$.fx.currencyCode")
            .isEqualTo("EUR")
            .jsonPath("$.fx.rateDate")
            .isEqualTo(today.toString())
            // The UI credits the ECB by name in its disclosure, so the claim has to be on the wire.
            .jsonPath("$.fx.source")
            .isEqualTo("ECB")
            .jsonPath("$.fx.ratePerUsd")
            .value(rate -> assertThat(new BigDecimal(rate.toString())).isEqualByComparingTo("0.878966"))
            .returnResult()
            .getResponseBody();

        // Once for the whole response, not once per row: the key occurs a single time on the wire.
        // This is what makes the block above an ENVELOPE fact — copy the rate onto each of the two
        // workspace rows and there are three occurrences, not one.
        assertThat(new String(body, StandardCharsets.UTF_8).split("\"fx\"", -1)).hasSize(2);
    }

    /**
     * The value-level half of {@code LlmBudgetFxIsolationArchTest}, which can only prove that the
     * enforcement classes do not IMPORT fx — not that a rollup service never converts a number on its
     * way into a verdict. The fixture is built so the two currencies disagree about the answer: spend
     * is $9.00 against a $8.00 cap, exhausted in USD, while at the seeded ECB rate the same spend is
     * €7.91, comfortably under a cap of 8 — so any code that converted before comparing would report
     * WITHIN and quietly unpause a workspace that is over its budget.
     */
    @Test
    @WithAdminUser
    void budgetVerdictIsJudgedInUsdEvenWhenADisplayRateWouldUndercutIt() {
        Workspace workspace = setupWorkspaceWithAdmin("fx-verdict-usd");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("8.00"));
        workspaceRepository.save(workspace);
        seedEvent(workspace, "9.00");
        seedTodaysRate();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            // The rate is present — this is a converting context, not a null-fx accident.
            .jsonPath("$.fx.ratePerUsd")
            .value(rate -> assertThat(new BigDecimal(rate.toString())).isEqualByComparingTo("0.878966"))
            .jsonPath("$.instanceTotalCostUsd")
            .isEqualTo(9.0)
            .jsonPath("$.instanceBudgetVerdict")
            .isEqualTo("EXHAUSTED")
            .jsonPath("$.instancePaused")
            .isEqualTo(true);
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
            .uri("/workspaces/{slug}/llm/usage", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.fx")
            .doesNotExist();
    }
}
