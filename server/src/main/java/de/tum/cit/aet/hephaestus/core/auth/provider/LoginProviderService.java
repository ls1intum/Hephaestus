package de.tum.cit.aet.hephaestus.core.auth.provider;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the instance-scoped {@link LoginProvider} table: seeds the env-configured defaults on first
 * boot and is the single read path for building login {@code ClientRegistration}s (Slice 1b) and the
 * admin discovery/CRUD (Slice 2).
 *
 * <p>Seeding is idempotent and promote-once: a provider is created from {@code hephaestus.auth.*} only
 * when it does not already exist, so env becomes the <em>seed</em>, never the live source — an admin
 * can edit or disable a seeded provider afterwards and the env value won't clobber it on the next boot.
 * Registration ids {@code github} / {@code gitlab-lrz} are preserved so existing {@code IdentityLink}s
 * and the admin allowlist keep resolving.
 */
@Service
@WorkspaceAgnostic("Login providers are instance-global, not workspace-scoped")
public class LoginProviderService {

    static final String GITHUB_REGISTRATION_ID = "github";
    static final String GITLAB_LRZ_REGISTRATION_ID = "gitlab-lrz";
    private static final String GITHUB_SCOPES = "read:user user:email";
    private static final String GITLAB_SCOPES = "openid profile email read_user";

    private static final Logger log = LoggerFactory.getLogger(LoginProviderService.class);

    private final LoginProviderRepository repository;
    private final AuthProperties authProperties;

    public LoginProviderService(LoginProviderRepository repository, AuthProperties authProperties) {
        this.repository = repository;
        this.authProperties = authProperties;
    }

    /** Enabled providers for the login page / discovery, stable order. */
    @Transactional(readOnly = true)
    public List<LoginProvider> listEnabled() {
        return repository.findByEnabledTrueOrderByDisplayNameAsc();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedFromEnvOnStartup() {
        AuthProperties.GithubLogin github = authProperties.github();
        if (github.configured() && !repository.existsByRegistrationId(GITHUB_REGISTRATION_ID)) {
            save(
                GITHUB_REGISTRATION_ID,
                LoginProvider.ProviderType.GITHUB,
                "GitHub",
                "https://github.com",
                github.clientId(),
                github.clientSecret(),
                GITHUB_SCOPES
            );
            log.info("auth.login-provider: seeded default '{}' from env", GITHUB_REGISTRATION_ID);
        }
        AuthProperties.GitlabLrzLogin gitlab = authProperties.gitlabLrz();
        if (gitlab.configured() && !repository.existsByRegistrationId(GITLAB_LRZ_REGISTRATION_ID)) {
            String baseUrl = gitlab.baseUrl().toString().replaceAll("/+$", "");
            save(
                GITLAB_LRZ_REGISTRATION_ID,
                LoginProvider.ProviderType.GITLAB,
                "gitlab.lrz.de",
                baseUrl,
                gitlab.clientId(),
                gitlab.clientSecret(),
                GITLAB_SCOPES
            );
            log.info("auth.login-provider: seeded default '{}' from env", GITLAB_LRZ_REGISTRATION_ID);
        }
    }

    private void save(
        String registrationId,
        LoginProvider.ProviderType type,
        String displayName,
        String baseUrl,
        String clientId,
        String clientSecret,
        String scopes
    ) {
        LoginProvider provider = new LoginProvider();
        provider.setRegistrationId(registrationId);
        provider.setType(type);
        provider.setDisplayName(displayName);
        provider.setBaseUrl(baseUrl);
        provider.setClientId(clientId);
        provider.setClientSecret(clientSecret);
        provider.setScopes(scopes);
        provider.setEnabled(true);
        provider.setSeededFromEnv(true);
        repository.save(provider);
    }
}
