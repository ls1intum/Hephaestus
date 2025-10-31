package de.tum.in.www1.hephaestus.gitprovider.user;

import org.springframework.lang.NonNull;

public record UserSettingsDTO(@NonNull Boolean receiveNotifications, @NonNull Boolean participateInResearch) {}
