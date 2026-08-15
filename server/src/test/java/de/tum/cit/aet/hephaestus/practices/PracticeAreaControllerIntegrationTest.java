package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.audit.ConfigAuditEventRepository;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditFilter;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReorderPracticeAreasRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeReviewTierRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierSource;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Access-control coverage for {@link PracticeAreaController}.
 *
 * <p>Read operations are annotated {@code @SecurityRequirements} (any workspace member); the five
 * mutating operations (create / update / review-tier / reorder / delete) are
 * {@code @RequireAtLeastWorkspaceAdmin}. These tests assert that a plain workspace MEMBER is forbidden on
 * every mutation and permitted on reads, and that anonymous callers are rejected. Functional CRUD
 * behaviour for the bind endpoint lives on {@code PracticeCatalogControllerIntegrationTest}; the
 * review-tier PATCH's own behaviour is pinned here, because the area is the middle level of the
 * inheritance chain and nothing else exercises it.
 */
class PracticeAreaControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String BASE_URI = "/workspaces/{workspaceSlug}/practice-areas";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeAreaRepository areaRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ConfigAuditEventRepository configAuditEventRepository;

    private Workspace workspace;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("area-owner");
        workspace = createWorkspace("area-ws", "Area WS", "area-org", AccountType.ORG, owner);
    }

    private PracticeArea persistArea(String slug, String name) {
        PracticeArea area = new PracticeArea();
        area.setWorkspace(workspace);
        area.setSlug(slug);
        area.setName(name);
        return areaRepository.save(area);
    }

    private CreatePracticeAreaRequestDTO validCreateRequest(String slug) {
        return new CreatePracticeAreaRequestDTO(slug, "Area " + slug, "Develops " + slug, null, "Package", "sky");
    }

    /** Registers the current {@code @WithMentorUser} principal as a plain workspace MEMBER. */
    private void asMember() {
        User member = persistUser("mentor");
        ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);
    }

    // LIST — read, member-allowed (@SecurityRequirements)

    @Nested
    @DisplayName("GET /practice-areas")
    class ListAreas {

        @Test
        @WithAdminUser
        void shouldReturnAreasForAdmin() {
            ensureAdminMembership(workspace);
            persistArea("alpha", "Alpha");
            persistArea("beta", "Beta");

            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(2);
        }

        @Test
        @WithMentorUser
        @DisplayName("allows a plain workspace member to list")
        void shouldAllowMemberToList() {
            asMember();
            persistArea("member-visible", "Visible");

            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1);
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            webTestClient.get().uri(BASE_URI, workspace.getWorkspaceSlug()).exchange().expectStatus().isUnauthorized();
        }
    }

    // GET SINGLE — read, member-allowed (@SecurityRequirements)

    @Nested
    @DisplayName("GET /practice-areas/{areaSlug}")
    class GetArea {

        @Test
        @WithMentorUser
        @DisplayName("allows a plain workspace member to get an area")
        void shouldAllowMemberToGet() {
            asMember();
            persistArea("member-get", "Member Get");

            webTestClient
                .get()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "member-get")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.slug")
                .isEqualTo("member-get");
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            webTestClient
                .get()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "any-slug")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // CREATE — @RequireAtLeastWorkspaceAdmin

    @Nested
    @DisplayName("POST /practice-areas")
    class CreateArea {

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from creating an area")
        void shouldReturn403ForNonAdmin() {
            asMember();

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("forbidden-area"))
                .exchange()
                .expectStatus()
                .isForbidden();

            assertThat(areaRepository.existsByWorkspaceIdAndSlug(workspace.getId(), "forbidden-area")).isFalse();
        }

        @Test
        @DisplayName("rejects anonymous create (403 via CSRF gate, before auth)")
        void shouldRejectAnonymousCreate() {
            // Anonymous POST → double-submit CSRF gate (ADR 0017) rejects 403 before auth (no
            // X-XSRF-TOKEN). The create stays blocked for anonymous callers.
            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("anon-area"))
                .exchange()
                .expectStatus()
                .isForbidden();
        }
    }

    // UPDATE — @RequireAtLeastWorkspaceAdmin

    @Nested
    @DisplayName("PATCH /practice-areas/{areaSlug}")
    class UpdateArea {

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from updating an area")
        void shouldReturn403ForNonAdmin() {
            asMember();
            persistArea("forbidden-update", "Original");

            var request = new UpdatePracticeAreaRequestDTO("Hacked Name", null, null, null, null, null);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "forbidden-update")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();

            PracticeArea persisted = areaRepository
                .findByWorkspaceIdAndSlug(workspace.getId(), "forbidden-update")
                .orElseThrow();
            assertThat(persisted.getName()).isEqualTo("Original");
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            var request = new UpdatePracticeAreaRequestDTO("Name", null, null, null, null, null);

            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "any-slug")
                .headers(TestAuthUtils.withCsrf(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // REORDER — @RequireAtLeastWorkspaceAdmin

    @Nested
    @DisplayName("PATCH /practice-areas/reorder")
    class ReorderAreas {

        @Test
        @WithAdminUser
        @DisplayName("reorders areas for an admin and persists the new display order")
        void shouldReorderForAdmin() {
            ensureAdminMembership(workspace);
            persistArea("first", "First");
            persistArea("second", "Second");

            var request = new ReorderPracticeAreasRequestDTO(List.of("second", "first"));

            webTestClient
                .patch()
                .uri(BASE_URI + "/reorder", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].slug")
                .isEqualTo("second")
                .jsonPath("$[1].slug")
                .isEqualTo("first");

            assertThat(
                areaRepository.findByWorkspaceIdAndSlug(workspace.getId(), "second").orElseThrow().getDisplayOrder()
            ).isZero();
            assertThat(
                areaRepository.findByWorkspaceIdAndSlug(workspace.getId(), "first").orElseThrow().getDisplayOrder()
            ).isEqualTo(1);
        }

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from reordering areas")
        void shouldReturn403ForNonAdmin() {
            asMember();
            persistArea("first", "First");
            persistArea("second", "Second");

            var request = new ReorderPracticeAreasRequestDTO(List.of("second", "first"));

            webTestClient
                .patch()
                .uri(BASE_URI + "/reorder", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            var request = new ReorderPracticeAreasRequestDTO(List.of("first", "second"));

            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .patch()
                .uri(BASE_URI + "/reorder", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCsrf(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // FUNCTIONAL — conflict / not-found / validation paths an admin can hit

    @Nested
    @DisplayName("functional admin paths (conflict / not-found / validation / tenancy)")
    class FunctionalAdminPaths {

        @Test
        @WithAdminUser
        @DisplayName("POST a duplicate slug returns 409")
        void createDuplicateSlugConflicts() {
            ensureAdminMembership(workspace);
            persistArea("dup", "Existing");

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("dup"))
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        }

        @Test
        @WithAdminUser
        @DisplayName("GET an unknown slug returns 404")
        void getUnknownSlugNotFound() {
            ensureAdminMembership(workspace);

            webTestClient
                .get()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "does-not-exist")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithAdminUser
        @DisplayName("PATCH an unknown slug returns 404")
        void patchUnknownSlugNotFound() {
            ensureAdminMembership(workspace);
            var request = new UpdatePracticeAreaRequestDTO("New Name", null, null, null, null, null);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "does-not-exist")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithAdminUser
        @DisplayName("DELETE an unknown slug returns 404")
        void deleteUnknownSlugNotFound() {
            ensureAdminMembership(workspace);

            webTestClient
                .delete()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "does-not-exist")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithAdminUser
        @DisplayName("PATCH /reorder with a duplicate slug returns 400")
        void reorderDuplicateSlugIsBadRequest() {
            ensureAdminMembership(workspace);
            persistArea("only", "Only");

            var request = new ReorderPracticeAreasRequestDTO(List.of("only", "only"));

            webTestClient
                .patch()
                .uri(BASE_URI + "/reorder", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("PATCH /reorder with an unknown slug returns 404")
        void reorderUnknownSlugNotFound() {
            ensureAdminMembership(workspace);
            persistArea("known", "Known");

            var request = new ReorderPracticeAreasRequestDTO(List.of("known", "ghost"));

            webTestClient
                .patch()
                .uri(BASE_URI + "/reorder", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithAdminUser
        @DisplayName("an area in another workspace is not readable via this workspace's slug (tenancy 404)")
        void crossWorkspaceAreaIsNotLeaked() {
            ensureAdminMembership(workspace);

            // Persist an area that belongs ONLY to a second workspace.
            User otherOwner = persistUser("other-owner");
            Workspace other = createWorkspace("other-ws", "Other WS", "other-org", AccountType.ORG, otherOwner);
            PracticeArea foreign = new PracticeArea();
            foreign.setWorkspace(other);
            foreign.setSlug("foreign-area");
            foreign.setName("Foreign");
            areaRepository.save(foreign);

            // Reading it through THIS workspace's slug must 404 — it is scoped to the other workspace.
            webTestClient
                .get()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "foreign-area")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();
        }
    }

    // DELETE — @RequireAtLeastWorkspaceAdmin

    @Nested
    @DisplayName("DELETE /practice-areas/{areaSlug}")
    class DeleteArea {

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from deleting an area")
        void shouldReturn403ForNonAdmin() {
            asMember();
            persistArea("forbidden-delete", "Keep Me");

            webTestClient
                .delete()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "forbidden-delete")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();

            assertThat(areaRepository.existsByWorkspaceIdAndSlug(workspace.getId(), "forbidden-delete")).isTrue();
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .delete()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "any-slug")
                .headers(TestAuthUtils.withCsrf(csrf))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // PATCH /{areaSlug}/review-tier — @RequireAtLeastWorkspaceAdmin

    /**
     * The middle level of the practice → area → workspace chain, and the only one an administrator reaches
     * to move a dozen practices at once. Two behaviours make it the middle level rather than a second copy
     * of the practice endpoint: a null clears the area's own answer back to the workspace's, and re-sending
     * the tier already in force does nothing at all — including to the audit ledger, which is what
     * "nothing" has to mean for a decision that is recorded.
     */
    @Nested
    @DisplayName("PATCH /practice-areas/{areaSlug}/review-tier")
    class SetAreaReviewTier {

        private PracticeArea persistAreaAt(String slug, @Nullable PracticeReviewTier tier) {
            PracticeArea area = persistArea(slug, "Area " + slug);
            area.setReviewTier(tier);
            return areaRepository.save(area);
        }

        /** Gives the workspace an opinion of its own, so "inherit" has something to resolve to. */
        private void workspaceDefaultsTo(PracticeReviewTier tier) {
            Workspace stored = workspaceRepository.findById(workspace.getId()).orElseThrow();
            stored.getReviewSettings().applyDefaultReviewTier(tier.name());
            workspaceRepository.save(stored);
        }

        private @Nullable PracticeReviewTier storedTierOf(String slug) {
            return areaRepository.findByWorkspaceIdAndSlug(workspace.getId(), slug).orElseThrow().getReviewTier();
        }

        /**
         * How many config-audit rows this one area has accumulated. Read through the workspace-scoped
         * query rather than a bare {@code count()}: {@code config_audit_event} is workspace-scoped, and
         * {@code WorkspaceStatementInspector} rejects a statement that reaches it without a
         * {@code workspace_id} predicate — in a test just as in production.
         */
        private long auditedChangesTo(PracticeArea area) {
            return configAuditEventRepository
                .findForWorkspace(
                    workspace.getId(),
                    new ConfigAuditFilter(
                        List.of(ConfigAuditEntityType.PRACTICE_AREA),
                        String.valueOf(area.getId()),
                        null,
                        null,
                        null,
                        null,
                        null
                    ),
                    PageRequest.of(0, 50)
                )
                .getTotalElements();
        }

        /**
         * Every rung is settable on an area, and setting one is the area's own decision rather than an
         * inheritance. PROPOSE is in the list on purpose: refusing the middle rung would leave OFF and
         * DELIVER, an on/off switch, which is the defect the tier chain exists to remove.
         */
        @ParameterizedTest
        @EnumSource(PracticeReviewTier.class)
        @WithAdminUser
        @DisplayName("sets the area's own tier and reports it as the area's decision, not an inheritance")
        void shouldSetTheAreasOwnTier(PracticeReviewTier tier) {
            ensureAdminMembership(workspace);
            persistAreaAt("decides", null);

            PracticeAreaDTO result = webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}/review-tier", workspace.getWorkspaceSlug(), "decides")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeReviewTierRequestDTO(tier))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeAreaDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.reviewTier().effective()).isEqualTo(tier);
            assertThat(result.reviewTier().override()).isEqualTo(tier);
            assertThat(result.reviewTier().source()).isEqualTo(ReviewTierSource.AREA);
            assertThat(result.reviewTier().inherited()).isFalse();
            assertThat(storedTierOf("decides")).isEqualTo(tier);
        }

        @Test
        @WithAdminUser
        @DisplayName("an explicit null clears the area's tier back to inheriting the workspace's")
        void shouldClearToInheritOnExplicitNull() {
            ensureAdminMembership(workspace);
            workspaceDefaultsTo(PracticeReviewTier.PROPOSE);
            persistAreaAt("clear-me", PracticeReviewTier.OFF);

            PracticeAreaDTO result = webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}/review-tier", workspace.getWorkspaceSlug(), "clear-me")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reviewTier\": null}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeAreaDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.reviewTier().override()).isNull();
            assertThat(result.reviewTier().effective()).isEqualTo(PracticeReviewTier.PROPOSE);
            assertThat(result.reviewTier().source()).isEqualTo(ReviewTierSource.WORKSPACE);
            assertThat(result.reviewTier().inherited()).isTrue();
            assertThat(storedTierOf("clear-me")).isNull();
        }

        /**
         * An absent key clears too. This endpoint carries one field, so "not sent" cannot mean "leave it
         * alone" without leaving no way at all to express a clear — unlike the settings PATCH, where absent
         * means no change and a clear is named in a {@code reset} set.
         */
        @Test
        @WithAdminUser
        @DisplayName("an absent tier key clears just as an explicit null does")
        void shouldClearToInheritWhenTheKeyIsAbsent() {
            ensureAdminMembership(workspace);
            workspaceDefaultsTo(PracticeReviewTier.OFF);
            persistAreaAt("absent-key", PracticeReviewTier.DELIVER);

            PracticeAreaDTO result = webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}/review-tier", workspace.getWorkspaceSlug(), "absent-key")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeAreaDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.reviewTier().override()).isNull();
            assertThat(result.reviewTier().effective()).isEqualTo(PracticeReviewTier.OFF);
            assertThat(result.reviewTier().source()).isEqualTo(ReviewTierSource.WORKSPACE);
            assertThat(storedTierOf("absent-key")).isNull();
        }

        /**
         * Re-sending the tier already in force short-circuits before the write. The observable is the audit
         * ledger: a config change that did not happen must not be recorded as one, or a reviewer reading
         * the ledger sees a decision nobody made.
         */
        @Test
        @WithAdminUser
        @DisplayName("re-sending the tier already in force records nothing in the audit ledger")
        void shouldBeIdempotentAndNotAudited() {
            ensureAdminMembership(workspace);
            PracticeArea area = persistAreaAt("unchanged", PracticeReviewTier.PROPOSE);
            long auditedBefore = auditedChangesTo(area);

            PracticeAreaDTO result = webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}/review-tier", workspace.getWorkspaceSlug(), "unchanged")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeReviewTierRequestDTO(PracticeReviewTier.PROPOSE))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeAreaDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.reviewTier().effective()).isEqualTo(PracticeReviewTier.PROPOSE);
            assertThat(result.reviewTier().override()).isEqualTo(PracticeReviewTier.PROPOSE);
            assertThat(storedTierOf("unchanged")).isEqualTo(PracticeReviewTier.PROPOSE);
            assertThat(auditedChangesTo(area)).isEqualTo(auditedBefore);
        }

        /** A real change, by contrast, is recorded — otherwise the assertion above would pass vacuously. */
        @Test
        @WithAdminUser
        @DisplayName("a real change IS recorded in the audit ledger")
        void shouldAuditARealChange() {
            ensureAdminMembership(workspace);
            PracticeArea area = persistAreaAt("changes", PracticeReviewTier.PROPOSE);
            long auditedBefore = auditedChangesTo(area);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}/review-tier", workspace.getWorkspaceSlug(), "changes")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeReviewTierRequestDTO(PracticeReviewTier.OFF))
                .exchange()
                .expectStatus()
                .isOk();

            assertThat(auditedChangesTo(area)).isEqualTo(auditedBefore + 1);
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 404 for an unknown area")
        void shouldReturn404ForUnknownArea() {
            ensureAdminMembership(workspace);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}/review-tier", workspace.getWorkspaceSlug(), "no-such-area")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeReviewTierRequestDTO(PracticeReviewTier.OFF))
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from setting an area's tier")
        void shouldReturn403ForNonAdmin() {
            asMember();
            persistAreaAt("forbidden-tier", PracticeReviewTier.DELIVER);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}/review-tier", workspace.getWorkspaceSlug(), "forbidden-tier")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeReviewTierRequestDTO(PracticeReviewTier.OFF))
                .exchange()
                .expectStatus()
                .isForbidden();

            assertThat(storedTierOf("forbidden-tier")).isEqualTo(PracticeReviewTier.DELIVER);
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}/review-tier", workspace.getWorkspaceSlug(), "any-slug")
                .headers(TestAuthUtils.withCsrf(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeReviewTierRequestDTO(PracticeReviewTier.OFF))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }
}
