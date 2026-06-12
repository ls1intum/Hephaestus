package de.tum.cit.aet.hephaestus.core.auth.provider;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.ServerUrlValidator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns the instance-scoped {@link LoginProvider} table: seeds the env-configured defaults on first
 * boot, is the single read path for building login {@code ClientRegistration}s (Slice 1b), and backs
 * the instance-admin CRUD (Slice 2).
 *
 * <p>Seeding is idempotent and promote-once: a provider is created from {@code hephaestus.auth.login-
 * providers.*} only when it does not already exist, so env becomes the <em>seed</em>, never the live
 * source — an admin can edit or disable a seeded provider afterwards and the env value won't clobber it
 * on the next boot. Each entry is keyed by a stable, operator-chosen {@code registrationId} (e.g.
 * {@code github}, {@code gitlab}, {@code gitlab-lrz}) so {@code IdentityLink}s and the admin allowlist
 * keep resolving across reboots; multiple providers per instance seed in one boot (one per
 * {@code (type, baseUrl)} SCM instance).
 *
 * <p>Every mutation evicts the {@link LoginProviderClientRegistrationRepository} cache so an admin's
 * edit/enable/disable takes effect immediately rather than after the 60s TTL.
 */
@ConditionalOnServerRole
@Service
@WorkspaceAgnostic("Login providers are instance-global, not workspace-scoped")
public class LoginProviderService {

    static final String GITHUB_SCOPES = "read:user user:email";
    // GitLab login uses the OAuth2 flow (userInfo = /api/v4/user, keyed on "id"), NOT OIDC — so the
    // scope must NOT contain "openid". A request carrying "openid" makes Spring Security take the OIDC
    // path and validate the id_token JWS via a jwkSetUri the registration never sets, which 500s the
    // callback. "read_user" alone returns id + username + email from /api/v4/user. See ADR 0017.
    static final String GITLAB_SCOPES = "read_user";
    private static final String GITHUB_COM = "https://github.com";

    private static final Logger log = LoggerFactory.getLogger(LoginProviderService.class);

    private final LoginProviderRepository repository;
    private final LoginProviderClientRegistrationRepository registrationCache;
    private final AuthProperties authProperties;

    public LoginProviderService(
        LoginProviderRepository repository,
        LoginProviderClientRegistrationRepository registrationCache,
        AuthProperties authProperties
    ) {
        this.repository = repository;
        this.registrationCache = registrationCache;
        this.authProperties = authProperties;
    }

    /** Enabled providers for the login page / discovery, stable order. */
    @Transactional(readOnly = true)
    public List<LoginProvider> listEnabled() {
        return repository.findByEnabledTrueOrderByDisplayNameAsc();
    }

    /** All providers (incl. disabled) for the admin UI, stable order. */
    @Transactional(readOnly = true)
    public List<LoginProvider> listAll() {
        return repository.findAll(Sort.by("displayName").ascending());
    }

