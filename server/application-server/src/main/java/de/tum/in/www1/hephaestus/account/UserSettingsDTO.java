package de.tum.in.www1.hephaestus.account;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

@Schema(description = "User preferences and settings")
public record UserSettingsDTO(
    @NonNull @Schema(description = "Whether the user wants to receive notifications") Boolean receiveNotifications,
    @NonNull
    @Schema(description = "Whether the user consents to participate in research studies")
    Boolean participateInResearch
) {}
