package de.tum.cit.aet.hephaestus.practices.result;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemSourceAccess;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Import(ReviewIntegrationData.class)
class MigratedHistoryReadIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ReviewIntegrationData data;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WebTestClient http;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    IdentityProviderRepository providers;

    @Autowired
    IdentityRepository identities;

    @Autowired
    JsonMapper json;

    @MockitoBean
    ScmItemSourceAccess source;

    Scenario scenario;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        scenario = data.scenario();
        when(source.kinds()).thenReturn(Set.of(new ItemKind("scm.pull_request")));
        when(source.permits(any(), any())).thenReturn(true);
    }

    WebTestClient.ResponseSpec get(String path) {
        return http.get()
                .uri("/workspaces/" + scenario.workspace().getWorkspaceSlug() + path)
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .exchange();
    }

    @Test
    void shouldReadMigratedTextAndQuotesAsUnverifiedHistoryWithoutCustodyReceipts() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        var retained = json.createObjectNode();
        retained.set("citations", Objects.requireNonNull(result.getEvidence()).path("citations"));
        migrated(result, retained.toString());
        get("/practices/reviews/results/" + result.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.summary")
                .isEqualTo("Recorded result")
                .jsonPath("$.currentness")
                .isEqualTo("UNVERIFIABLE")
                .jsonPath("$.evidenceHidden")
                .isEqualTo(false)
                .jsonPath("$.evidence.citations[0].quote")
                .isEqualTo("changed();");
        get("/practice-profile/reviews?itemId=" + scenario.item().getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content[0].review.id")
                .isEqualTo(result.getReviewId().toString())
                .jsonPath("$.content[0].results[0].currentness")
                .isEqualTo("UNVERIFIABLE");
        var provider = providers
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() -> providers.saveAndFlush(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));
        var identity = new Identity();
        identity.setAccount(scenario.account());
        identity.setProviderId(Objects.requireNonNull(provider.getId()));
        identity.setProviderUserKey("90000000" + scenario.account().getId());
        identities.saveAndFlush(identity);
        var feedbackId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO feedback(id,workspace_id,review_id,item_id,developer_id,destination,state,body,created_at,delivered_at,checks) "
                        + "VALUES (?,?,?,?,?,'PRACTICE_PAGE','DELIVERED','Legacy feedback',now(),now(),'{}')",
                feedbackId,
                scenario.workspace().getId(),
                result.getReviewId(),
                scenario.item().getId(),
                scenario.account().getId());
        jdbc.update(
                "INSERT INTO feedback_basis(id,workspace_id,feedback_id,result_id,developer_id,ordinal) VALUES (?,?,?,?,?,0)",
                UUID.randomUUID(),
                scenario.workspace().getId(),
                feedbackId,
                result.getId(),
                scenario.account().getId());
        get("/practices/feedback/practice-page")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].id")
                .isEqualTo(feedbackId.toString())
                .jsonPath("$[0].unverifiedHistory")
                .isEqualTo(true);
        when(source.permits(any(), any())).thenReturn(false);
        get("/practices/reviews/results/" + result.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.summary")
                .isEmpty()
                .jsonPath("$.evidenceHidden")
                .isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"citations\":[]}",
                "{\"openQuestions\":[]}",
                "{\"citations\":null,\"openQuestions\":null}"
            })
    void shouldReadAnOlderEvidenceObjectWithoutInventingQuotations(String retained) {
        var result = data.result(scenario, ResultOutcome.MET, Instant.now());
        migrated(result, retained);
        get("/practices/reviews/results/" + result.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.summary")
                .isEqualTo("Recorded result")
                .jsonPath("$.evidence.citations.length()")
                .isEqualTo(0)
                .jsonPath("$.evidence.openQuestions.length()")
                .isEqualTo(0);
        when(source.permits(any(), any())).thenReturn(false);
        get("/practices/reviews/results/" + result.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.evidenceHidden")
                .isEqualTo(true)
                .jsonPath("$.evidence")
                .isEmpty();
    }

    private void migrated(Result result, String evidence) {
        // Only migration creates these historical rows; the new-write guard must keep refusing them.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL session_replication_role=replica");
            jdbc.update(
                    "UPDATE result SET evidence_version=0,evidence=CAST(? AS jsonb) WHERE workspace_id=? AND id=?",
                    evidence,
                    scenario.workspace().getId(),
                    result.getId());
        });
    }
}
