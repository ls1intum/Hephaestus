package de.tum.cit.aet.hephaestus.core.auth.provider;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.core.auth.spi.IdentityProviderCatalog;
import java.time.Duration;
import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Builds Spring Security login {@link ClientRegistration}s from the instance-scoped
 * {@code login_provider} store (ADR 0017). One shared registration per configured provider — GitHub,
 * GitLab.com, or a self-hosted GitLab — reused across all workspaces; there are no per-workspace login
 * apps. Replaces Boot's auto-configured {@code InMemoryClientRegistrationRepository} (which backs off
 * once this bean is present) and serves the sign-in picker via {@link IdentityProviderCatalog}.
 *
 * <p>A small {@code expireAfterWrite} cache absorbs read load; an admin edit/disable calls
 * {@link #evict(String)} for immediate effect.
 */
public class LoginProviderClientRegistrationRepository
    implements ClientRegistrationRepository, Iterable<ClientRegistration>, IdentityProviderCatalog
{

    private static final String CALLBACK_TEMPLATE = "{baseUrl}/login/oauth2/code/{registrationId}";

    private final LoginProviderRepository loginProviderRepository;
    private final Cache<String, ClientRegistration> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(60))
        .maximumSize(256)
        .build();

    public LoginProviderClientRegistrationRepository(LoginProviderRepository loginProviderRepository) {
        this.loginProviderRepository = loginProviderRepository;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        ClientRegistration cached = cache.getIfPresent(registrationId);
        if (cached != null) {
            return cached;
        }
        return loginProviderRepository
            .findByRegistrationId(registrationId)
            .filter(LoginProvider::isEnabled)
            .map(this::toRegistration)
            .map(reg -> {
                cache.put(registrationId, reg);
                return reg;
            })
            .orElse(null);
    }

    /** Drop a cached registration after an admin edit/disable so the change takes effect at once. */
    public void evict(String registrationId) {
        cache.invalidate(registrationId);
    }

    @Override
    public java.util.Iterator<ClientRegistration> iterator() {
        return listRegistrations().iterator();
    }

    @Override
    public List<ClientRegistration> listRegistrations() {
        return loginProviderRepository
            .findByEnabledTrueOrderByDisplayNameAsc()
            .stream()
            .map(this::toRegistration)
            .toList();
    }

    private ClientRegistration toRegistration(LoginProvider provider) {
        String base = provider.getBaseUrl().replaceAll("/+$", "");
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(provider.getRegistrationId())
            .clientId(provider.getClientId())
            .clientSecret(provider.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(CALLBACK_TEMPLATE)
            .scope(provider.getScopes().trim().split("\\s+"))
            .userNameAttributeName("id")
            .clientName(provider.getDisplayName());

        if (provider.getType() == LoginProvider.ProviderType.GITHUB) {
            // github.com only (GHE is out of scope). The user API lives on the api. host, not the OAuth host.
            builder
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user");
        } else {
            // GitLab (gitlab.com or self-hosted) — all endpoints hang off the instance base URL.
            builder
                .authorizationUri(base + "/oauth/authorize")
                .tokenUri(base + "/oauth/token")
                .userInfoUri(base + "/api/v4/user");
        }
        return builder.build();
    }
}
