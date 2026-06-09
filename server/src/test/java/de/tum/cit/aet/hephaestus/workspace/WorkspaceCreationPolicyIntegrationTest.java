package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies the {@code hephaestus.workspace.creation-policy=ADMIN_ONLY} gate on {@code POST /workspaces}.
 * The rest of the suite runs under {@code SELF_SERVICE} (see {@code application-test.yml}); this class
 * pins ADMIN_ONLY to prove the actor gate blocks non-admins and admits instance admins.
 */
@TestPropertySource(properties = "hephaestus.workspace.creation-policy=ADMIN_ONLY")
class WorkspaceCreationPolicyIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private static CreateWorkspaceRequestDTO githubRequest(String slug, Long ownerId) {
        return new CreateWorkspaceRequestDTO(
            slug,
            "Policy Space",
            "policy-org",
            AccountType.ORG,
            ownerId,
            IntegrationKind.GITHUB,
            "ghp_dummy_token_for_test",
            null
        );
    }

    @Test
    @WithUser
    void adminOnlyPolicyForbidsNonAdminCreation() {
        User owner = persistUser("policy-nonadmin");

        webTestClient
            .post()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(githubRequest("policy-forbidden", owner.getId()))
            .exchange()
            .expectStatus()
            .isForbidden();

        assertThat(workspaceRepository.count()).isZero();
    }

    @Test
    @WithAdminUser
    void adminOnlyPolicyAdmitsInstanceAdmin() {
        User owner = persistUser("policy-admin");

        webTestClient
            .post()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(githubRequest("policy-allowed", owner.getId()))
            .exchange()
            .expectStatus()
            .isCreated();

        assertThat(workspaceRepository.count()).isEqualTo(1);
    }
}
