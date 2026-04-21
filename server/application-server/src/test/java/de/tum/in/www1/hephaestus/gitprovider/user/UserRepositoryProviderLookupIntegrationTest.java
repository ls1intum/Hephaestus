package de.tum.in.www1.hephaestus.gitprovider.user;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for {@link UserRepository#findAllByProviderTypeAndNativeId(GitProviderType, Long)}.
 * <p>
 * This query is the foundation of claim-based identity resolution — exercising it against a
 * real schema catches errors that the service-layer unit tests (which mock the repository)
 * would miss, e.g. a broken association path in the JPQL.
 */
@DisplayName("UserRepository.findAllByProviderTypeAndNativeId")
class UserRepositoryProviderLookupIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @BeforeEach
    void resetState() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("returns the row for the requested provider type and native id")
    void findsRowByProviderTypeAndNativeId() {
        GitProvider github = persistProvider(GitProviderType.GITHUB, "https://github.com");
        GitProvider gitlab = persistProvider(GitProviderType.GITLAB, "https://gitlab.lrz.de");
        TestUserFactory.ensureUser(userRepository, "ghuser", 100L, github);
        TestUserFactory.ensureUser(userRepository, "gluser", 200L, gitlab);

        List<User> githubHits = userRepository.findAllByProviderTypeAndNativeId(GitProviderType.GITHUB, 100L);
        assertThat(githubHits).extracting(User::getLogin).containsExactly("ghuser");

        List<User> gitlabHits = userRepository.findAllByProviderTypeAndNativeId(GitProviderType.GITLAB, 200L);
        assertThat(gitlabHits).extracting(User::getLogin).containsExactly("gluser");
    }

    @Test
    @DisplayName("does not cross provider boundaries when native ids collide")
    void differentProvidersShareNativeIdSafely() {
        // Both rows use native_id=42, but on different providers — the unique key is
        // (provider_id, native_id), so they must coexist and queries must stay scoped.
        GitProvider github = persistProvider(GitProviderType.GITHUB, "https://github.com");
        GitProvider gitlab = persistProvider(GitProviderType.GITLAB, "https://gitlab.example");
        TestUserFactory.ensureUser(userRepository, "gh-user", 42L, github);
        TestUserFactory.ensureUser(userRepository, "gl-user", 42L, gitlab);

        List<User> github42 = userRepository.findAllByProviderTypeAndNativeId(GitProviderType.GITHUB, 42L);
        List<User> gitlab42 = userRepository.findAllByProviderTypeAndNativeId(GitProviderType.GITLAB, 42L);

        assertThat(github42).extracting(User::getLogin).containsExactly("gh-user");
        assertThat(gitlab42).extracting(User::getLogin).containsExactly("gl-user");
    }

    @Test
    @DisplayName("returns every matching row when the same native id is provisioned on multiple server instances")
    void returnsAllMatchesAcrossServerInstances() {
        // A single Keycloak session identifies one GitLab instance, but nothing in the
        // schema prevents two GitLab server URLs (e.g. gitlab.com and a self-hosted one)
        // from having rows with identical native ids. The query returns a list so callers
        // can aggregate rather than silently drop either row.
        GitProvider gitlabPublic = persistProvider(GitProviderType.GITLAB, "https://gitlab.com");
        GitProvider gitlabSelfHosted = persistProvider(GitProviderType.GITLAB, "https://gitlab.lrz.de");
        TestUserFactory.ensureUser(userRepository, "alice-public", 777L, gitlabPublic);
        TestUserFactory.ensureUser(userRepository, "alice-lrz", 777L, gitlabSelfHosted);

        List<User> hits = userRepository.findAllByProviderTypeAndNativeId(GitProviderType.GITLAB, 777L);
        assertThat(hits).hasSize(2).extracting(User::getLogin).containsExactlyInAnyOrder("alice-public", "alice-lrz");
    }

    @Test
    @DisplayName("returns empty when no row matches")
    void returnsEmptyWhenNoMatch() {
        persistProvider(GitProviderType.GITHUB, "https://github.com");

        assertThat(userRepository.findAllByProviderTypeAndNativeId(GitProviderType.GITHUB, 999L)).isEmpty();
    }

    private GitProvider persistProvider(GitProviderType type, String serverUrl) {
        return gitProviderRepository
            .findByTypeAndServerUrl(type, serverUrl)
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(type, serverUrl)));
    }
}
