package de.tum.in.www1.hephaestus.account;

import de.tum.in.www1.hephaestus.config.KeycloakProperties;
import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClient;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClientException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for user account management (settings, GDPR deletion).
 * Separated from git provider concerns to maintain clean architecture.
 */
@Service
@WorkspaceAgnostic("User-scoped account operations - not workspace-specific")
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserPreferencesRepository userPreferencesRepository;
    private final PosthogClient posthogClient;
    private final Keycloak keycloak;
    private final KeycloakProperties keycloakProperties;

    public AccountService(
        UserPreferencesRepository userPreferencesRepository,
        PosthogClient posthogClient,
        Keycloak keycloak,
        KeycloakProperties keycloakProperties
    ) {
        this.userPreferencesRepository = userPreferencesRepository;
        this.posthogClient = posthogClient;
        this.keycloak = keycloak;
        this.keycloakProperties = keycloakProperties;
    }

    /**
     * Get or create user preferences for the given user.
     * Creates default preferences if they don't exist yet.
     *
     * @param user the user to get preferences for
     * @return the user's preferences
     */
    @Transactional
    public UserPreferences getOrCreatePreferences(User user) {
        return userPreferencesRepository
            .findByUserId(user.getId())
            .orElseGet(() -> {
                log.debug("Created default preferences: userLogin={}", user.getLogin());
                UserPreferences preferences = new UserPreferences(user);
                return userPreferencesRepository.save(preferences);
            });
    }

    public UserSettingsDTO getUserSettings(User user) {
        log.debug("Fetching user settings: userLogin={}", user.getLogin());
        UserPreferences preferences = getOrCreatePreferences(user);
        return toDTO(preferences);
    }

    @Transactional
    public UserSettingsDTO updateUserSettings(User user, UserSettingsDTO userSettings, String keycloakUserId) {
        log.info("Updating user settings: userLogin={}", user.getLogin());

        UserPreferences preferences = getOrCreatePreferences(user);

        preferences.setAiReviewEnabled(
            Objects.requireNonNull(userSettings.aiReviewEnabled(), "aiReviewEnabled must not be null")
        );

        boolean previousParticipation = preferences.isParticipateInResearch();
        boolean participatesInResearch = Objects.requireNonNull(
            userSettings.participateInResearch(),
            "participateInResearch must not be null"
        );
        preferences.setParticipateInResearch(participatesInResearch);
        userPreferencesRepository.save(preferences);

        // If user is opting out of research, delete their analytics data
        if (previousParticipation && !participatesInResearch) {
            if (!StringUtils.hasText(keycloakUserId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing authentication subject");
            }
            try {
                boolean anyDeleted = deletePosthogIdentities(user, keycloakUserId);
                if (!anyDeleted) {
                    log.warn("No PostHog person matched provided identifiers: userLogin={}", user.getLogin());
                }
            } catch (PosthogClientException exception) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to revoke analytics consent",
                    exception
                );
            }
        }

        return toDTO(preferences);
    }

    /**
     * Checks whether AI review comments are enabled for a user.
     * Returns {@code true} (default) if the user has no preferences row yet.
     *
     * @param userLogin the user's git provider login (must not be blank)
     * @return true if AI review is enabled or no preferences exist
     * @throws IllegalArgumentException if userLogin is null or blank
     */
    @Transactional(readOnly = true)
    public boolean isAiReviewEnabled(String userLogin) {
        if (!StringUtils.hasText(userLogin)) {
            throw new IllegalArgumentException("userLogin must not be blank");
        }
        return userPreferencesRepository
            .findByUserLogin(userLogin)
            .map(UserPreferences::isAiReviewEnabled)
            .orElse(true);
    }

    /**
     * Delete all tracking data for a user (GDPR compliance).
     * Called before account deletion.
     *
     * @param user the git provider user, or null if unresolved
     * @param keycloakUserId the Keycloak subject identifier
     */
    public void deleteUserTrackingData(User user, String keycloakUserId) {
        boolean anyDeleted = deletePosthogIdentities(user, keycloakUserId);
        if (!anyDeleted) {
            String login = user != null ? user.getLogin() : "unknown";
            log.warn("No PostHog person matched provided identifiers during account deletion: userLogin={}", login);
        }
    }

    /**
     * Returns all identity providers configured in the Keycloak realm along with
     * the current user's connection status for each.
     *
     * @param keycloakUserId the Keycloak subject identifier
     * @return list of identity providers with connection status
     */
    public List<LinkedAccountDTO> getLinkedAccounts(String keycloakUserId) {
        try {
            List<IdentityProviderRepresentation> realmIdps = keycloak
                .realm(keycloakProperties.realm())
                .identityProviders()
                .findAll();

            List<FederatedIdentityRepresentation> linked = keycloak
                .realm(keycloakProperties.realm())
                .users()
                .get(keycloakUserId)
                .getFederatedIdentity();

            Set<String> linkedAliases = linked
                .stream()
                .map(FederatedIdentityRepresentation::getIdentityProvider)
                .collect(Collectors.toSet());

            return realmIdps
                .stream()
                .filter(idp -> !Boolean.TRUE.equals(idp.isLinkOnly()) && Boolean.TRUE.equals(idp.isEnabled()))
                .map(idp -> {
                    String alias = idp.getAlias();
                    boolean isConnected = linkedAliases.contains(alias);
                    String username = linked
                        .stream()
                        .filter(fi -> fi.getIdentityProvider().equals(alias))
                        .map(FederatedIdentityRepresentation::getUserName)
                        .findFirst()
                        .orElse(null);
                    return new LinkedAccountDTO(
                        alias,
                        idp.getDisplayName() != null ? idp.getDisplayName() : alias,
                        isConnected,
                        username
                    );
                })
                .toList();
        } catch (Exception e) {
            log.error("Failed to fetch linked accounts for user {}", keycloakUserId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to communicate with identity provider");
        }
    }

    /**
     * Unlinks a federated identity provider from the user's Keycloak account.
     * Prevents unlinking the last remaining provider.
     *
     * @param keycloakUserId the Keycloak subject identifier
     * @param providerAlias  the identity provider alias to unlink
     */
    public void unlinkAccount(String keycloakUserId, String providerAlias) {
        try {
            List<FederatedIdentityRepresentation> linked = keycloak
                .realm(keycloakProperties.realm())
                .users()
                .get(keycloakUserId)
                .getFederatedIdentity();

            if (linked.size() <= 1) {
                log.warn("User {} attempted to unlink last identity provider {}", keycloakUserId, providerAlias);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot unlink last identity provider");
            }

            boolean hasProvider = linked.stream().anyMatch(fi -> fi.getIdentityProvider().equals(providerAlias));
            if (!hasProvider) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity provider not linked");
            }

            keycloak
                .realm(keycloakProperties.realm())
                .users()
                .get(keycloakUserId)
                .removeFederatedIdentity(providerAlias);

            log.info("User {} unlinked identity provider {}", keycloakUserId, providerAlias);
        } catch (ResponseStatusException e) {
            throw e; // Re-throw our own exceptions
        } catch (Exception e) {
            log.error("Failed to unlink provider {} for user {}", providerAlias, keycloakUserId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to communicate with identity provider");
        }
    }

    private static UserSettingsDTO toDTO(UserPreferences preferences) {
        return new UserSettingsDTO(preferences.isParticipateInResearch(), preferences.isAiReviewEnabled());
    }

    private boolean deletePosthogIdentities(User user, String primaryDistinctId) {
        Set<String> distinctIds = new LinkedHashSet<>();
        if (StringUtils.hasText(primaryDistinctId)) {
            distinctIds.add(primaryDistinctId);
        }
        if (user != null) {
            distinctIds.add(String.valueOf(user.getId()));
        }

        boolean anyDeleted = false;
        for (String distinctId : distinctIds) {
            if (!StringUtils.hasText(distinctId)) {
                continue;
            }
            anyDeleted = posthogClient.deletePersonData(distinctId) || anyDeleted;
        }
        return anyDeleted;
    }
}
