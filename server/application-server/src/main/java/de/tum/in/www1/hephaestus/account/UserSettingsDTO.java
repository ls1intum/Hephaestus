package de.tum.in.www1.hephaestus.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "User preferences and settings")
public record UserSettingsDTO(
    @NotNull(message = "receiveNotifications must not be null")
    @Schema(description = "Whether the user wants to receive notifications")
    Boolean receiveNotifications,
    @NotNull(message = "participateInResearch must not be null")
    @Schema(description = "Whether the user consents to participate in research studies")
    Boolean participateInResearch,
    @NotNull(message = "aiReviewEnabled must not be null")
    @Schema(description = "Whether the user wants to receive AI-generated practice review comments on pull requests")
    Boolean aiReviewEnabled
) {}
