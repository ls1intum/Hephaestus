package de.tum.in.www1.hephaestus.workspace.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating Slack credentials (token and signing secret) for a workspace.
 * These credentials are used for Slack API integration and webhook signature verification.
 */
public record UpdateWorkspaceSlackCredentialsRequestDTO(
    @NotBlank(message = "Slack token is required")
    String slackToken,

    @NotBlank(message = "Slack signing secret is required")
    String slackSigningSecret
) {}
