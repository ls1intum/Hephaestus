package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.adapter.EvidenceFolderPersonDataCatalog;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemSourceAccess;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @Autowired
    EvidenceFolderPersonDataCatalog copies;

    @Autowired
    ResultSourceAuthorization authorization;

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
    void shouldHideSemanticProblemKeysWhenSourceUseIsRefused() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        migrated(result, Objects.requireNonNull(result.getEvidence()).toString());
        String semanticKey = "private-authentication-credential";
        jdbc.update(
                "UPDATE problem SET key = ? WHERE workspace_id = ? AND result_id = ?",
                semanticKey,
                scenario.workspace().getId(),
                result.getId());
        var problemId = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT id FROM problem WHERE workspace_id = ? AND result_id = ?",
                UUID.class,
                scenario.workspace().getId(),
                result.getId()));
        var paths = Map.of(
                "/practices/results/" + result.getId(), "$.problems[0].key",
                "/practices/reviews/results/" + result.getId(), "$.problems[0].key",
                "/practices/reviews/problems/" + problemId, "$.problem.key");
        for (var entry : paths.entrySet()) {
            get(entry.getKey())
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath(entry.getValue())
                    .isEqualTo(semanticKey)
                    .jsonPath(entry.getValue().replace(".key", ".summary"))
                    .isEqualTo("A recorded problem");
        }
        when(source.permits(any(), any())).thenReturn(false);
        for (var entry : paths.entrySet()) {
            get(entry.getKey())
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath(entry.getValue())
                    .value(value -> assertThat(value).isNull())
                    .jsonPath(entry.getValue().replace(".key", ".summary"))
                    .isEqualTo("Evidence hidden");
        }
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
        get("/practices/items/" + scenario.item().getId() + "/reviews")
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

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETE", "UNKNOWN"})
    void shouldRefuseMigratedHistoryAfterAnIdentityInAReadyCopyIsErased(String dependencies) {
        var result = data.result(scenario, ResultOutcome.MET, Instant.now());
        migrated(result, Objects.requireNonNull(result.getEvidence()).toString());
        var copiedPerson = data.member(scenario.workspace().getId());
        var providerId = identity(copiedPerson);
        var job = producingJob(result);
        var payload = json.createObjectNode().put("dependencies", dependencies);
        payload.putArray("repositories");
        payload.putArray("identities")
                .addObject()
                .put("providerType", "GITHUB")
                .put("providerOrigin", "https://github.com")
                .put("subject", "history-" + copiedPerson.getId());
        receipt(job, payload.toString());
        assertThat(copies.custody(scenario.workspace().getId(), List.of(job)).complete())
                .isEqualTo(dependencies.equals("COMPLETE"));
        assertThat(authorization.permitsHistory(
                        scenario.workspace().getId(), result.getId(), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isTrue();
        jdbc.update(
                "INSERT INTO identity_restriction(id,provider_id,provider_user_key,team_key,kind) VALUES (?,?,?,'','ERASED')",
                UUID.randomUUID(),
                providerId,
                "history-" + copiedPerson.getId());
        var retained = copies.custody(scenario.workspace().getId(), List.of(job));
        assertThat(retained.complete()).isFalse();
        assertThat(retained.refused()).isTrue();
        assertThat(retained.copiedIdentities()).hasSize(1);
        assertThat(authorization.permitsHistory(
                        scenario.workspace().getId(), result.getId(), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
        assertThat(authorization.permits(
                        scenario.workspace().getId(), result.getId(), SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                .isFalse();
        get("/practices/reviews/results/" + result.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.summary")
                .isEmpty()
                .jsonPath("$.evidence")
                .isEmpty()
                .jsonPath("$.evidenceHidden")
                .isEqualTo(true);
    }

    @ParameterizedTest
    @CsvSource({"PRACTICE_PAGE,0", "CONVERSATION,0", "ON_WORK,0", "PRACTICE_PAGE,1", "CONVERSATION,1", "ON_WORK,1"})
    void shouldKeepPrivateFeedbackOutOfOperatorProjectionsWithoutHidingItFromItsRecipient(
            FeedbackDestination destination, int evidenceVersion) {
        var result = data.result(scenario, ResultOutcome.MET, Instant.now());
        if (evidenceVersion == 0)
            migrated(result, Objects.requireNonNull(result.getEvidence()).toString());
        var job = producingJob(result);
        receipt(job, "{\"dependencies\":\"COMPLETE\",\"identities\":[],\"repositories\":[]}");
        identity(scenario.account());
        var feedbackId = UUID.randomUUID();
        String body = "Private feedback sentinel";
        jdbc.update(
                "INSERT INTO feedback(id,workspace_id,review_id,item_id,developer_id,destination,state,body,created_at,delivered_at,checks) "
                        + "VALUES (?,?,?,?,?,?,'DELIVERED',?,now(),now(),'{}')",
                feedbackId,
                scenario.workspace().getId(),
                result.getReviewId(),
                scenario.item().getId(),
                scenario.account().getId(),
                destination.name(),
                body);
        jdbc.update(
                "INSERT INTO feedback_basis(id,workspace_id,feedback_id,result_id,developer_id,ordinal) VALUES (?,?,?,?,?,0)",
                UUID.randomUUID(),
                scenario.workspace().getId(),
                feedbackId,
                result.getId(),
                scenario.account().getId());
        var thread = UUID.randomUUID();
        var message = UUID.randomUUID();
        if (destination == FeedbackDestination.CONVERSATION) {
            jdbc.update(
                    "INSERT INTO chat_thread(id,workspace_id,account_id,title,created_at,surface) VALUES (?,?,?,'Private feedback',now(),'WEB')",
                    thread,
                    scenario.workspace().getId(),
                    scenario.account().getId());
            var parts = json.createArrayNode();
            parts.addObject().put("type", "text").put("text", body);
            jdbc.update(
                    "INSERT INTO chat_message(id,workspace_id,thread_id,role,status,parts,created_at) VALUES (?,?,?,'ASSISTANT','completed',CAST(? AS jsonb),now())",
                    message,
                    scenario.workspace().getId(),
                    thread,
                    parts.toString());
        }
        jdbc.update(
                "INSERT INTO feedback_copy(id,workspace_id,feedback_id,kind,position,body,path,state,posted_at,chat_message_id) VALUES (?,?,?,?,0,?,?,'POSTED',now(),?)",
                UUID.randomUUID(),
                scenario.workspace().getId(),
                feedbackId,
                destination == FeedbackDestination.CONVERSATION
                        ? "CHAT_TURN"
                        : destination == FeedbackDestination.ON_WORK ? "FILE_NOTE" : "SUMMARY",
                destination == FeedbackDestination.PRACTICE_PAGE ? null : body,
                destination == FeedbackDestination.ON_WORK ? "src/Main.java" : null,
                destination == FeedbackDestination.CONVERSATION ? message : null);
        var other = data.member(scenario.workspace().getId());
        jdbc.update(
                "UPDATE membership SET role='ADMIN' WHERE workspace_id=? AND account_id=?",
                scenario.workspace().getId(),
                other.getId());
        var operator = new Scenario(scenario.workspace(), other, scenario.practice(), scenario.item());
        boolean privateDestination = destination != FeedbackDestination.ON_WORK;
        var list = get(operator, "/practices/reviews/feedback?reviewId=" + result.getReviewId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content[0].id")
                .isEqualTo(feedbackId.toString())
                .jsonPath("$.content[0].textHidden")
                .isEqualTo(privateDestination);
        if (privateDestination) list.jsonPath("$.content[0].bodyPreview").isEmpty();
        else list.jsonPath("$.content[0].bodyPreview").isEqualTo(body);
        var detail = get(operator, "/practices/reviews/feedback/" + feedbackId)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.textHidden")
                .isEqualTo(privateDestination);
        if (privateDestination) {
            detail.jsonPath("$.body")
                    .isEmpty()
                    .jsonPath("$.bodyPreview")
                    .isEmpty()
                    .jsonPath("$.copies[0].body")
                    .isEmpty()
                    .jsonPath("$.contentDigest")
                    .isEmpty();
        } else {
            detail.jsonPath("$.body")
                    .isEqualTo(body)
                    .jsonPath("$.copies[0].body")
                    .isEqualTo(body);
        }
        if (destination == FeedbackDestination.PRACTICE_PAGE) {
            get("/practices/feedback/practice-page")
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$[0].id")
                    .isEqualTo(feedbackId.toString())
                    .jsonPath("$[0].body")
                    .isEqualTo(body);
            get(operator, "/practices/feedback/practice-page")
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$")
                    .isEmpty();
        } else if (destination == FeedbackDestination.CONVERSATION) {
            get("/mentor/threads/" + thread)
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.messages[0].parts[0].text")
                    .isEqualTo(body);
            get(operator, "/mentor/threads/" + thread)
                    .expectStatus()
                    .isNotFound()
                    .expectBody(Void.class);
        }
    }

    private WebTestClient.ResponseSpec get(Scenario viewer, String path) {
        return http.get()
                .uri("/workspaces/" + viewer.workspace().getWorkspaceSlug() + path)
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(viewer)))
                .exchange();
    }

    private long identity(Account account) {
        var provider = providers
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() -> providers.saveAndFlush(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));
        var identity = new Identity();
        identity.setAccount(account);
        identity.setProviderId(Objects.requireNonNull(provider.getId()));
        identity.setProviderUserKey("history-" + account.getId());
        identities.saveAndFlush(identity);
        return Objects.requireNonNull(provider.getId());
    }

    private UUID producingJob(Result result) {
        var job = data.job(result);
        jdbc.update(
                "UPDATE review_item SET job_id=? WHERE workspace_id=? AND result_id=?",
                job,
                scenario.workspace().getId(),
                result.getId());
        return job;
    }

    private void receipt(UUID job, String payload) {
        jdbc.update(
                "INSERT INTO person_evidence_copy(id,workspace_id,job_id,store_id,state,payload) VALUES (?,?,?,?,'READY',CAST(? AS jsonb))",
                UUID.randomUUID(),
                scenario.workspace().getId(),
                job,
                UUID.randomUUID(),
                payload);
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