    @Transactional(readOnly = true)
    public LoginProvider require(String registrationId) {
        return repository
            .findByRegistrationId(registrationId)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "no login provider: " + registrationId)
            );
    }

    /** Create a new login provider (instance admin). The client secret is sealed at rest. */
    @Transactional
    public LoginProvider create(Draft draft) {
        String registrationId = draft.registrationId() == null ? "" : draft.registrationId().trim();
        if (!registrationId.matches("^[a-z][a-z0-9-]{1,62}$")) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "registrationId must be 2-63 chars: lowercase letter then lowercase letters, digits, or hyphens"
            );
        }
        if (repository.existsByRegistrationId(registrationId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "a login provider '" + registrationId + "' already exists"
            );
        }
        LoginProvider provider = new LoginProvider();
        provider.setRegistrationId(registrationId);
        provider.setType(draft.type());
        provider.setDisplayName(blankToNull(draft.displayName()) == null ? registrationId : draft.displayName().trim());
        provider.setBaseUrl(resolveBaseUrl(draft.type(), draft.baseUrl()));
        provider.setClientId(requireValue(draft.clientId(), "clientId"));
        provider.setClientSecret(requireValue(draft.clientSecret(), "clientSecret"));
        provider.setScopes(resolveScopes(draft.type(), draft.scopes()));
        provider.setEnabled(true);
        provider.setSeededFromEnv(false);
        LoginProvider saved = persist(provider);
        log.info("auth.login-provider: admin created '{}' ({})", registrationId, saved.getType());
        return saved;
    }

    /** Apply a partial update (only non-null fields). registrationId + type are immutable identity. */
    @Transactional
    public LoginProvider update(String registrationId, Patch patch) {
        LoginProvider provider = require(registrationId);
        if (patch.displayName() != null && !patch.displayName().isBlank()) {
            provider.setDisplayName(patch.displayName().trim());
        }
        if (patch.baseUrl() != null && !patch.baseUrl().isBlank()) {
            provider.setBaseUrl(resolveBaseUrl(provider.getType(), patch.baseUrl()));
        }
        if (patch.clientId() != null && !patch.clientId().isBlank()) {
            provider.setClientId(patch.clientId().trim());
        }
        // Write-only secret: a null/blank value leaves the sealed secret unchanged.
        if (patch.clientSecret() != null && !patch.clientSecret().isBlank()) {
            provider.setClientSecret(patch.clientSecret());
        }
        if (patch.scopes() != null && !patch.scopes().isBlank()) {
            provider.setScopes(sanitizeScopesOrThrow(provider.getType(), patch.scopes()));
        }
        if (patch.enabled() != null && !patch.enabled() && provider.isEnabled()) {
            requireNotLastEnabled(registrationId, "disable");
            provider.setEnabled(false);
        } else if (patch.enabled() != null && patch.enabled()) {
            provider.setEnabled(true);
        }
        LoginProvider saved = persist(provider);
        log.info("auth.login-provider: admin updated '{}' (enabled={})", registrationId, saved.isEnabled());
        return saved;
    }

    /** Delete a login provider. Refuses to remove the last enabled one (would lock everyone out). */
    @Transactional
    public void delete(String registrationId) {
        LoginProvider provider = require(registrationId);
        if (provider.isEnabled()) {
            requireNotLastEnabled(registrationId, "delete");
        }
        repository.delete(provider);
        registrationCache.evict(registrationId);
        log.info("auth.login-provider: admin deleted '{}'", registrationId);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedFromEnvOnStartup() {
        // Sorted by registration id for deterministic seeding when two entries collide on (type, baseUrl).
        Set<String> seededInstances = new HashSet<>();
        new TreeMap<>(authProperties.loginProviders()).forEach((registrationId, seed) -> {
            if (seed == null || !seed.configured()) {
                return; // blank client id → provider omitted (credential-less pods still boot)
            }
            String id = registrationId == null ? "" : registrationId.trim();
            if (!id.matches("^[a-z][a-z0-9-]{1,62}$")) {
                log.warn("auth.login-provider: skipping seed of invalid registration id '{}'", registrationId);
                return;
            }
            if (repository.existsByRegistrationId(id)) {
                return; // seed-once: env is the seed, never the live source — admin edits survive reboots
            }
            String baseUrl;
            try {
                baseUrl = resolveBaseUrl(seed.type(), seed.baseUrl());
            } catch (ResponseStatusException e) {
                log.warn("auth.login-provider: skipping seed '{}' — invalid base URL: {}", id, e.getReason());
                return;
            }
            // One login app per SCM instance (uq on type+base_url): skip a duplicate so a misconfiguration
            // can't crash startup on a constraint violation.
            if (
                !seededInstances.add(seed.type() + "|" + baseUrl) ||
                repository.existsByTypeAndBaseUrl(seed.type(), baseUrl)
            ) {
                log.warn(
                    "auth.login-provider: skipping seed '{}' — a {} provider for {} already exists",
                    id,
                    seed.type(),
                    baseUrl
                );
                return;
            }
            String displayName = seed.displayName().isBlank() ? id : seed.displayName().trim();
            seed(
                id,
                seed.type(),
                displayName,
                baseUrl,
                seed.clientId().trim(),
                seed.clientSecret(),
                resolveScopes(seed.type(), null)
            );
            log.info("auth.login-provider: seeded '{}' ({} {}) from env", id, seed.type(), baseUrl);
        });
    }

    private void seed(
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

    private LoginProvider persist(LoginProvider provider) {
        LoginProvider saved = repository.save(provider);
        registrationCache.evict(saved.getRegistrationId());
        return saved;
    }

    /**
     * GitHub login is github.com only (GHE login is out of scope; the registration builder hardcodes
     * github.com endpoints). For GitLab, validate the supplied base URL (HTTPS + SSRF guard).
     */
    private static String resolveBaseUrl(LoginProvider.ProviderType type, @Nullable String baseUrl) {
        if (type == LoginProvider.ProviderType.GITHUB) {
            return GITHUB_COM;
        }
        String value = baseUrl == null ? "" : baseUrl.trim();
        try {
            ServerUrlValidator.validate(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid base URL: " + e.getMessage());
        }
        return value;
    }

    private static String resolveScopes(LoginProvider.ProviderType type, @Nullable String scopes) {
        if (scopes != null && !scopes.isBlank()) {
            return sanitizeScopesOrThrow(type, scopes);
        }
        return type == LoginProvider.ProviderType.GITHUB ? GITHUB_SCOPES : GITLAB_SCOPES;
    }

    /**
     * GitLab login uses the OAuth2 flow (userInfo = /api/v4/user); an {@code openid} scope flips Spring
     * to the OIDC path and 500s the callback (no jwkSetUri is configured). Reject it with an actionable
     * 422 so an admin editing the provider can't silently brick sign-in. See GITLAB_SCOPES + ADR 0017.
     */
    private static String sanitizeScopesOrThrow(LoginProvider.ProviderType type, String scopes) {
        String trimmed = scopes.trim();
        if (type == LoginProvider.ProviderType.GITLAB) {
            for (String scope : trimmed.split("\\s+")) {
                if (scope.equalsIgnoreCase("openid")) {
                    throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "GitLab login uses the OAuth2 flow — the scope must not contain 'openid' (use 'read_user')"
                    );
                }
            }
        }
        return trimmed;
    }

    private void requireNotLastEnabled(String registrationId, String verb) {
        List<LoginProvider> enabled = repository.findByEnabledTrueOrderByDisplayNameAsc();
        boolean isLast = enabled.stream().allMatch(p -> p.getRegistrationId().equals(registrationId));
        if (!enabled.isEmpty() && isLast) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "cannot " + verb + " the last enabled login provider — users would be unable to sign in"
            );
        }
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String requireValue(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, field + " is required");
        }
        return value.trim();
    }

    /** Create command (instance admin). {@code scopes} may be null → defaulted by {@code type}. */
    public record Draft(
        String registrationId,
        LoginProvider.ProviderType type,
        @Nullable String displayName,
        @Nullable String baseUrl,
        String clientId,
        String clientSecret,
        @Nullable String scopes
    ) {}

    /** Partial-update command; every field null = leave unchanged. */
    public record Patch(
        @Nullable String displayName,
        @Nullable String baseUrl,
        @Nullable String clientId,
        @Nullable String clientSecret,
        @Nullable String scopes,
        @Nullable Boolean enabled
    ) {}
}
