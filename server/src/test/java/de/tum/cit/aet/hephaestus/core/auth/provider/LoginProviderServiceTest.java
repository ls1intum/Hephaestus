package de.tum.cit.aet.hephaestus.core.auth.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.AuthPropertiesFixture;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pins the env → seed contract: a configured login provider is seeded once when absent, never
 * re-seeded when present (so an admin's later edits aren't clobbered), and unconfigured providers
 * are skipped. Providers are keyed by a stable, operator-chosen {@code registrationId} (e.g.
 * {@code gitlab-lrz}) and several seed in one boot.
 */
class LoginProviderServiceTest extends BaseUnitTest {

    private final LoginProviderRepository repository = mock(LoginProviderRepository.class);
    private final LoginProviderClientRegistrationRepository registrationCache = mock(
        LoginProviderClientRegistrationRepository.class
    );

    private LoginProviderService service(Map<String, AuthProperties.LoginProviderSeed> providers) {
        return new LoginProviderService(
            repository,
            registrationCache,
            AuthPropertiesFixture.withLoginProviders(providers)
        );
    }

    private LoginProviderService adminService() {
        return service(Map.of());
    }

    private static AuthProperties.LoginProviderSeed githubSeed(String clientId, String secret) {
        return new AuthProperties.LoginProviderSeed(LoginProvider.ProviderType.GITHUB, "", clientId, secret, "");
    }

    private static AuthProperties.LoginProviderSeed gitlabSeed(
        String clientId,
        String secret,
        String baseUrl,
        String displayName
    ) {
        return new AuthProperties.LoginProviderSeed(
            LoginProvider.ProviderType.GITLAB,
            baseUrl,
            clientId,
            secret,
            displayName
        );
    }

    private static LoginProviderService.Draft gitlabDraft(String registrationId, String baseUrl, String scopes) {
        return new LoginProviderService.Draft(
            registrationId,
            LoginProvider.ProviderType.GITLAB,
            "Self-hosted GitLab",
            baseUrl,
            "client-id",
            "client-secret",
            scopes
        );
    }

    @Test
    void seedsConfiguredGithubWhenAbsent() {
        when(repository.existsByRegistrationId(any())).thenReturn(false);

        service(Map.of("github", githubSeed("client-id", "secret"))).seedFromEnvOnStartup();

        ArgumentCaptor<LoginProvider> captor = ArgumentCaptor.forClass(LoginProvider.class);
        verify(repository).save(captor.capture());
        LoginProvider seeded = captor.getValue();
        assertThat(seeded.getRegistrationId()).isEqualTo("github");
        assertThat(seeded.getType()).isEqualTo(LoginProvider.ProviderType.GITHUB);
        assertThat(seeded.getBaseUrl()).isEqualTo("https://github.com");
        assertThat(seeded.getScopes()).isEqualTo("read:user user:email"); // defaulted by type
        assertThat(seeded.isSeededFromEnv()).isTrue();
        assertThat(seeded.isEnabled()).isTrue();
    }

    @Test
    void seedsSelfHostedGitlabUnderItsDescriptiveRegistrationId() {
        when(repository.existsByRegistrationId(any())).thenReturn(false);
        // The whole point of the map: a self-hosted GitLab keeps a descriptive, stable id (gitlab-lrz),
        // not a forced generic "gitlab" — so several GitLab instances can coexist on one Hephaestus.

        service(
            Map.of("gitlab-lrz", gitlabSeed("client-id", "secret", "https://gitlab.lrz.de", "GitLab LRZ"))
        ).seedFromEnvOnStartup();

        ArgumentCaptor<LoginProvider> captor = ArgumentCaptor.forClass(LoginProvider.class);
        verify(repository).save(captor.capture());
        LoginProvider seeded = captor.getValue();
        assertThat(seeded.getRegistrationId()).isEqualTo("gitlab-lrz");
        assertThat(seeded.getType()).isEqualTo(LoginProvider.ProviderType.GITLAB);
        assertThat(seeded.getBaseUrl()).isEqualTo("https://gitlab.lrz.de");
        assertThat(seeded.getDisplayName()).isEqualTo("GitLab LRZ");
        assertThat(seeded.getScopes()).isEqualTo("read_user"); // OAuth2 (no openid), defaulted by type
        assertThat(seeded.isSeededFromEnv()).isTrue();
        assertThat(seeded.isEnabled()).isTrue();
    }

