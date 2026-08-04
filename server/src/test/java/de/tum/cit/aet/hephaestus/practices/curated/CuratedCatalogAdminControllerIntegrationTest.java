package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceDefaults;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CreateCuratedAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CreateCuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedAreaDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedCatalogDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.UpdateCuratedStatusRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PlacePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReorderPracticeAreasRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReorderPracticesRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
    private static final String PRACTICE = "describe-what-and-why";
    private static final String AREA = "review-ready-work";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PracticeEvidenceDefaults evidenceDefaults;

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
    void appAdminCanReadEvidenceAuthoringOptions() {
        webTestClient
            .get()
            .uri(CATALOG + "/evidence-options")
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.artifacts[2].artifactType")
            .isEqualTo("CONVERSATION_THREAD")
            .jsonPath("$.artifacts[2].baseline.required[0].sourceKind")
            .isEqualTo("slack.conversation.thread");
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
            before.definition().evidence(),
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
    void savingTheShippedDefinitionStopsOverridingIt() {
        CuratedPracticeDTO original = getPractice();
        putPractice(etagOf(original), "Our own criteria").expectStatus().isOk();
        CuratedPracticeDTO edited = getPractice();
        assertThat(edited.shipped()).isNotNull();

        CuratedPracticeDTO restored = webTestClient
            .put()
            .uri(CATALOG + "/practices/" + PRACTICE)
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etagOf(edited));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(edited.shipped())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedPracticeDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(restored).isNotNull();
        assertThat(restored.status().state()).isEqualTo(CatalogEntryState.FROM_HEPHAESTUS);
        assertThat(restored.position()).isEqualTo(original.position());
        assertThat(overrideRows()).isZero();
    }

    @Test
    void refusesAnEditBasedOnAVersionSomebodyElseHasMovedOn() {
        String stale = etagOf(getPractice());
        putPractice(stale, "Our own criteria").expectStatus().isOk();

        putPractice(stale, "Someone else's criteria")
            .expectStatus()
            .isEqualTo(412)
            .expectBody()
            .jsonPath("$.detail")
            .isEqualTo("Catalog practice '" + PRACTICE + "' changed since it was loaded.");
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
        assertThat(overrideRows()).isZero();
        putPractice("\"nonsense\"", "Our own criteria").expectStatus().isEqualTo(412);
    }

    @Test
    void acknowledgingEntriesWithoutCustomDefinitionsIsAnIdempotentNoOp() {
        CuratedPracticeDTO practice = getPractice();
        webTestClient
            .patch()
            .uri(CATALOG + "/practices/" + PRACTICE + "/status")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etagOf(practice));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateCuratedStatusRequestDTO(CuratedStatus.RETIRED))
            .exchange()
            .expectStatus()
            .isOk();

        CuratedCatalogDTO catalog = getCatalog();
        webTestClient
            .patch()
            .uri(CATALOG + "/areas/" + AREA + "/status")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, quote(catalog.etag()));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateCuratedStatusRequestDTO(CuratedStatus.RETIRED))
            .exchange()
            .expectStatus()
            .isOk();

        webTestClient
            .put()
            .uri(CATALOG + "/practices/" + PRACTICE + "/override/acknowledgement")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etagOf(getPractice()));
            })
            .exchange()
            .expectStatus()
            .isOk();
        webTestClient
            .put()
            .uri(CATALOG + "/areas/" + AREA + "/override/acknowledgement")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etagOf(getArea(AREA)));
            })
            .exchange()
            .expectStatus()
            .isOk();

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM curated_practice_override WHERE slug = ? AND based_on_digest IS NULL",
                Long.class,
                PRACTICE
            )
        ).isOne();
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM curated_area_override WHERE slug = ? AND based_on_digest IS NULL",
                Long.class,
                AREA
            )
        ).isOne();
    }

    @Test
    void reordersAreasWithoutTurningTheirDefinitionsIntoEdits() {
        CuratedCatalogDTO before = getCatalog();
        List<String> original = before.areas().stream().map(CuratedAreaDTO::slug).toList();
        CuratedCatalogDTO unchanged = webTestClient
            .patch()
            .uri(CATALOG + "/areas/reorder")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, quote(before.etag()));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new ReorderPracticeAreasRequestDTO(original))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedCatalogDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(unchanged).isNotNull();
        assertThat(unchanged.customOrder()).isFalse();
        assertThat(overrideRows()).isZero();

        List<String> reversed = before
            .areas()
            .stream()
            .map(CuratedAreaDTO::slug)
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        java.util.Collections.reverse(reversed);

        CuratedCatalogDTO after = webTestClient
            .patch()
            .uri(CATALOG + "/areas/reorder")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, quote(before.etag()));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new ReorderPracticeAreasRequestDTO(reversed))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedCatalogDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(after).isNotNull();
        assertThat(after.customOrder()).isTrue();
        assertThat(after.areas()).extracting(CuratedAreaDTO::slug).containsExactlyElementsOf(reversed);
        assertThat(after.areas()).allSatisfy(area ->
            assertThat(area.status().state()).isEqualTo(CatalogEntryState.FROM_HEPHAESTUS)
        );
        assertThat(after.etag()).isNotEqualTo(before.etag());
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM config_audit_event WHERE entity_type IN ('CURATED_PRACTICE', 'CURATED_PRACTICE_AREA')",
                Long.class
            )
        ).isZero();

        webTestClient
            .patch()
            .uri(CATALOG + "/areas/reorder")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, quote(before.etag()));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new ReorderPracticeAreasRequestDTO(reversed))
            .exchange()
            .expectStatus()
            .isEqualTo(412)
            .expectBody()
            .jsonPath("$.detail")
            .isEqualTo("Practice catalog changed since it was loaded.");

        CuratedCatalogDTO ownedDefaultOrder = webTestClient
            .patch()
            .uri(CATALOG + "/areas/reorder")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, quote(after.etag()));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new ReorderPracticeAreasRequestDTO(original))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedCatalogDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(ownedDefaultOrder).isNotNull();
        assertThat(ownedDefaultOrder.customOrder()).isTrue();
        assertThat(ownedDefaultOrder.areas()).extracting(CuratedAreaDTO::slug).containsExactlyElementsOf(original);

        CuratedCatalogDTO reset = webTestClient
            .delete()
            .uri(CATALOG + "/order")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, quote(ownedDefaultOrder.etag()));
            })
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedCatalogDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(reset).isNotNull();
        assertThat(reset.customOrder()).isFalse();
        assertThat(reset.etag()).isNotEqualTo(ownedDefaultOrder.etag());
        assertThat(reset.areas())
            .extracting(CuratedAreaDTO::slug)
            .containsExactlyElementsOf(before.areas().stream().map(CuratedAreaDTO::slug).toList());
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM curated_area_override WHERE position IS NOT NULL",
                Long.class
            )
        ).isZero();
    }

    @Test
    void reordersAndMovesPracticesWithOneStructuralPrecondition() {
        CuratedCatalogDTO before = getCatalog();
        String sourceArea = before.practices().getFirst().areaSlug();
        List<String> bucket = before
            .practices()
            .stream()
            .filter(practice -> java.util.Objects.equals(practice.areaSlug(), sourceArea))
            .map(practice -> practice.slug())
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        assertThat(bucket).hasSizeGreaterThan(1);
        java.util.Collections.reverse(bucket);

        CuratedCatalogDTO reordered = webTestClient
            .patch()
            .uri(CATALOG + "/practices/reorder")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, quote(before.etag()));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new ReorderPracticesRequestDTO(sourceArea, bucket))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedCatalogDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(reordered).isNotNull();
        assertThat(reordered.practices())
            .filteredOn(practice -> java.util.Objects.equals(practice.areaSlug(), sourceArea))
            .extracting(practice -> practice.slug())
            .containsExactlyElementsOf(bucket);

        String movedSlug = bucket.getFirst();
        String destinationArea = reordered
            .areas()
            .stream()
            .map(CuratedAreaDTO::slug)
            .filter(slug -> !slug.equals(sourceArea))
            .findFirst()
            .orElseThrow();
        CuratedCatalogDTO moved = webTestClient
            .patch()
            .uri(CATALOG + "/practices/" + movedSlug + "/placement")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, quote(reordered.etag()));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new PlacePracticeRequestDTO(destinationArea, 0))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedCatalogDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(moved).isNotNull();
        assertThat(moved.practices())
            .filteredOn(practice -> practice.slug().equals(movedSlug))
            .singleElement()
            .satisfies(practice -> {
                assertThat(practice.areaSlug()).isEqualTo(destinationArea);
                assertThat(practice.position()).isZero();
                assertThat(practice.status().state()).isEqualTo(CatalogEntryState.EDITED_HERE);
            });
    }

    @Test
    @WithAdminUser
    void notOfferingAnAreaWithholdsThePracticesFiledUnderIt() {
        ensureAdminMembership(workspace);
        String catalogTag = tag(CATALOG);
        var result = webTestClient
            .patch()
            .uri(CATALOG + "/areas/" + AREA + "/status")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, catalogTag);
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateCuratedStatusRequestDTO(CuratedStatus.RETIRED))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedCatalogDTO.class)
            .returnResult();

        CuratedCatalogDTO updated = result.getResponseBody();
        assertThat(updated).isNotNull();
        assertThat(result.getResponseHeaders().getETag()).isEqualTo(quote(updated.etag()));

        assertThat(getPractice().status().offered()).isTrue();
        CuratedCatalogDTO catalog = webTestClient
            .get()
            .uri(CATALOG)
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedCatalogDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(catalog).isNotNull();
        assertThat(catalog.practices())
            .filteredOn(practice -> practice.slug().equals(PRACTICE))
            .singleElement()
            .extracting(practice -> practice.effectivelyOffered())
            .isEqualTo(false);
        int unavailableEntries = Math.toIntExact(
            catalog
                    .areas()
                    .stream()
                    .filter(areaEntry -> !areaEntry.status().offered())
                    .count() +
                catalog
                    .practices()
                    .stream()
                    .filter(practice -> !practice.effectivelyOffered())
                    .count()
        );
        assertThat(catalog.summary().notOffered()).isEqualTo(unavailableEntries);

        webTestClient
            .get()
            .uri(
                "/workspaces/{workspaceSlug}/practice-catalog/practices/{practiceSlug}",
                workspace.getWorkspaceSlug(),
                PRACTICE
            )
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void rejectsAnAreaStatusDecisionBasedOnStalePracticeMembership() {
        CuratedCatalogDTO before = getCatalog();
        String staleTag = quote(before.etag());
        String destinationArea = before
            .areas()
            .stream()
            .map(CuratedAreaDTO::slug)
            .filter(slug -> !slug.equals(AREA))
            .findFirst()
            .orElseThrow();

        webTestClient
            .patch()
            .uri(CATALOG + "/practices/" + PRACTICE + "/placement")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, staleTag);
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new PlacePracticeRequestDTO(destinationArea, 0))
            .exchange()
            .expectStatus()
            .isOk();

        webTestClient
            .patch()
            .uri(CATALOG + "/areas/" + AREA + "/status")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, staleTag);
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateCuratedStatusRequestDTO(CuratedStatus.RETIRED))
            .exchange()
            .expectStatus()
            .isEqualTo(412);
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
                    new CuratedAreaRequestDTO("House rules", "Ours alone", "Scale", "amber")
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
    void usesArtifactEvidenceBaselineWhenCreateOmitsDeclaration() {
        CuratedPracticeDTO template = getPractice();
        var source = definitionOf(template, "Server baseline criteria");
        var request = new CuratedPracticeRequestDTO(
            source.name(),
            source.artifactType(),
            source.triggerEvents(),
            source.criteria(),
            source.precomputeScript(),
            null,
            source.whyItMatters(),
            source.whatGoodLooksLike(),
            source.areaSlug()
        );

        CuratedPracticeDTO created = webTestClient
            .post()
            .uri(CATALOG + "/practices")
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new CreateCuratedPracticeRequestDTO("server-baseline", request))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(CuratedPracticeDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.definition().evidence()).isEqualTo(evidenceDefaults.forArtifact(source.artifactType()));
    }

    @Test
    void keepsRemovedDefaultsAsCustomEntries() {
        String practiceSlug = "removed-practice";
        CuratedPracticeDTO template = getPractice();
        webTestClient
            .post()
            .uri(CATALOG + "/practices")
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new CreateCuratedPracticeRequestDTO(practiceSlug, definitionOf(template, "Saved criteria")))
            .exchange()
            .expectStatus()
            .isCreated();

        String areaSlug = "removed-area";
        webTestClient
            .post()
            .uri(CATALOG + "/areas")
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                new CreateCuratedAreaRequestDTO(
                    areaSlug,
                    new CuratedAreaRequestDTO("Removed area", "Saved description", "Folder", "slate")
                )
            )
            .exchange()
            .expectStatus()
            .isCreated();

        jdbcTemplate.update(
            "UPDATE curated_practice_override SET based_on_digest = repeat('a', 64) WHERE slug = ?",
            practiceSlug
        );
        jdbcTemplate.update(
            "UPDATE curated_area_override SET based_on_digest = repeat('a', 64) WHERE slug = ?",
            areaSlug
        );

        CuratedPracticeDTO removedPractice = getPractice(practiceSlug);
        CuratedAreaDTO removedArea = getArea(areaSlug);
        assertThat(removedPractice.status().state()).isEqualTo(CatalogEntryState.NO_LONGER_SHIPPED);
        assertThat(removedArea.status().state()).isEqualTo(CatalogEntryState.NO_LONGER_SHIPPED);

        webTestClient
            .put()
            .uri(CATALOG + "/practices/" + practiceSlug + "/override/acknowledgement")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, "\"stale\"");
            })
            .exchange()
            .expectStatus()
            .isEqualTo(412);

        webTestClient
            .put()
            .uri(CATALOG + "/practices/" + practiceSlug + "/override/acknowledgement")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etagOf(removedPractice));
            })
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.status.state")
            .isEqualTo("YOURS");

        webTestClient
            .put()
            .uri(CATALOG + "/areas/" + areaSlug + "/override/acknowledgement")
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etagOf(removedArea));
            })
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.status.state")
            .isEqualTo("YOURS");

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM curated_practice_override WHERE slug = ? AND based_on_digest IS NULL",
                Long.class,
                practiceSlug
            )
        ).isOne();
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM curated_area_override WHERE slug = ? AND based_on_digest IS NULL",
                Long.class,
                areaSlug
            )
        ).isOne();
        assertThat(auditValues("CURATED_PRACTICE", practiceSlug)).anySatisfy(value ->
            assertThat(value).contains("YOURS")
        );
        assertThat(auditValues("CURATED_PRACTICE_AREA", areaSlug)).anySatisfy(value ->
            assertThat(value).contains("YOURS")
        );
    }

    @Test
    void customEntriesAppendWithoutTakingOwnershipOfTheShippedOrder() {
        CuratedCatalogDTO beforeArea = getCatalog();
        webTestClient
            .post()
            .uri(CATALOG + "/areas")
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                new CreateCuratedAreaRequestDTO(
                    "house-rules",
                    new CuratedAreaRequestDTO("House rules", "Ours alone", "Scale", "amber")
                )
            )
            .exchange()
            .expectStatus()
            .isCreated();

        CuratedCatalogDTO afterArea = getCatalog();
        assertThat(afterArea.areas())
            .extracting(CuratedAreaDTO::slug)
            .containsExactlyElementsOf(
                Stream.concat(beforeArea.areas().stream().map(CuratedAreaDTO::slug), Stream.of("house-rules")).toList()
            );
        assertThat(afterArea.areas())
            .extracting(CuratedAreaDTO::position)
            .containsExactlyElementsOf(IntStream.range(0, afterArea.areas().size()).boxed().toList());
        assertThat(afterArea.customOrder()).isFalse();
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM curated_area_override WHERE position IS NOT NULL",
                Long.class
            )
        ).isZero();

        CuratedPracticeDTO template = getPractice();
        List<String> beforePractices = afterArea
            .practices()
            .stream()
            .filter(practice -> java.util.Objects.equals(practice.areaSlug(), template.definition().areaSlug()))
            .map(practice -> practice.slug())
            .toList();
        webTestClient
            .post()
            .uri(CATALOG + "/practices")
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new CreateCuratedPracticeRequestDTO("house-practice", definitionOf(template, "House criteria")))
            .exchange()
            .expectStatus()
            .isCreated();

        List<CuratedPracticeSummaryDTO> afterPractices = getCatalog()
            .practices()
            .stream()
            .filter(practice -> java.util.Objects.equals(practice.areaSlug(), template.definition().areaSlug()))
            .toList();
        assertThat(afterPractices)
            .extracting(CuratedPracticeSummaryDTO::slug)
            .containsExactlyElementsOf(Stream.concat(beforePractices.stream(), Stream.of("house-practice")).toList());
        assertThat(afterPractices)
            .extracting(CuratedPracticeSummaryDTO::position)
            .containsExactlyElementsOf(IntStream.range(0, afterPractices.size()).boxed().toList());
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM curated_practice_override WHERE position IS NOT NULL",
                Long.class
            )
        ).isZero();
    }

    @Test
    @WithAdminUser
    void aWorkspaceCopyReportsWhereItCameFromAndWhetherItStillMatches() {
        ensureAdminMembership(workspace);
        events.publishEvent(new WorkspacesInitializedEvent(1));

        webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/practices/{practiceSlug}", workspace.getWorkspaceSlug(), PRACTICE)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.catalogOrigin.link")
            .isEqualTo("IN_SYNC")
            .jsonPath("$.catalogOrigin.sourceOffered")
            .isEqualTo(true);

        webTestClient
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
                    .contains("criteriaSha256", "state");
            });
    }

    @Test
    void auditsSuccessiveGuidanceOnlyEdits() {
        CuratedPracticeDTO original = getPractice();
        putPractice(etagOf(original), "Our own criteria").expectStatus().isOk();

        CuratedPracticeDTO edited = getPractice();
        CuratedPracticeRequestDTO guidanceEdit = new CuratedPracticeRequestDTO(
            edited.definition().name(),
            edited.definition().artifactType(),
            edited.definition().triggerEvents(),
            edited.definition().criteria(),
            edited.definition().precomputeScript(),
            edited.definition().evidence(),
            "Updated guidance",
            edited.definition().whatGoodLooksLike(),
            edited.definition().areaSlug()
        );
        webTestClient
            .put()
            .uri(CATALOG + "/practices/" + PRACTICE)
            .headers(headers -> {
                headers.setBearerAuth(ADMIN_TOKEN);
                headers.set(HttpHeaders.IF_MATCH, etagOf(edited));
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(guidanceEdit)
            .exchange()
            .expectStatus()
            .isOk();

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM config_audit_event WHERE entity_type = 'CURATED_PRACTICE' AND entity_id = ? AND action = 'UPDATED'",
                Long.class,
                PRACTICE
            )
        ).isEqualTo(2);
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
            practice.definition().evidence(),
            practice.definition().whyItMatters(),
            practice.definition().whatGoodLooksLike(),
            practice.definition().areaSlug()
        );
    }

    private CuratedPracticeDTO getPractice() {
        return getPractice(PRACTICE);
    }

    private CuratedPracticeDTO getPractice(String slug) {
        return webTestClient
            .get()
            .uri(CATALOG + "/practices/" + slug)
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedPracticeDTO.class)
            .returnResult()
            .getResponseBody();
    }

    private CuratedCatalogDTO getCatalog() {
        return webTestClient
            .get()
            .uri(CATALOG)
            .headers(headers -> headers.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CuratedCatalogDTO.class)
            .returnResult()
            .getResponseBody();
    }

    private static String quote(String etag) {
        return '"' + etag + '"';
    }

    private CuratedAreaDTO getArea() {
        return getArea(AREA);
    }

    private CuratedAreaDTO getArea(String slug) {
        return webTestClient
            .get()
            .uri(CATALOG + "/areas/" + slug)
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

    private List<String> auditValues(String entityType, String entityId) {
        return jdbcTemplate.queryForList(
            """
            SELECT new_value::text
            FROM config_audit_event
            WHERE entity_type = ? AND entity_id = ?
            ORDER BY id DESC
            """,
            String.class,
            entityType,
            entityId
        );
    }
}
