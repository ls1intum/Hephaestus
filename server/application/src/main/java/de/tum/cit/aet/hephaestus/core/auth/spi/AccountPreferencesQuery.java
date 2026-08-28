package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.Optional;

public interface AccountPreferencesQuery {
    Optional<PreferencesView> preferencesForLogin(String login);

    Optional<PreferencesView> preferencesForUserId(long userId);

    default boolean practiceFeedbackDeliveryEnabled(long userId) {
        return preferencesForUserId(userId)
            .map(PreferencesView::practiceFeedbackDeliveryEnabled)
            .orElse(PreferencesView.PRACTICE_FEEDBACK_DELIVERY_ENABLED_BY_DEFAULT);
    }

    record PreferencesView(boolean participateInResearch, boolean practiceFeedbackDeliveryEnabled) {
        public static final boolean PRACTICE_FEEDBACK_DELIVERY_ENABLED_BY_DEFAULT = true;
    }
}
