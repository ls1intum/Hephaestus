package de.tum.in.www1.hephaestus.account;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClient;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClientException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    public AccountService(UserPreferencesRepository userPreferencesRepository, PosthogClient posthogClient) {
        this.userPreferencesRepository = userPreferencesRepository;
        this.posthogClient = posthogClient;
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
        return new UserSettingsDTO(preferences.isNotificationsEnabled(), preferences.isParticipateInResearch());
    }

    @Transactional
    public UserSettingsDTO updateUserSettings(User user, UserSettingsDTO userSettings, String keycloakUserId) {
        log.info("Updating user settings: userLogin={}", user.getLogin());

        UserPreferences preferences = getOrCreatePreferences(user);

        preferences.setNotificationsEnabled(
            Objects.requireNonNull(userSettings.receiveNotifications(), "receiveNotifications must not be null")
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

        return new UserSettingsDTO(preferences.isNotificationsEnabled(), preferences.isParticipateInResearch());
    }

    /**
     * Delete all tracking data for a user (GDPR compliance).
     * Called before account deletion.
     */
    public void deleteUserTrackingData(Optional<User> user, String keycloakUserId) {
        try {
            boolean anyDeleted = deletePosthogIdentities(user.orElse(null), keycloakUserId);
            if (!anyDeleted) {
                log.warn(
                    "No PostHog person matched provided identifiers during account deletion: userLogin={}",
                    user.map(User::getLogin).orElse("unknown")
                );
            }
        } catch (PosthogClientException exception) {
            throw exception;
        }
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
