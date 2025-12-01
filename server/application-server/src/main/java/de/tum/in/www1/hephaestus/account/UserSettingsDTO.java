package de.tum.in.www1.hephaestus.account;

import org.springframework.lang.NonNull;

public record UserSettingsDTO(@NonNull Boolean receiveNotifications, @NonNull Boolean participateInResearch) {}
