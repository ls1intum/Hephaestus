package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.Optional;

public interface AccountPreferencesQuery {
    Optional<PreferencesView> preferencesForLogin(String login);

    Optional<PreferencesView> preferencesForUserId(long userId);

    record PreferencesView(boolean participateInResearch, boolean practiceFeedbackDeliveryEnabled) {}
}
