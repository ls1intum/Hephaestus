package de.tum.cit.aet.hephaestus.integration.slack.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;

/**
 * Request to allow-list a Slack channel for monitoring. The channel lands in {@code PENDING}; activation is a
 * separate, explicit admin act. Idempotent on the natural key {@code (workspace, slackChannelId)}.
 */
@Schema(description = "Allow-list a Slack channel (lands in PENDING)")
public record RegisterSlackChannelRequestDTO(
    @NonNull
    @NotBlank
    @Size(max = 32)
    @Schema(description = "Slack channel id (the stable C… id)", requiredMode = Schema.RequiredMode.REQUIRED)
    String slackChannelId,

    @Size(max = 256)
    @Schema(description = "Optional human-readable channel name to store alongside the id")
    String channelName
) {}
