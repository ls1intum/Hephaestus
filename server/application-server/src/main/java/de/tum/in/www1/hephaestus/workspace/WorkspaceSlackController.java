package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controller for managing Slack integration settings within a workspace
 * context.
 * <p>
 * This controller is workspace-scoped, meaning all endpoints are prefixed with
 * {@code /workspaces/{workspaceSlug}}.
 */
@WorkspaceScopedController
public class WorkspaceSlackController {

  private final WorkspaceSlackIntegrationService slackIntegrationService;

  @Value("${hephaestus.slack.enabled:false}")
  private boolean slackEnabled;

  public WorkspaceSlackController(WorkspaceSlackIntegrationService slackIntegrationService) {
    this.slackIntegrationService = slackIntegrationService;
  }

  @GetMapping("/slack/install")
  @RequireAtLeastWorkspaceAdmin
  @Operation(summary = "Initiate Slack Bot Installation", description = "Redirects to Slack authorization page to install the Hephaestus bot to this workspace. "
      +
      "Requires workspace admin privileges and Slack integration to be enabled.")
  @ApiResponses({
      @ApiResponse(responseCode = "302", description = "Redirect to Slack authorization page"),
      @ApiResponse(responseCode = "400", description = "Slack integration is not enabled or not configured"),
      @ApiResponse(responseCode = "401", description = "User is not authenticated"),
      @ApiResponse(responseCode = "403", description = "User is not a workspace admin")
  })
  public void installSlackBot(WorkspaceContext workspaceContext, HttpServletResponse response) throws IOException {
    // Verify Slack is enabled before proceeding
    if (!slackEnabled) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Slack integration is not enabled. Set SLACK_LINKING_ENABLED=true to enable.");
    }

    try {
      String redirectUri = getCallbackRedirectUri();
      String installUrl = slackIntegrationService.generateInstallUrl(workspaceContext.slug(), redirectUri);
      response.sendRedirect(installUrl);
    } catch (IllegalStateException e) {
      // Thrown when client credentials are not configured
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Slack integration is not properly configured: " + e.getMessage());
    }
  }

  /**
   * Constructs the redirect URI for the OAuth callback.
   * This MUST match the path in {@link SlackCallbackController}.
   */
  private String getCallbackRedirectUri() {
    return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/integrations/slack/callback")
        .build()
        .toUriString();
  }
}