    @Test
    void seedsMultipleProvidersInOneBoot() {
        when(repository.existsByRegistrationId(any())).thenReturn(false);

        service(
            Map.of(
                "github",
                githubSeed("gh-id", "gh-secret"),
                "gitlab-lrz",
                gitlabSeed("gl-id", "gl-secret", "https://gitlab.lrz.de", "GitLab LRZ")
            )
        ).seedFromEnvOnStartup();

        ArgumentCaptor<LoginProvider> captor = ArgumentCaptor.forClass(LoginProvider.class);
        verify(repository, Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(LoginProvider::getRegistrationId)
            .containsExactlyInAnyOrder("github", "gitlab-lrz");
    }

    @Test
    void skipsSeedWhenAnInstanceWithSameTypeAndBaseUrlAlreadyExists() {
        when(repository.existsByRegistrationId(any())).thenReturn(false);
        // A row for this (type, base_url) already exists (e.g. an admin added it, or a prior id) — the
        // uniqueness guard must skip rather than crash startup on a constraint violation.
        when(repository.existsByTypeAndBaseUrl(LoginProvider.ProviderType.GITLAB, "https://gitlab.lrz.de")).thenReturn(
            true
        );

        service(
            Map.of("gitlab-lrz", gitlabSeed("client-id", "secret", "https://gitlab.lrz.de", "GitLab LRZ"))
        ).seedFromEnvOnStartup();

        verify(repository, never()).save(any());
    }

    @Test
    void skipsSeedOfInvalidRegistrationId() {
        when(repository.existsByRegistrationId(any())).thenReturn(false);

        service(Map.of("Bad Id!", githubSeed("client-id", "secret"))).seedFromEnvOnStartup();

        verify(repository, never()).save(any());
    }

    @Test
    void doesNotReseedWhenAlreadyPresent() {
        when(repository.existsByRegistrationId(any())).thenReturn(true);

        service(Map.of("github", githubSeed("client-id", "secret"))).seedFromEnvOnStartup();

        verify(repository, never()).save(any());
    }

    @Test
    void skipsUnconfiguredProviders() {
        when(repository.existsByRegistrationId(any())).thenReturn(false);

        service(
            Map.of("github", githubSeed("", ""), "gitlab", gitlabSeed("", "", "https://gitlab.com", "GitLab"))
        ).seedFromEnvOnStartup();

        verify(repository, never()).save(any());
    }

    @Test
    void createSelfHostedGitlabDefaultsScopesAndEvictsCache() {
        when(repository.existsByRegistrationId("gitlab-acme")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginProvider created = adminService().create(gitlabDraft("gitlab-acme", "https://gitlab.acme.test/", null));

        assertThat(created.getType()).isEqualTo(LoginProvider.ProviderType.GITLAB);
        assertThat(created.getBaseUrl()).isEqualTo("https://gitlab.acme.test"); // trailing slash stripped
        assertThat(created.getScopes()).isEqualTo("read_user"); // defaulted by type (OAuth2, no openid)
        assertThat(created.isSeededFromEnv()).isFalse();
        verify(registrationCache).evict("gitlab-acme");
    }

    @Test
    void createRejectsDuplicateRegistrationId() {
        when(repository.existsByRegistrationId("gitlab-acme")).thenReturn(true);

        assertThatThrownBy(() ->
            adminService().create(gitlabDraft("gitlab-acme", "https://gitlab.acme.test", null))
        ).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsGitlabOpenidScope() {
        // openid flips Spring to the OIDC path and 500s the callback (no jwkSetUri) — must be rejected.
        assertThatThrownBy(() ->
            adminService().create(gitlabDraft("gitlab-x", "https://gitlab.acme.test", "openid profile read_user"))
        ).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsGitlabOpenidScope() {
        LoginProvider existing = gitlabProvider("gitlab", "sealed");
        when(repository.findByRegistrationId("gitlab")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            adminService().update(
                "gitlab",
                new LoginProviderService.Patch(null, null, null, null, "openid read_user", null)
            )
        ).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsMalformedRegistrationId() {
        assertThatThrownBy(() ->
            adminService().create(gitlabDraft("Bad Id!", "https://gitlab.acme.test", null))
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void disableRefusesWhenItIsTheLastEnabledProvider() {
        LoginProvider only = new LoginProvider();
        only.setRegistrationId("github");
        only.setType(LoginProvider.ProviderType.GITHUB);
        only.setEnabled(true);
        when(repository.findByRegistrationId("github")).thenReturn(Optional.of(only));
        when(repository.findByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(only));

        assertThatThrownBy(() ->
            adminService().update("github", new LoginProviderService.Patch(null, null, null, null, null, false))
        ).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteRefusesWhenItIsTheLastEnabledProvider() {
        LoginProvider only = gitlabProvider("gitlab", "sealed");
        when(repository.findByRegistrationId("gitlab")).thenReturn(Optional.of(only));
        when(repository.findByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(only));

        assertThatThrownBy(() -> adminService().delete("gitlab")).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).delete(any());
        verify(registrationCache, never()).evict(any());
    }

    @Test
    void createRejectsNonHttpsGitlabBaseUrl() {
        // SSRF / HTTPS guard: ServerUrlValidator rejects http:// (and loopback/internal) base URLs.
        assertThatThrownBy(() ->
            adminService().create(gitlabDraft("gitlab-x", "http://gitlab.acme.test", null))
        ).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateLeavesSealedSecretUnchangedWhenPatchSecretIsNullOrBlank() {
        LoginProvider existing = gitlabProvider("gitlab", "sealed-secret");
        when(repository.findByRegistrationId("gitlab")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService().update("gitlab", new LoginProviderService.Patch("Renamed", null, null, null, null, null));
        assertThat(existing.getClientSecret()).isEqualTo("sealed-secret"); // null secret → unchanged

        adminService().update("gitlab", new LoginProviderService.Patch(null, null, null, "   ", null, null));
        assertThat(existing.getClientSecret()).isEqualTo("sealed-secret"); // blank secret → unchanged
    }

    @Test
    void updateReplacesSecretWhenPatchSecretIsPresent() {
        LoginProvider existing = gitlabProvider("gitlab", "old-secret");
        when(repository.findByRegistrationId("gitlab")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService().update("gitlab", new LoginProviderService.Patch(null, null, null, "new-secret", null, null));
        assertThat(existing.getClientSecret()).isEqualTo("new-secret");
    }

    private static LoginProvider gitlabProvider(String registrationId, String clientSecret) {
        LoginProvider provider = new LoginProvider();
        provider.setRegistrationId(registrationId);
        provider.setType(LoginProvider.ProviderType.GITLAB);
        provider.setDisplayName("GitLab");
        provider.setBaseUrl("https://gitlab.example.com");
        provider.setClientId("client-id");
        provider.setClientSecret(clientSecret);
        provider.setScopes("read_user");
        provider.setEnabled(true);
        return provider;
    }
}
