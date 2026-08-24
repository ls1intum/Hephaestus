package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.Optional;

public interface AccountPreferencesQuery {
    Optional<PreferencesView> preferencesForLogin(String login);

    Optional<PreferencesView> preferencesForUserId(long userId);

    /**
     * A row exists only once someone opens their account settings, so its absence is not a choice — it
     * resolves to the same default a freshly created row carries.
     */
    default boolean practiceFeedbackDeliveryEnabled(long userId) {
        return preferencesForUserId(userId)
            .map(PreferencesView::practiceFeedbackDeliveryEnabled)
            .orElse(PreferencesView.PRACTICE_FEEDBACK_DELIVERY_ENABLED_BY_DEFAULT);
    }

    record PreferencesView(boolean participateInResearch, boolean practiceFeedbackDeliveryEnabled) {
        public static final boolean PRACTICE_FEEDBACK_DELIVERY_ENABLED_BY_DEFAULT = true;
    }
}
