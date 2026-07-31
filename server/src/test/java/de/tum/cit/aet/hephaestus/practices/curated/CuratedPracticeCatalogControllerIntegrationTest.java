package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CreateCuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDetailDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.UpdateCuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.UpdateCuratedPracticeStatusRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.support.TransactionOperations;

@Tag("integration")
class CuratedPracticeCatalogControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String ADMIN_TOKEN = "mock-jwt-token-for-admin-user";
    private static final String MENTOR_TOKEN = "mock-jwt-token-for-mentor-user";
    private static final String ADMIN_URI = "/admin/practice-catalog/practices";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CuratedPracticeAreaRepository areaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CuratedPracticeRepository practiceRepository;

    @Autowired
    private CuratedPracticeRevisionRepository revisionRepository;

    @Autowired
    private TransactionOperations transactionOperations;

    private Workspace workspace;

    @BeforeEach
    void setUpCatalog() {
        jdbcTemplate.update(
            """
            INSERT INTO curated_catalog_sync_state (
                source, catalog_revision, content_digest, synchronized_at,
                provenance_backfill_version, provenance_backfilled_at
            ) VALUES ('BUNDLED', 0, NULL, NULL, 0, NULL)
            """
        );
        CuratedPracticeArea area = new CuratedPracticeArea();
        area.setSlug("engineering");
        area.setName("Engineering");
        area.setDisplayOrder(1);
        areaRepository.save(area);

        User owner = persistUser("catalog-browser-owner");
        workspace = createWorkspace("catalog-browser", "Catalog browser", "catalog-browser", AccountType.ORG, owner);
    }

    @Test
    void shouldRevisePracticeWhenIfMatchIsCurrent() {
        CuratedPracticeDetailDTO created = createPractice();
        var revisedResult = webTestClient
            .put()
            .uri(ADMIN_URI + "/review-failure-paths")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etag(created));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .exists(HttpHeaders.ETAG)
            .expectBody(CuratedPracticeDetailDTO.class)
            .returnResult();

        CuratedPracticeDetailDTO revised = revisedResult.getResponseBody();
        assertThat(revised).isNotNull();
        assertThat(revised.revisionNumber()).isEqualTo(2);
        assertThat(revised.version()).isGreaterThan(created.version());
    }

    @Test
    void shouldReturn412WhenIfMatchIsStale() {
        CuratedPracticeDetailDTO created = createPractice();
        updatePractice(etag(created)).expectStatus().isOk();

        updatePractice(etag(created)).expectStatus().isEqualTo(412);
    }

    @Test
    void shouldRetirePracticeWhenIfMatchIsCurrent() {
        CuratedPracticeDetailDTO created = createPractice();

        webTestClient
            .patch()
            .uri(ADMIN_URI + "/review-failure-paths/status")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etag(created));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateCuratedPracticeStatusRequestDTO(CuratedPracticeStatus.RETIRED))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("RETIRED");
    }

    @Test
    @WithAdminUser
    void shouldHideRetiredEntriesWhenWorkspaceAdminBrowsesCatalog() {
        ensureAdminMembership(workspace);
        CuratedPracticeDetailDTO created = createPractice();

        webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/practice-catalog", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.practices[0].slug")
            .isEqualTo("review-failure-paths");

        webTestClient
            .patch()
            .uri(ADMIN_URI + "/review-failure-paths/status")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, "\"v" + created.version() + "\"");
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateCuratedPracticeStatusRequestDTO(CuratedPracticeStatus.RETIRED))
            .exchange()
            .expectStatus()
            .isOk();

        webTestClient
            .get()
            .uri(
                "/workspaces/{workspaceSlug}/practice-catalog/practices/review-failure-paths",
                workspace.getWorkspaceSlug()
            )
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void shouldForbidMutationWhenUserIsNotAppAdmin() {
        webTestClient
            .post()
            .uri(ADMIN_URI)
            .headers(headers -> headers.setBearerAuth(MENTOR_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest())
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    void shouldReturn428WhenIfMatchIsMissing() {
        createPractice();

        webTestClient
            .put()
            .uri(ADMIN_URI + "/review-failure-paths")
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest())
            .exchange()
            .expectStatus()
            .isEqualTo(428);
    }

    @Test
    void shouldReturn412WhenIfMatchIsWeak() {
        CuratedPracticeDetailDTO created = createPractice();

        updatePractice("W/" + etag(created)).expectStatus().isEqualTo(412);
    }

    @Test
    void shouldUpdatePracticeWhenIfMatchContainsCurrentTag() {
        CuratedPracticeDetailDTO created = createPractice();

        updatePractice("\"stale\", " + etag(created)).expectStatus().isOk();
    }

    @Test
    void shouldUpdateExistingPracticeWhenIfMatchIsWildcard() {
        createPractice();

        webTestClient
            .put()
            .uri(ADMIN_URI + "/review-failure-paths")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, "*");
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest())
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void shouldWriteRedactedInstanceAuditWhenPracticeMutates() {
        CreateCuratedPracticeRequestDTO request = new CreateCuratedPracticeRequestDTO(
            "review-failure-paths",
            "Review failure paths",
            List.of("PullRequestCreated"),
            "criteria-redaction-probe",
            "const precomputeRedactionProbe = true;",
            WorkArtifact.PULL_REQUEST,
            "Clear failures shorten incident recovery.",
            "Changed failure paths preserve the original cause and add actionable context.",
            "engineering"
        );
        CuratedPracticeDetailDTO created = createPractice(request);

        var rows = jdbcTemplate.queryForList(
            """
            SELECT workspace_id, action, new_value::text AS new_value
            FROM config_audit_event
            WHERE entity_type = 'CURATED_PRACTICE' AND entity_id = ?
            """,
            created.id().toString()
        );

        assertThat(rows)
            .singleElement()
            .satisfies(row -> {
                assertThat(row.get("workspace_id")).isNull();
                assertThat(row.get("action")).hasToString("CREATED");
                assertThat(row.get("new_value"))
                    .asString()
                    .doesNotContain(request.criteria(), request.precomputeScript())
                    .contains("detectionFingerprint", "revisionNumber");
            });
    }

    @Test
    void shouldMarkBundledPracticeOverriddenWhenDefinitionChanges() {
        EntityExchangeResult<CuratedPracticeDetailDTO> overridden = overrideBundledPractice();

        assertThat(overridden.getResponseBody())
            .isNotNull()
            .extracting(
                CuratedPracticeDetailDTO::sourceKind,
                CuratedPracticeDetailDTO::syncStatus,
                CuratedPracticeDetailDTO::latestBundledCatalogRevision
            )
            .containsExactly(CuratedPracticeSourceKind.BUNDLED, CuratedPracticeSyncStatus.OVERRIDDEN, 1L);
    }

    @Test
    void shouldReturn412WhenResetIfMatchIsStale() {
        overrideBundledPractice();

        webTestClient
            .delete()
            .uri(ADMIN_URI + "/review-failure-paths/override")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, "\"v999999\"");
            })
            .exchange()
            .expectStatus()
            .isEqualTo(412);
    }

    @Test
    void shouldResetBundledOverrideWhenIfMatchIsCurrent() {
        EntityExchangeResult<CuratedPracticeDetailDTO> overridden = overrideBundledPractice();

        webTestClient
            .delete()
            .uri(ADMIN_URI + "/review-failure-paths/override")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, overridden.getResponseHeaders().getETag());
            })
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.sourceKind")
            .isEqualTo("BUNDLED")
            .jsonPath("$.syncStatus")
            .isEqualTo("SYNCED")
            .jsonPath("$.latestBundledCatalogRevision")
            .isEqualTo(1)
            .jsonPath("$.revisionNumber")
            .isEqualTo(3);
    }

    private EntityExchangeResult<CuratedPracticeDetailDTO> overrideBundledPractice() {
        createBundledPractice();
        String initialEtag = webTestClient
            .get()
            .uri(ADMIN_URI + "/review-failure-paths")
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedPracticeDetailDTO.class)
            .returnResult()
            .getResponseHeaders()
            .getETag();

        return webTestClient
            .put()
            .uri(ADMIN_URI + "/review-failure-paths")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, initialEtag);
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedPracticeDetailDTO.class)
            .returnResult();
    }

    private WebTestClient.ResponseSpec updatePractice(String ifMatch) {
        return webTestClient
            .put()
            .uri(ADMIN_URI + "/review-failure-paths")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, ifMatch);
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest())
            .exchange();
    }

    private static String etag(CuratedPracticeDetailDTO practice) {
        return "\"v" + practice.version() + "\"";
    }

    private CuratedPracticeDetailDTO createPractice() {
        return createPractice(createRequest());
    }

    private CuratedPracticeDetailDTO createPractice(CreateCuratedPracticeRequestDTO request) {
        return webTestClient
            .post()
            .uri(ADMIN_URI)
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectHeader()
            .valueMatches(HttpHeaders.ETAG, "\"v\\d+\"")
            .expectBody(CuratedPracticeDetailDTO.class)
            .returnResult()
            .getResponseBody();
    }

    private void createBundledPractice() {
        CreateCuratedPracticeRequestDTO request = createRequest();
        PracticeDefinition definition = new PracticeDefinition(
            request.name(),
            request.artifactType(),
            request.triggerEvents(),
            request.criteria(),
            request.precomputeScript(),
            request.whyItMatters(),
            request.whatGoodLooksLike(),
            request.areaSlug()
        );
        String digest = definition.digest(request.slug());
        transactionOperations.executeWithoutResult(ignored -> {
            Instant now = Instant.now();
            CuratedPractice practice = new CuratedPractice();
            practice.initializeBundled(request.slug(), now);
            practice = practiceRepository.save(practice);
            CuratedPracticeRevision revision = revisionRepository.save(
                new CuratedPracticeRevision(
                    practice,
                    1,
                    definition,
                    definition.detectionFingerprint(request.slug()),
                    CuratedPracticeRevisionOrigin.BUNDLED,
                    1L,
                    digest,
                    now
                )
            );
            practice.applyBundled(revision, now);
            practiceRepository.save(practice);
        });
    }

    private CreateCuratedPracticeRequestDTO createRequest() {
        return new CreateCuratedPracticeRequestDTO(
            "review-failure-paths",
            "Review failure paths",
            List.of("PullRequestCreated"),
            "Check whether changed failure paths keep useful failure context.",
            null,
            WorkArtifact.PULL_REQUEST,
            "Clear failures shorten incident recovery.",
            "Changed failure paths preserve the original cause and add actionable context.",
            "engineering"
        );
    }

    private UpdateCuratedPracticeRequestDTO updateRequest() {
        return new UpdateCuratedPracticeRequestDTO(
            "Review failure paths",
            List.of("PullRequestCreated"),
            "Check whether changed failure paths preserve useful context.",
            null,
            WorkArtifact.PULL_REQUEST,
            "Clear failures shorten incident recovery.",
            "Changed failure paths preserve the original cause and add actionable context.",
            "engineering"
        );
    }
}
