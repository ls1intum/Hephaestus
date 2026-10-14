package de.tum.cit.aet.hephaestus.practices.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;
import de.tum.cit.aet.hephaestus.practices.PracticeReleaseChoice;
import de.tum.cit.aet.hephaestus.practices.dto.AcceptPracticeReleaseRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogGroupInputDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import de.tum.cit.aet.hephaestus.practices.dto.MovePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class CatalogAdminControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    WebTestClient client;

    @Autowired
    CatalogService catalog;

    @Test
    @WithAdminUser
    void shouldMoveACatalogPracticeThroughTheGroupRoute() {
        persistUser("admin");
        String suffix = UUID.randomUUID().toString();
        String groupSlug = "move-group-" + suffix;
        String practiceSlug = "move-practice-" + suffix;
        catalog.createGroup(new CatalogGroupInputDTO(groupSlug, "Move group", null, null, null, null));
        var created = catalog.createPractice(new CatalogPracticeInputDTO(
                practiceSlug, "scm.pull_request", null, DefinitionTestFixtures.definition(), null));

        var response = client.patch()
                .uri("/admin/practice-catalog/practices/{slug}/group", practiceSlug)
                .headers(TestAuthUtils.withCurrentUser())
                .header("If-Match", EntityTagPrecondition.format(created.etag()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MovePracticeRequestDTO(groupSlug, 0))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(CatalogDTO.class)
                .returnResult();

        var moved = Objects.requireNonNull(response.getResponseBody()).practices().stream()
                .filter(practice -> practice.slug().equals(practiceSlug))
                .findFirst()
                .orElseThrow();
        assertThat(moved.groupSlug()).isEqualTo(groupSlug);
        assertThat(moved.position()).isZero();
        assertThat(catalog.practice(practiceSlug).effective().groupSlug()).isEqualTo(groupSlug);
    }

    @Test
    @WithAdminUser
    void shouldRejectAStaleWriteWithoutDisclosingTheCurrentETag() {
        persistUser("admin");
        String slug = "stale-" + UUID.randomUUID();
        var created = catalog.createPractice(
                new CatalogPracticeInputDTO(slug, "scm.pull_request", null, DefinitionTestFixtures.definition(), null));

        client.patch()
                .uri("/admin/practice-catalog/practices/{slug}/status", slug)
                .headers(TestAuthUtils.withCurrentUser())
                .header("If-Match", EntityTagPrecondition.format("stale"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("excluded", true))
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("This entry changed after you loaded it. Reload and try again.");

        var unchanged = catalog.practice(slug);
        assertThat(unchanged.excluded()).isFalse();
        assertThat(unchanged.etag()).isEqualTo(created.etag());
    }

    @Test
    @WithAdminUser
    void shouldRequireAValidInstanceCatalogDefinition() {
        persistUser("admin");
        String slug = "instance-" + UUID.randomUUID();
        client.post()
                .uri("/admin/practice-catalog/practices")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CatalogPracticeInputDTO(
                        slug, "scm.pull_request", null, DefinitionTestFixtures.definition(), null))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueMatches("Location", ".*/admin/practice-catalog/practices/" + slug)
                .expectBody()
                .jsonPath("$.kind")
                .isEqualTo("scm.pull_request")
                .jsonPath("$.definition.name")
                .isEqualTo(DefinitionTestFixtures.definition().name())
                .jsonPath("$.state")
                .isEqualTo("YOURS");
    }

    @Test
    @WithMentorUser
    void shouldRefuseInstanceCatalogWritesForAMember() {
        persistUser("mentor");
        client.post()
                .uri("/admin/practice-catalog/practices")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CatalogPracticeInputDTO(
                        "unauthorized", "scm.pull_request", null, DefinitionTestFixtures.definition(), null))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
    }

    static Stream<CatalogPracticeInputDTO> invalidPracticeInputs() {
        var definition = DefinitionTestFixtures.definition();
        return Stream.of(
                new CatalogPracticeInputDTO("Uppercase", "scm.pull_request", null, definition, null),
                new CatalogPracticeInputDTO("bad--slug", "scm.pull_request", null, definition, null),
                new CatalogPracticeInputDTO("invalid-position", "scm.pull_request", null, definition, -1));
    }

    @ParameterizedTest
    @MethodSource("invalidPracticeInputs")
    @WithAdminUser
    void shouldRejectInvalidCatalogInputsBeforeWriting(CatalogPracticeInputDTO input) {
        persistUser("admin");
        client.post()
                .uri("/admin/practice-catalog/practices")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(input)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(Void.class);
    }

    @Test
    @WithAdminUser
    void shouldRejectABlankGroupName() {
        persistUser("admin");
        client.post()
                .uri("/admin/practice-catalog/groups")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CatalogGroupInputDTO("invalid-name", " ", null, null, null, null))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(Void.class);
    }

    @Test
    @WithAdminUser
    void shouldRejectRepeatedReleaseFieldsAndAcceptOneExplicitChoice() {
        persistUser("admin");
        var source = catalog.effectiveCatalog().practices().stream()
                .filter(entry -> entry.shipped() != null)
                .findFirst()
                .orElseThrow();
        var local = DefinitionTestFixtures.withCriteria(source.effective().definition(), "Locally authored criteria.");
        var current = catalog.updatePractice(
                source.slug(),
                new CatalogPracticeInputDTO(
                        source.slug(),
                        source.effective().kind().value(),
                        source.effective().groupSlug(),
                        local,
                        source.position()),
                EntityTagPrecondition.parse(EntityTagPrecondition.format(source.etag())));
        var choice = Map.of("field", "CRITERIA", "source", "OFFERED");
        client.put()
                .uri("/admin/practice-catalog/practices/{slug}/release", source.slug())
                .headers(TestAuthUtils.withCurrentUser())
                .header("If-Match", EntityTagPrecondition.format(current.etag()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("choices", List.of(choice, choice)))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(Void.class);
        assertThat(catalog.practice(source.slug()).effective().definition()).isEqualTo(local);
        client.put()
                .uri("/admin/practice-catalog/practices/{slug}/release", source.slug())
                .headers(TestAuthUtils.withCurrentUser())
                .header("If-Match", EntityTagPrecondition.format(current.etag()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AcceptPracticeReleaseRequestDTO(List.of(new AcceptPracticeReleaseRequestDTO.Choice(
                        PracticeDefinitionField.CRITERIA, PracticeReleaseChoice.OFFERED))))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.definition.criteria")
                .isEqualTo(source.effective().definition().criteria());
    }
}
