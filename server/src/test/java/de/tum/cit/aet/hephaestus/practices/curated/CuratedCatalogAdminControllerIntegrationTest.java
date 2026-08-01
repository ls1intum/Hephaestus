package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CreateCuratedAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CreateCuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedAreaDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.UpdateCuratedStatusRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

@Tag("integration")
class CuratedCatalogAdminControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String ADMIN_TOKEN = "mock-jwt-token-for-admin-user";
    private static final String MENTOR_TOKEN = "mock-jwt-token-for-mentor-user";
    private static final String CATALOG = "/admin/practice-catalog";
    /** Real entries of the shipped catalog, so these cases run against what an instance actually gets. */
    private static final String PRACTICE = "describe-what-and-why";
    private static final String AREA = "review-ready-work";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEventPublisher events;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        User owner = persistUser("catalog-owner");
        workspace = createWorkspace("catalog", "Catalog", "catalog", AccountType.ORG, owner);
    }

    @Test
    void showsTheCatalogTheBuildShipsWithNothingStored() {
        assertThat(overrideRows()).isZero();

        webTestClient
            .get()
            .uri(CATALOG)
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.summary.updatesChangingDetection")
            .isEqualTo(0)
            .jsonPath("$.summary.editedHere")
            .isEqualTo(0)
            .jsonPath("$.practices[0].status.state")
            .isEqualTo("FROM_HEPHAESTUS");
    }

    @Test
    void anEditIsTheOnlyThingThatGetsStored() {
        CuratedPracticeDTO before = getPractice();

        CuratedPracticeDTO edited = putPractice(etagOf(before), "Our own criteria")
            .expectStatus()
            .isOk()
            .expectBody(CuratedPracticeDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(edited).isNotNull();
        assertThat(edited.status().state()).isEqualTo(CatalogEntryState.EDITED_HERE);
        assertThat(edited.definition().criteria()).isEqualTo("Our own criteria");
        assertThat(overrideRows()).isOne();
    }

    @Test
    void theShippedDefinitionIsAlwaysThereToCompareAgainstBeforeTakingIt() {
        putPractice(etagOf(getPractice()), "Our own criteria").expectStatus().isOk();

        CuratedPracticeDTO edited = getPractice();

        // Without this, "use the Hephaestus version" would be asking somebody to accept text they
        // cannot read.
        assertThat(edited.shipped()).isNotNull();
        assertThat(edited.shipped().criteria()).isNotEqualTo(edited.definition().criteria());
        assertThat(edited.status().changeKind()).isEqualTo(CatalogChangeKind.DETECTION);
    }

    @Test
    void anEditThatOnlyChangesWhatPeopleReadIsMarkedAsSuch() {
        CuratedPracticeDTO before = getPractice();
        CuratedPracticeRequestDTO body = new CuratedPracticeRequestDTO(
            before.definition().name(),
            before.definition().artifactType(),
            before.definition().triggerEvents(),
            before.definition().criteria(),
            before.definition().precomputeScript(),
            "Our own words about why this matters.",
            before.definition().whatGoodLooksLike(),
            before.definition().areaSlug()
        );
        webTestClient
            .put()
            .uri(CATALOG + "/practices/" + PRACTICE)
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etagOf(before));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isOk();

        assertThat(getPractice().status().changeKind()).isEqualTo(CatalogChangeKind.WORDING);
    }

    @Test
    void usingTheHephaestusVersionRemovesTheStoredEditEntirely() {
        putPractice(etagOf(getPractice()), "Our own criteria").expectStatus().isOk();
        assertThat(overrideRows()).isOne();

        webTestClient
            .delete()
            .uri(CATALOG + "/practices/" + PRACTICE + "/override")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etagOf(getPractice()));
            })
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.status.state")
            .isEqualTo("FROM_HEPHAESTUS");

        assertThat(overrideRows()).isZero();
    }

    @Test
    void refusesAnEditBasedOnAVersionSomebodyElseHasMovedOn() {
        String stale = etagOf(getPractice());
        putPractice(stale, "Our own criteria").expectStatus().isOk();

        putPractice(stale, "Someone else's criteria").expectStatus().isEqualTo(412);
    }

    @Test
    void refusesAnEditThatDoesNotSayWhichVersionItIsBasedOn() {
        webTestClient
            .put()
            .uri(CATALOG + "/practices/" + PRACTICE)
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(definitionOf(getPractice(), "Our own criteria"))
            .exchange()
            .expectStatus()
            .isEqualTo(428);
    }

    @Test
    void guardsTheFirstEditOfAnEntryNobodyHasTouched() {
        // The case with no stored row is exactly where two administrators are most likely to collide,
        // so the tag has to exist before the row does.
        assertThat(overrideRows()).isZero();
        putPractice("\"nonsense\"", "Our own criteria").expectStatus().isEqualTo(412);
    }

    @Test
    void notOfferingAnAreaWithholdsThePracticesFiledUnderIt() {
        CuratedAreaDTO area = getArea();
        webTestClient
            .patch()
            .uri(CATALOG + "/areas/" + AREA + "/status")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etagOf(area));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateCuratedStatusRequestDTO(CuratedStatus.RETIRED))
            .exchange()
            .expectStatus()
            .isOk();

        assertThat(getPractice().status().offered()).isTrue();
        webTestClient
            .get()
            .uri(CATALOG + "/areas/" + AREA + "/practices")
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$[0]")
            .exists();
    }

    @Test
    void anAreaTheInstanceWritesBehavesLikeAShippedOne() {
        CuratedAreaDTO created = webTestClient
            .post()
            .uri(CATALOG + "/areas")
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                new CreateCuratedAreaRequestDTO(
                    "house-rules",
                    new CuratedAreaRequestDTO("House rules", "Ours alone", 9, "Scale", "amber")
                )
            )
            .exchange()
            .expectStatus()
            .isCreated()
            .expectHeader()
            .exists(HttpHeaders.ETAG)
            .expectBody(CuratedAreaDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.status().state()).isEqualTo(CatalogEntryState.YOURS);
        assertThat(created.shipped()).isNull();
    }

    @Test
    @WithAdminUser
    void aWorkspaceCopyReportsWhereItCameFromAndWhetherItStillMatches() {
        ensureAdminMembership(workspace);
        events.publishEvent(new WorkspacesInitializedEvent(1));

        // Read over HTTP, so the response is built after the transaction closes — the only way a
        // missing fetch shows up. A workspace holding the whole catalog returns more than the client's
        // default in-memory limit, which is a property of this harness, not of the endpoint.
        WebTestClient client = webTestClient
            .mutate()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
            .build();
        client
            .get()
            .uri("/workspaces/{workspaceSlug}/practices", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$[0].catalogOrigin.link")
            .isEqualTo("IN_SYNC")
            .jsonPath("$[0].catalogOrigin.sourceOffered")
            .isEqualTo(true);

        client
            .get()
            .uri("/workspaces/{workspaceSlug}/practice-areas", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$[0].catalogOrigin.link")
            .isEqualTo("IN_SYNC");
    }

    @Test
    void keepsTheCatalogOutOfReachOfEveryoneButInstanceAdmins() {
        webTestClient
            .post()
            .uri(CATALOG + "/practices")
            .headers(headers -> headers.setBearerAuth(MENTOR_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new CreateCuratedPracticeRequestDTO("anything", definitionOf(getPractice(), "Criteria")))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    void recordsAnInstanceAuditRowWithoutCopyingTheDefinitionIntoIt() {
        putPractice(etagOf(getPractice()), "Our own criteria").expectStatus().isOk();

        var rows = jdbcTemplate.queryForList(
            """
            SELECT workspace_id, new_value::text AS new_value
            FROM config_audit_event
            WHERE entity_type = 'CURATED_PRACTICE' AND entity_id = ? AND action = 'UPDATED'
            """,
            PRACTICE
        );

        assertThat(rows)
            .singleElement()
            .satisfies(row -> {
                assertThat(row.get("workspace_id")).isNull();
                assertThat(row.get("new_value"))
                    .asString()
                    .doesNotContain("Our own criteria")
                    .contains("effectiveFingerprint", "state");
            });
    }

    private WebTestClient.ResponseSpec putPractice(String ifMatch, String criteria) {
        return webTestClient
            .put()
            .uri(CATALOG + "/practices/" + PRACTICE)
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, ifMatch);
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(definitionOf(getPractice(), criteria))
            .exchange();
    }

    private static CuratedPracticeRequestDTO definitionOf(CuratedPracticeDTO practice, String criteria) {
        return new CuratedPracticeRequestDTO(
            practice.definition().name(),
            practice.definition().artifactType() == null
                ? WorkArtifact.PULL_REQUEST
                : practice.definition().artifactType(),
            practice.definition().triggerEvents() == null
                ? List.of("PullRequestCreated")
                : practice.definition().triggerEvents(),
            criteria,
            practice.definition().precomputeScript(),
            practice.definition().whyItMatters(),
            practice.definition().whatGoodLooksLike(),
            practice.definition().areaSlug()
        );
    }

    private CuratedPracticeDTO getPractice() {
        return webTestClient
            .get()
            .uri(CATALOG + "/practices/" + PRACTICE)
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedPracticeDTO.class)
            .returnResult()
            .getResponseBody();
    }

    private CuratedAreaDTO getArea() {
        return webTestClient
            .get()
            .uri(CATALOG + "/areas/" + AREA)
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedAreaDTO.class)
            .returnResult()
            .getResponseBody();
    }

    private String etagOf(CuratedPracticeDTO practice) {
        return tag(CATALOG + "/practices/" + practice.slug());
    }

    private String etagOf(CuratedAreaDTO area) {
        return tag(CATALOG + "/areas/" + area.slug());
    }

    private String tag(String uri) {
        return webTestClient
            .get()
            .uri(uri)
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseHeaders()
            .getETag();
    }

    private long overrideRows() {
        return jdbcTemplate.queryForObject(
            "SELECT (SELECT count(*) FROM curated_practice_override) + (SELECT count(*) FROM curated_area_override)",
            Long.class
        );
    }
}
