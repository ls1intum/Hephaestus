package de.tum.cit.aet.hephaestus.account;

import de.tum.cit.aet.hephaestus.analytics.posthog.PosthogClient;
import de.tum.cit.aet.hephaestus.analytics.posthog.PosthogClientException;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * User preferences (notification / research / AI-review opt-ins) + GDPR analytics deletion.
 *
 * <p>The federated-identity operations (list / link / unlink / claim) live in the
 * {@code core.auth} module (Account + IdentityLink), not here. This
 * service keeps only the preference + PostHog-consent surface, which never depended on the
 * identity provider. Named {@code AccountPreferencesService} (not {@code AccountService}) to
 * avoid a bean-name clash with {@code core.auth.AccountService}.
 */
@Service
@WorkspaceAgnostic("User-scoped preferences + analytics consent — not workspace-specific")
public class AccountPreferencesService {

    private static final Logger log = LoggerFactory.getLogger(AccountPreferencesService.class);

    private final UserPreferencesRepository userPreferencesRepository;
    private final ObjectProvider<PosthogClient> posthogClientProvider;

    public AccountPreferencesService(
        UserPreferencesRepository userPreferencesRepository,
        ObjectProvider<PosthogClient> posthogClientProvider
    ) {
        this.userPreferencesRepository = userPreferencesRepository;
        this.posthogClientProvider = posthogClientProvider;
    }

    @Transactional
    public UserPreferences getOrCreatePreferences(User user) {
        return userPreferencesRepository
            .findByUserId(user.getId())
            .orElseGet(() -> {
                log.debug("Created default preferences: userLogin={}", user.getLogin());
                return userPreferencesRepository.save(new UserPreferences(user));
            });
    }

    public UserSettingsDTO getUserSettings(User user) {
        log.debug("Fetching user settings: userLogin={}", user.getLogin());
        return toDTO(getOrCreatePreferences(user));
    }

    /**
     * Update preferences. {@code subjectId} is the authenticated principal's subject (the
     * Hephaestus account id) used as the PostHog distinct id when revoking
     * analytics consent.
     */
    @Transactional
    public UserSettingsDTO updateUserSettings(User user, UserSettingsDTO userSettings, String subjectId) {
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

        if (previousParticipation && !participatesInResearch) {
            if (!StringUtils.hasText(subjectId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing authentication subject");
            }
            try {
                if (!deletePosthogIdentities(user, subjectId)) {
                    log.warn("No PostHog person matched provided identifiers: userLogin={}", user.getLogin());
                }
            } catch (PosthogClientException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to revoke analytics consent", exception);
            }
        }
        return toDTO(preferences);
    }

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

    /** Delete analytics data for a user (GDPR). Called before account deletion. */
    public void deleteUserTrackingData(User user, String subjectId) {
        if (!deletePosthogIdentities(user, subjectId)) {
            String login = user != null ? user.getLogin() : "unknown";
            log.warn("No PostHog person matched provided identifiers during account deletion: userLogin={}", login);
        }
    }

    private static UserSettingsDTO toDTO(UserPreferences preferences) {
        return new UserSettingsDTO(preferences.isParticipateInResearch(), preferences.isAiReviewEnabled());
    }

    private boolean deletePosthogIdentities(User user, String primaryDistinctId) {
        PosthogClient client = posthogClientProvider.getIfAvailable();
        if (client == null) {
            log.debug("Skipped PostHog deletion: reason=clientDisabled");
            return false;
        }
        Set<String> distinctIds = new LinkedHashSet<>();
        if (StringUtils.hasText(primaryDistinctId)) {
            distinctIds.add(primaryDistinctId);
        }
        if (user != null) {
            distinctIds.add(String.valueOf(user.getId()));
        }
        boolean anyDeleted = false;
        for (String distinctId : distinctIds) {
            if (StringUtils.hasText(distinctId)) {
                anyDeleted = client.deletePersonData(distinctId) || anyDeleted;
            }
        }
        return anyDeleted;
    }
}
