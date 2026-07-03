package de.tum.cit.aet.hephaestus.account;

import de.tum.cit.aet.hephaestus.analytics.posthog.PosthogClient;
import de.tum.cit.aet.hephaestus.analytics.posthog.PosthogClientException;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.ConsentSource;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchConsentAudit;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchParticipationCommand;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
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
public class AccountPreferencesService implements ResearchParticipationCommand {

    private static final Logger log = LoggerFactory.getLogger(AccountPreferencesService.class);

    private final UserPreferencesRepository userPreferencesRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<PosthogClient> posthogClientProvider;
    // ObjectProvider: the audit adapter is @ConditionalOnServerRole, so it is absent in the worker/webhook
    // roles where this bean still loads. A hard dependency would break context load there.
    private final ObjectProvider<ResearchConsentAudit> researchConsentAuditProvider;

    public AccountPreferencesService(
        UserPreferencesRepository userPreferencesRepository,
        UserRepository userRepository,
        ObjectProvider<PosthogClient> posthogClientProvider,
        ObjectProvider<ResearchConsentAudit> researchConsentAuditProvider
    ) {
        this.userPreferencesRepository = userPreferencesRepository;
        this.userRepository = userRepository;
        this.posthogClientProvider = posthogClientProvider;
        this.researchConsentAuditProvider = researchConsentAuditProvider;
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
     * {@link ResearchParticipationCommand} — set research participation by login, <strong>lenient</strong>.
     *
     * <p>Deliberately does NOT reuse {@link #updateUserSettings}: that path throws {@code BAD_REQUEST} on a
     * missing subject and {@code BAD_GATEWAY} on a PostHog failure, which is wrong for an out-of-band consent
     * surface (the Slack App Home has no HTTP subject and an opt-out is a user right that must take effect
     * regardless of analytics reachability). Here every degenerate case is logged, never thrown:
     * <ul>
     *   <li>blank login → no-op (logged);</li>
     *   <li>no mirrored {@code User} for the login → no-op (logged) — nothing to persist against;</li>
     *   <li>on opt-out, the analytics {@code subjectId} is optional: {@link #deletePosthogIdentities} falls
     *       back to the user id as the distinct id, and a {@code PosthogClientException} is swallowed — the
     *       opt-out has already been persisted.</li>
     * </ul>
     * An opt-out (true → false) additionally writes a {@code RESEARCH_CONSENT_REVOKED} audit event.
     */
    @Override
    @Transactional
    public void setForLogin(String login, boolean participate, ConsentSource source) {
        if (!StringUtils.hasText(login)) {
            log.warn("research-consent: setForLogin with blank login ignored (source={})", source);
            return;
        }
        Optional<User> userOpt = userRepository.findByLogin(login);
        if (userOpt.isEmpty()) {
            // Lenient: a login with no mirrored SCM user cannot carry preferences yet. Do not throw.
            log.warn("research-consent: no user for login={} (source={}); nothing to persist", login, source);
            return;
        }
        User user = userOpt.get();
        UserPreferences preferences = getOrCreatePreferences(user);
        boolean previousParticipation = preferences.isParticipateInResearch();
        preferences.setParticipateInResearch(participate);
        userPreferencesRepository.save(preferences);

        if (previousParticipation && !participate) {
            revokeResearchAnalyticsLenient(user);
            writeOptOutAuditEvent(user, source);
        }
    }

    /** Best-effort analytics revocation for an opt-out — never surfaces the failure (opt-out already persisted). */
    private void revokeResearchAnalyticsLenient(User user) {
        try {
            // subjectId optional: null → deletePosthogIdentities falls back to the user id as the distinct id.
            if (!deletePosthogIdentities(user, null)) {
                log.warn("research-consent: no PostHog person matched on opt-out: userLogin={}", user.getLogin());
            }
        } catch (PosthogClientException exception) {
            // Lenient — the opt-out row is already committed; analytics reachability must not gate a user right.
            log.warn(
                "research-consent: PostHog revocation failed on opt-out (opt-out still applied): userLogin={}",
                user.getLogin(),
                exception
            );
        }
    }

    /** Append the opt-out to the auth-event trail via the SPI port. Best-effort: absent off the server role. */
    private void writeOptOutAuditEvent(User user, ConsentSource source) {
        ResearchConsentAudit audit = researchConsentAuditProvider.getIfAvailable();
        if (audit != null) {
            audit.recordOptOut(user.getLogin(), source);
        }
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
