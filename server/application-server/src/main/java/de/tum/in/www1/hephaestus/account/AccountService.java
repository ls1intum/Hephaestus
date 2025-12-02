package de.tum.in.www1.hephaestus.account;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
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
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository userRepository;
    private final PosthogClient posthogClient;

    public AccountService(UserRepository userRepository, PosthogClient posthogClient) {
        this.userRepository = userRepository;
        this.posthogClient = posthogClient;
    }

    public UserSettingsDTO getUserSettings(User user) {
        logger.debug("Getting user settings for user: {}", user.getLogin());
        return new UserSettingsDTO(user.isNotificationsEnabled(), user.isParticipateInResearch());
    }

    @Transactional
    public UserSettingsDTO updateUserSettings(User user, UserSettingsDTO userSettings, String keycloakUserId) {
        logger.info("Updating user settings for user: {}", user.getLogin());

        user.setNotificationsEnabled(
            Objects.requireNonNull(userSettings.receiveNotifications(), "receiveNotifications must not be null")
        );

        boolean previousParticipation = user.isParticipateInResearch();
        boolean participatesInResearch = Objects.requireNonNull(
            userSettings.participateInResearch(),
            "participateInResearch must not be null"
        );
        user.setParticipateInResearch(participatesInResearch);
        userRepository.save(user);

        // If user is opting out of research, delete their analytics data
        if (previousParticipation && !participatesInResearch) {
            if (!StringUtils.hasText(keycloakUserId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing authentication subject");
            }
            try {
                boolean anyDeleted = deletePosthogIdentities(user, keycloakUserId);
                if (!anyDeleted) {
                    logger.warn("No PostHog person matched the provided identifiers for user {}", user.getLogin());
                }
            } catch (PosthogClientException exception) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to revoke analytics consent",
                    exception
                );
            }
        }

        return new UserSettingsDTO(user.isNotificationsEnabled(), user.isParticipateInResearch());
    }

    /**
     * Delete all tracking data for a user (GDPR compliance).
     * Called before account deletion.
     */
    public void deleteUserTrackingData(Optional<User> user, String keycloakUserId) {
        try {
            boolean anyDeleted = deletePosthogIdentities(user.orElse(null), keycloakUserId);
            if (!anyDeleted) {
                logger.warn(
                    "No PostHog person matched the provided identifiers for user {} during account deletion",
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
