package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.Optional;

public interface AccountPreferencesQuery {
    /** One account's choices apply to every linked identity and workspace. */
    Optional<PreferencesView> preferencesForAccount(Long accountId);

    default boolean practiceFeedbackDeliveryEnabled(long accountId) {
        return preferencesForAccount(accountId)
                .map(PreferencesView::practiceFeedbackDeliveryEnabled)
                .orElse(PreferencesView.PRACTICE_FEEDBACK_DELIVERY_ENABLED_BY_DEFAULT);
    }

    record PreferencesView(boolean participateInResearch, boolean practiceFeedbackDeliveryEnabled) {
        public static final boolean PRACTICE_FEEDBACK_DELIVERY_ENABLED_BY_DEFAULT = true;
    }
}
