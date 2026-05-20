package de.tum.cit.aet.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating Slack credentials (token and signing secret) for a workspace.
 * These credentials are used for Slack API integration and webhook signature verification.
 */
@Schema(description = "Request to update Slack integration credentials")
public record UpdateWorkspaceSlackCredentialsRequestDTO(
    @NotBlank(message = "Slack token is required")
    @Schema(description = "Slack Bot User OAuth Token for API access")
    String slackToken,

    @NotBlank(message = "Slack signing secret is required")
    @Schema(description = "Slack Signing Secret for webhook verification")
    String slackSigningSecret
) {}
