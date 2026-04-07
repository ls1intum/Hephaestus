package de.tum.in.www1.hephaestus.account;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureWebTestClient
@DisplayName("Account controller integration")
class AccountControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitLabProperties gitLabProperties;

    @Test
    @Transactional
    @DisplayName("GET /user/settings provisions a GitLab user from JWT claims when no user row exists yet")
    void getUserSettingsProvisionsGitLabUserWhenMissing() {
        assertThat(userRepository.findByLogin("gitlabuser")).isEmpty();

        webTestClient
            .get()
            .uri("/user/settings")
            .headers(headers -> headers.setBearerAuth("mock-jwt-token-for-gitlab-user"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.aiReviewEnabled")
            .isEqualTo(true)
            .jsonPath("$.participateInResearch")
            .isEqualTo(true);

        var provisionedUser = userRepository.findByLogin("gitlabuser");
        assertThat(provisionedUser).isPresent();
        assertThat(provisionedUser.orElseThrow().getNativeId()).isEqualTo(18024L);
        assertThat(provisionedUser.orElseThrow().getProvider().getType()).isEqualTo(GitProviderType.GITLAB);
        assertThat(provisionedUser.orElseThrow().getProvider().getServerUrl()).isEqualTo(
            gitLabProperties.defaultServerUrl()
        );
    }
}
