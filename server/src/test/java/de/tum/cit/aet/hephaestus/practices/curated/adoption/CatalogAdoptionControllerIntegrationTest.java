package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

@Tag("integration")
class CatalogAdoptionControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String PRACTICE = "describe-what-and-why";
    private static final String AREA = "review-ready-work";
    private static final String BASE = "/workspaces/{workspaceSlug}/practice-catalog/adoption";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository revisionRepository;

    @Autowired
    private PracticeAreaRepository areaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        User owner = persistUser("adoption-owner");
        workspace = createWorkspace("adoption", "Adoption", "adoption", AccountType.ORG, owner);
    }

    @Test
    @WithAdminUser
    void workspaceCreationDoesNotInstallCatalogEntries() {
        ensureAdminMembership(workspace);

        assertThat(practiceRepository.existsByWorkspaceId(workspace.getId())).isFalse();
        webTestClient
            .get()
            .uri(BASE, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueMatches(HttpHeaders.CACHE_CONTROL, ".*no-store.*private.*|.*private.*no-store.*")
            .expectBody()
            .jsonPath("$[?(@.slug == '" + PRACTICE + "')].availability")
            .isEqualTo("AVAILABLE");
    }

    @Test
    @WithAdminUser
    void previewIncludesDefinitionAreaOutcomeProposeTierAndValidation() {
        ensureAdminMembership(workspace);

        webTestClient
            .get()
            .uri(BASE + "/" + PRACTICE, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .exists(HttpHeaders.ETAG)
            .expectBody()
            .jsonPath("$.definition.criteria")
            .isNotEmpty()
            .jsonPath("$.definition.bindings.length()")
            .value(value -> assertThat((Integer) value).isPositive())
            .jsonPath("$.area.disposition")
            .isEqualTo("CREATE_CATALOG_AREA")
            .jsonPath("$.area.slug")
            .isEqualTo(AREA)
            .jsonPath("$.area.definition.name")
            .isNotEmpty()
            .jsonPath("$.initialReviewTier")
            .isEqualTo("PROPOSE")
            .jsonPath("$.sourceReviewRuleFingerprint")
            .value(value -> assertThat((String) value).matches("v3:[0-9a-f]{64}"))
            .jsonPath("$.definition.automatedReviewValidation.status")
            .isEqualTo("AUTHOR_DECLARED");
    }

    @Test
    @WithAdminUser
    void adoptionRejectsMissingOrInvalidPreviewEtags() {
        ensureAdminMembership(workspace);

        webTestClient
            .post()
            .uri(BASE + "/" + PRACTICE, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isEqualTo(428)
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(428)
            .jsonPath("$.title")
            .isEqualTo("Practice adoption preview required");

        webTestClient
            .post()
            .uri(BASE + "/" + PRACTICE, workspace.getWorkspaceSlug())
            .headers(headers -> {
                TestAuthUtils.withCurrentUser().accept(headers);
                headers.set(HttpHeaders.IF_MATCH, "\"stale\"");
            })
            .exchange()
            .expectStatus()
            .isEqualTo(412)
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(412)
            .jsonPath("$.title")
            .isEqualTo("Practice adoption preview changed");

        adopt("*").expectStatus().isEqualTo(412);
        adopt("W/" + previewEtag()).expectStatus().isEqualTo(412);
        adopt("malformed").expectStatus().isEqualTo(412);
    }

    @Test
    @WithAdminUser
    void adoptsOneIndependentProposeCopyWithProvenanceRevisionAndAudit() {
        ensureAdminMembership(workspace);
        String etag = previewEtag();

        webTestClient
            .post()
            .uri(BASE + "/" + PRACTICE, workspace.getWorkspaceSlug())
            .headers(headers -> {
                TestAuthUtils.withCurrentUser().accept(headers);
                headers.set(HttpHeaders.IF_MATCH, etag);
            })
            .exchange()
            .expectStatus()
            .isCreated()
            .expectHeader()
            .valueMatches(HttpHeaders.LOCATION, ".*/workspaces/adoption/practices/" + PRACTICE)
            .expectBody()
            .jsonPath("$.reviewTier.effective")
            .isEqualTo("PROPOSE")
            .jsonPath("$.catalogOrigin.link")
            .isEqualTo("IN_SYNC")
            .jsonPath("$.catalogOrigin.slug")
            .isEqualTo(PRACTICE);

        var practice = practiceRepository.findByWorkspaceIdAndSlug(workspace.getId(), PRACTICE).orElseThrow();
        assertThat(practice.getSourceCuratedSlug()).isEqualTo(PRACTICE);
        assertThat(practice.getSourceCuratedFingerprint()).matches("v3:[0-9a-f]{64}");
        assertThat(practice.getReviewTier()).isEqualTo(PracticeReviewTier.PROPOSE);
        assertThat(areaRepository.findByWorkspaceIdAndSlug(workspace.getId(), AREA)).isPresent();
        assertThat(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(practice.getId()))
            .get()
            .extracting(revision -> revision.getReviewRuleFingerprint())
            .asString()
            .matches("v3:[0-9a-f]{64}");
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM config_audit_event WHERE workspace_id = ? AND entity_type IN ('PRACTICE_AREA', 'PRACTICE_DEFINITION', 'PRACTICE_USAGE')",
                Long.class,
                workspace.getId()
            )
        ).isEqualTo(3);
        String practiceSnapshot = jdbcTemplate.queryForObject(
            "SELECT new_value::text FROM config_audit_event WHERE workspace_id = ? AND entity_type = 'PRACTICE_DEFINITION'",
            String.class,
            workspace.getId()
        );
        assertThat(practiceSnapshot).contains(PRACTICE, "sourceCuratedSlug", "sourceCuratedFingerprint");
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT new_value::text FROM config_audit_event WHERE workspace_id = ? AND entity_type = 'PRACTICE_USAGE'",
                String.class,
                workspace.getId()
            )
        ).contains("PROPOSE");
    }

    @Test
    @WithAdminUser
    void duplicateAdoptionReturnsConflictWithoutCreatingAnotherCopy() {
        ensureAdminMembership(workspace);
        String etag = previewEtag();
        adopt(etag).expectStatus().isCreated();
        var practice = practiceRepository.findByWorkspaceIdAndSlug(workspace.getId(), PRACTICE).orElseThrow();
        String sourceFingerprint = practice.getSourceCuratedFingerprint();

        String adoptedPreviewEtag = previewEtag();
        adopt(adoptedPreviewEtag)
            .expectStatus()
            .isEqualTo(409)
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(409);

        assertThat(practiceRepository.findByWorkspaceIdAndSlug(workspace.getId(), PRACTICE))
            .get()
            .extracting(candidate -> candidate.getSourceCuratedFingerprint())
            .isEqualTo(sourceFingerprint);
        assertThat(practiceRepository.findAllForCatalog(workspace.getId())).hasSize(1);
    }

    @Test
    @WithAdminUser
    void concurrentAdoptersCreateExactlyOneCompleteCopy() throws Exception {
        ensureAdminMembership(workspace);
        String etag = previewEtag();
        String token = TestAuthUtils.getCurrentUserToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = List.of(
                executor.submit(() -> adoptAfter(ready, start, etag, token)),
                executor.submit(() -> adoptAfter(ready, start, etag, token))
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).as("both adoption requests are ready").isTrue();
            start.countDown();

            assertThat(
                List.of(attempts.get(0).get(30, TimeUnit.SECONDS), attempts.get(1).get(30, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.PRECONDITION_FAILED);
        }

        assertThat(practiceRepository.findAllForCatalog(workspace.getId())).hasSize(1);
        assertThat(areaRepository.findByWorkspaceIdAndSlug(workspace.getId(), AREA)).isPresent();
        var practice = practiceRepository.findByWorkspaceIdAndSlug(workspace.getId(), PRACTICE).orElseThrow();
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM practice_revision WHERE practice_id = ?",
                Long.class,
                practice.getId()
            )
        ).isEqualTo(1);
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM config_audit_event WHERE workspace_id = ? AND entity_type IN ('PRACTICE_AREA', 'PRACTICE_DEFINITION', 'PRACTICE_USAGE')",
                Long.class,
                workspace.getId()
            )
        ).isEqualTo(3);
    }

    @Test
    @WithMentorUser
    void memberCannotReadReviewRulesOrAdopt() {
        User member = persistUser("mentor");
        ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);

        webTestClient
            .get()
            .uri(BASE, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();

        webTestClient
            .get()
            .uri(BASE + "/" + PRACTICE, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();

        webTestClient
            .post()
            .uri(BASE + "/" + PRACTICE, workspace.getWorkspaceSlug())
            .headers(headers -> {
                TestAuthUtils.withCurrentUser().accept(headers);
                headers.set(HttpHeaders.IF_MATCH, "\"untrusted\"");
            })
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    private String previewEtag() {
        return webTestClient
            .get()
            .uri(BASE + "/" + PRACTICE, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(CatalogPracticePreviewDTO.class)
            .getResponseHeaders()
            .getETag();
    }

    private WebTestClient.ResponseSpec adopt(String etag) {
        return webTestClient
            .post()
            .uri(BASE + "/" + PRACTICE, workspace.getWorkspaceSlug())
            .headers(headers -> {
                TestAuthUtils.withCurrentUser().accept(headers);
                headers.set(HttpHeaders.IF_MATCH, etag);
            })
            .exchange();
    }

    private HttpStatus adoptAfter(CountDownLatch ready, CountDownLatch start, String etag, String token)
        throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent adoption");
        }
        return (HttpStatus) webTestClient
            .post()
            .uri(BASE + "/" + PRACTICE, workspace.getWorkspaceSlug())
            .headers(headers -> {
                headers.setBearerAuth(token);
                headers.set(HttpHeaders.IF_MATCH, etag);
            })
            .exchange()
            .returnResult(Void.class)
            .getStatus();
    }
}
