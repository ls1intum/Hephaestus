package de.tum.cit.aet.hephaestus.integration.identity.connect;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.core.auth.spi.IdentityProviderCatalog;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.CredentialBundleConverter;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.OAuthClientSecret;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Composite {@link ClientRegistrationRepository}: env-configured default providers
 * ({@code github}, {@code gitlab-lrz}) overlaid with workspace-scoped OIDC login providers
 * materialized from {@link Connection} rows of family {@code IDENTITY}.
 *
 * <p>Workspace registrations use the id form {@code gl-ws-{connectionId}} /
 * {@code gh-ws-{connectionId}} — the connection id makes them globally unique and lets the
 * callback path {@code /login/oauth2/code/{registrationId}} round-trip cleanly.
 *
 * <p>Also implements {@link Iterable} so Spring Security can enumerate registrations for the
 * default login-page discovery (we don't use Spring's default page, but the contract is
 * honoured), and {@link IdentityProviderCatalog} so {@code core.auth}'s discovery controller
 * can list the sign-in options without importing this integration class. The dynamic set is
 * Caffeine-cached for 5 minutes; that TTL is the invalidation mechanism — a rotated/added/removed
 * workspace login provider takes effect within 5 minutes. (Event-driven eviction is intentionally
 * not implemented: login-provider changes are rare and the bounded staleness is acceptable.)
 */
public class LoginClientRegistrationRepository
    implements ClientRegistrationRepository, Iterable<ClientRegistration>, IdentityProviderCatalog
{

    private static final Logger log = LoggerFactory.getLogger(LoginClientRegistrationRepository.class);
    private static final String CALLBACK_TEMPLATE = "{baseUrl}/login/oauth2/code/{registrationId}";
    private static final List<IntegrationKind> OIDC_KINDS = List.of(
        IntegrationKind.OIDC_LOGIN_GITHUB,
        IntegrationKind.OIDC_LOGIN_GITLAB
    );

    private final Map<String, ClientRegistration> defaults;
    private final ConnectionRepository connectionRepository;
    private final CredentialBundleConverter credentialConverter;
    private final Cache<String, ClientRegistration> dynamicCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .maximumSize(512)
        .build();

    /**
     * @param defaults env-configured registrations keyed by id. May be empty in dev when no
     *                 OAuth credentials are set (unlike {@code InMemoryClientRegistrationRepository},
     *                 which rejects an empty list).
     */
    public LoginClientRegistrationRepository(
        List<ClientRegistration> defaults,
        ConnectionRepository connectionRepository,
        CredentialBundleConverter credentialConverter
    ) {
        Map<String, ClientRegistration> map = new LinkedHashMap<>();
        for (ClientRegistration reg : defaults) {
            map.put(reg.getRegistrationId(), reg);
        }
        this.defaults = map;
        this.connectionRepository = connectionRepository;
        this.credentialConverter = credentialConverter;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        ClientRegistration env = defaults.get(registrationId);
        if (env != null) {
            return env;
        }
        ClientRegistration cached = dynamicCache.getIfPresent(registrationId);
        if (cached != null) {
            return cached;
        }
        Optional<Long> connectionId = parseConnectionId(registrationId);
        if (connectionId.isEmpty()) {
            return null;
        }
        return connectionRepository
            .findById(connectionId.get())
            .filter(c -> c.getState() == IntegrationState.ACTIVE)
            .filter(c -> OIDC_KINDS.contains(c.getKind()))
            .map(this::toRegistration)
            .map(reg -> {
                dynamicCache.put(registrationId, reg);
                return reg;
            })
            .orElse(null);
    }

    @Override
    public java.util.Iterator<ClientRegistration> iterator() {
        return listRegistrations().iterator();
    }

    @Override
    public List<ClientRegistration> listRegistrations() {
        List<ClientRegistration> all = new ArrayList<>(defaults.values());
        connectionRepository
            .findByKindInAndStateWithWorkspace(OIDC_KINDS, IntegrationState.ACTIVE)
            .stream()
            .map(this::toRegistration)
            .forEach(all::add);
        return all;
    }

    private ClientRegistration toRegistration(Connection connection) {
        if (!(connection.getConfig() instanceof ConnectionConfig.OidcLoginConfig cfg)) {
            throw new IllegalStateException(
                "Connection " + connection.getId() + " kind=" + connection.getKind() + " has non-OIDC config"
            );
        }
        OAuthClientSecret secret = connection
            .credentials(credentialConverter)
            .filter(OAuthClientSecret.class::isInstance)
            .map(OAuthClientSecret.class::cast)
            .orElseThrow(() ->
                new IllegalStateException("OIDC login Connection " + connection.getId() + " has no client secret")
            );

        String registrationId = registrationIdFor(connection.getId(), connection.getKind());
        String issuer = cfg.issuerUrl();
        boolean github = connection.getKind() == IntegrationKind.OIDC_LOGIN_GITHUB;

        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(registrationId)
            .clientId(secret.clientId())
            .clientSecret(secret.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(CALLBACK_TEMPLATE)
            .scope(cfg.scopes().toArray(String[]::new))
            .clientName(cfg.displayName());

        if (github) {
            builder
                .authorizationUri(issuer + "/login/oauth/authorize")
                .tokenUri(issuer + "/login/oauth/access_token")
                .userInfoUri(issuer + "/api/v3/user")
                .userNameAttributeName("id");
        } else {
            builder
                .authorizationUri(issuer + "/oauth/authorize")
                .tokenUri(issuer + "/oauth/token")
                .userInfoUri(issuer + "/api/v4/user")
                .userNameAttributeName("id");
        }
        return builder.build();
    }

    private static String registrationIdFor(long connectionId, IntegrationKind kind) {
        String prefix = kind == IntegrationKind.OIDC_LOGIN_GITHUB ? "gh-ws-" : "gl-ws-";
        return prefix + connectionId;
    }

    private static Optional<Long> parseConnectionId(String registrationId) {
        String numeric;
        if (registrationId.startsWith("gh-ws-")) {
            numeric = registrationId.substring("gh-ws-".length());
        } else if (registrationId.startsWith("gl-ws-")) {
            numeric = registrationId.substring("gl-ws-".length());
        } else {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(numeric));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
