package de.tum.cit.aet.hephaestus.core.auth.provider;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.ServerUrlValidator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * boot, is the single read path for building login {@code ClientRegistration}s, and backs
 * the instance-admin CRUD.
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
    // GitLab is plain OAuth2, NOT OIDC — scope must NOT contain "openid" (see sanitizeScopesOrThrow).
    // "read_user" alone returns id + username + email from /api/v4/user. See ADR 0017.
    static final String GITLAB_SCOPES = "read_user";
    // "Sign in with Slack" is OIDC: the id_token carries the sub + verified team_id claim, and
    // "openid" is REQUIRED (it makes Spring take the OIDC path).
    static final String SLACK_SCOPES = "openid profile email";
    // Outline is plain OAuth2, NOT OIDC — scope must NOT contain "openid" (see sanitizeScopesOrThrow).
    // "read" is Outline's read-everything scope, sufficient for the POST /api/auth.info identity probe.
    static final String OUTLINE_SCOPES = "read";
    private static final String GITHUB_COM = "https://github.com";
    // The single Slack instance. Canonical origin the seeded identity_provider + SlackMentorIdentityResolver key on.
    private static final String SLACK_COM = "https://slack.com";

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

    /**
     * The enabled provider behind a registration id, or empty when unknown/disabled — the login-begin
     * lookup ({@code AuthBeginController}): an unknown or disabled provider must not start an OAuth
     * flow, and the row's type drives the link-only gate.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<LoginProvider> findEnabled(String registrationId) {
        return repository.findByRegistrationId(registrationId).filter(LoginProvider::isEnabled);
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

    /**
     * A seed slot is <em>silence</em> (both credential halves blank — "this deployment has no wiki") or a
     * <em>promise</em> (credentials present). A promise that cannot be kept — a half-filled credential, an
     * unusable base URL — is an operator mistake that used to vanish into a single WARN, leaving the admin
     * with no provider and no reason why. It is now reported at ERROR with the exact knob to fix.
     *
     * <p>Deliberately non-fatal: this listener runs on {@link ApplicationReadyEvent}, and an exception here
     * aborts startup. One misconfigured optional integration must not take the whole app down — so we shout,
     * we don't die.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedFromEnvOnStartup() {
        // Sorted by registration id for deterministic seeding when two entries collide on (type, baseUrl).
        Set<String> seededInstances = new HashSet<>();
        new TreeMap<>(authProperties.loginProviders()).forEach((registrationId, seed) -> {
            if (seed == null) {
                return;
            }
            String id = registrationId == null ? "" : registrationId.trim();
            if (seed.partiallyConfigured()) {
                // Half a credential: seeding it would offer an ENABLED provider whose every OAuth exchange
                // 401s at the IdP with an opaque error. Skip it — and say exactly which env var is missing.
                log.error(
                    "auth.login-provider: NOT seeding '{}' — it is half-configured: {} is blank. " +
                        "Set {}, or clear the other half to disable this provider entirely.",
                    id,
                    seed.missingCredentialField(),
                    envKnobFor(id, seed.missingCredentialField())
                );
                return;
            }
            if (!seed.configured()) {
                return; // blank credentials → provider omitted (credential-less pods still boot)
            }
            if (!id.matches("^[a-z][a-z0-9-]{1,62}$")) {
                log.error(
                    "auth.login-provider: NOT seeding '{}' — invalid registration id. It must be 2-63 chars: " +
                        "a lowercase letter followed by lowercase letters, digits, or hyphens (e.g. 'gitlab-lrz').",
                    registrationId
                );
                return;
            }
            if (repository.existsByRegistrationId(id)) {
                return; // seed-once (see class javadoc)
            }
            String baseUrl;
            try {
                baseUrl = resolveBaseUrl(seed.type(), seed.baseUrl());
            } catch (ResponseStatusException e) {
                // The common self-hosted-Outline / self-hosted-GitLab trip-wire: credentials set, base URL
                // blank, http://, or a private/internal address (rejected by the SSRF guard).
                log.error(
                    "auth.login-provider: NOT seeding '{}' ({}) — its base URL is unusable: {}. " +
                        "Set {} to the instance's public HTTPS origin (e.g. https://wiki.example.com).",
                    id,
                    seed.type(),
                    e.getReason(),
                    envKnobFor(id, "base-url")
                );
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

    /**
     * The env var that feeds a shipped seed slot's field, so a misconfiguration diagnostic names the knob the
     * operator actually sets ({@code OUTLINE_OAUTH_CLIENT_SECRET}), not an abstract property path. Slots the
     * operator declared themselves (e.g. {@code gitlab-lrz}) have no fixed env var — fall back to the canonical
     * property path, which is always correct. Keep in sync with {@code application.yml}'s
     * {@code hephaestus.auth.login-providers} block.
     */
    private static final Map<String, String> SHIPPED_SLOT_ENV_PREFIX = Map.of(
        "github",
        "GITHUB_OAUTH",
        "gitlab",
        "GITLAB_OAUTH",
        "slack",
        "SLACK_OAUTH",
        "outline",
        "OUTLINE_OAUTH"
    );

    private static String envKnobFor(String registrationId, String field) {
        String prefix = SHIPPED_SLOT_ENV_PREFIX.get(registrationId);
        if (prefix == null) {
            return "hephaestus.auth.login-providers." + registrationId + "." + field;
        }
        return prefix + "_" + field.toUpperCase(Locale.ROOT).replace('-', '_');
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
     * github.com endpoints). For GitLab and Outline (self-hosted instances), validate the supplied
     * base URL (HTTPS + SSRF guard).
     */
    private static String resolveBaseUrl(LoginProvider.ProviderType type, @Nullable String baseUrl) {
        if (type == LoginProvider.ProviderType.GITHUB) {
            return GITHUB_COM;
        }
        if (type == LoginProvider.ProviderType.SLACK) {
            // Slack login is slack.com only (the registration builder hardcodes the slack.com OIDC endpoints).
            return SLACK_COM;
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
        return switch (type) {
            case GITHUB -> GITHUB_SCOPES;
            case GITLAB -> GITLAB_SCOPES;
            case SLACK -> SLACK_SCOPES;
            case OUTLINE -> OUTLINE_SCOPES;
        };
    }

    /**
     * GitLab and Outline use the plain OAuth2 flow (userInfo = /api/v4/user resp. /api/auth.info); an
     * {@code openid} scope flips Spring to the OIDC path and 500s the callback (no jwkSetUri is
     * configured — Outline is not an OIDC provider at all). Reject it with an actionable 422 so an
     * admin editing the provider can't silently brick sign-in. See GITLAB_SCOPES / OUTLINE_SCOPES +
     * ADR 0017.
     */
    private static String sanitizeScopesOrThrow(LoginProvider.ProviderType type, String scopes) {
        String trimmed = scopes.trim();
        if (type == LoginProvider.ProviderType.GITLAB || type == LoginProvider.ProviderType.OUTLINE) {
            String replacement = type == LoginProvider.ProviderType.GITLAB ? "'read_user'" : "'read'";
            for (String scope : trimmed.split("\\s+")) {
                if (scope.equalsIgnoreCase("openid")) {
                    throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        type +
                            " login uses the plain OAuth2 flow — the scope must not contain 'openid' (use " +
                            replacement +
                            ")"
                    );
                }
            }
        }
        return trimmed;
    }

    /**
     * The lockout guard, counting only providers that can actually sign someone in. A link-only provider
     * ({@link LoginProvider.ProviderType#isLinkOnly()} — Slack, Outline) is BY CONSTRUCTION never a sign-in
     * method: it is filtered off the login picker and rejected at flow begin. Counting it here would (a)
     * refuse to remove the sole Outline provider on an instance that has one, and (b) worse, let an admin
     * delete the last REAL sign-in provider as long as a link-only one remained — the guard would see a
     * non-empty "enabled" list and wave it through. Both fall out once the count is sign-in-capable only.
     */
    private void requireNotLastEnabled(String registrationId, String verb) {
        List<LoginProvider> enabled = repository
            .findByEnabledTrueOrderByDisplayNameAsc()
            .stream()
            .filter(p -> p.getType() != null && !p.getType().isLinkOnly())
            .toList();
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
