package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.Optional;

/**
 * Cross-module read query: the principal's account preferences (notification / research /
 * AI-review opt-ins).
 *
 * <p>Preferences live in the {@code account} module ({@code user_preferences}), keyed by the SCM
 * {@code user.login}. The {@code core.auth} module owns the {@code Account → login} mapping and
 * supplies the login; the {@code account} module resolves {@code login → UserPreferences}
 * internally. Defined here and implemented in {@code account} (dependency inversion, same shape
 * as {@link AccountRoleQuery}) so neither module imports the other's domain types.
 */
public interface AccountPreferencesQuery {
    /**
     * @param login the principal's git-provider login (case-insensitive)
     * @return the principal's preferences, or empty if none have been persisted yet
     */
    Optional<PreferencesView> preferencesForLogin(String login);

    /** Account preference flags, flattened for export. */
    record PreferencesView(boolean participateInResearch, boolean aiReviewEnabled) {}
}
