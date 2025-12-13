package de.tum.in.www1.hephaestus.workspace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controller for handling global Slack integration callbacks.
 * <p>
 * This controller handles the OAuth callback from Slack, which is not
 * workspace-scoped
 * in the URL (the workspace context is passed via the signed 'state'
 * parameter).
 * <p>
 * Security: The state parameter is HMAC-signed to prevent CSRF attacks.
 */
@RestController
@RequestMapping("/integrations/slack")
@Tag(name = "Workspace Integration", description = "Manage external integrations for workspaces")
public class SlackCallbackController {

  private static final Logger logger = LoggerFactory.getLogger(SlackCallbackController.class);

  private final WorkspaceSlackIntegrationService slackIntegrationService;

  @Value("${hephaestus.webapp.url:}")
  private String webappUrl;

  public SlackCallbackController(WorkspaceSlackIntegrationService slackIntegrationService) {
    this.slackIntegrationService = slackIntegrationService;
  }

  @GetMapping("/callback")
  @Operation(summary = "Slack OAuth Callback", description = "Handles the callback from Slack after bot installation. "
      +
      "Validates the HMAC-signed state parameter and exchanges the authorization code for an access token.")
  @ApiResponses({
      @ApiResponse(responseCode = "302", description = "Redirect to workspace settings on success or error page on failure"),
      @ApiResponse(responseCode = "400", description = "Missing required OAuth parameters")
  })
  public void slackCallback(
      @Parameter(description = "OAuth authorization code from Slack") @RequestParam(value = "code", required = false) String code,
      @Parameter(description = "HMAC-signed state containing workspace slug") @RequestParam(value = "state", required = false) String state,
      @Parameter(description = "Error code if user cancelled or Slack returned an error") @RequestParam(value = "error", required = false) String error,
      HttpServletResponse response) throws IOException {
    // Handle user cancellation or Slack-side errors
    if (error != null) {
      logger.info("Slack OAuth was cancelled or errored: {}", error);
      redirectToWebapp(response, "/settings", "slack", "cancelled");
      return;
    }

    if (code == null || state == null) {
      logger.warn("Slack callback missing required parameters: code={}, state={}", code != null, state != null);
      response.sendError(HttpStatus.BAD_REQUEST.value(), "Missing required OAuth parameters");
      return;
    }

    try {
      String redirectUri = getRedirectUri();
      String workspaceSlug = slackIntegrationService.completeInstallation(code, state, redirectUri);

      // Redirect to workspace admin settings
      redirectToWebapp(response, "/w/" + workspaceSlug + "/admin/settings", "slack", "success");
    } catch (IllegalArgumentException e) {
      // State validation failed (expired, invalid signature, etc.)
      logger.warn("Slack OAuth state validation failed: {}", e.getMessage());
      redirectToWebapp(response, "/settings", "slack", "invalid");
    } catch (Exception e) {
      logger.error("Slack installation failed", e);
      redirectToWebapp(response, "/settings", "slack", "error");
    }
  }

  /**
   * Redirects to the frontend webapp with the given path and query parameter.
   * Uses the configured webapp URL for proper handling behind reverse proxies.
   */
  private void redirectToWebapp(HttpServletResponse response, String path, String param, String value)
      throws IOException {
    String baseUrl = webappUrl != null && !webappUrl.isBlank() ? webappUrl : "";
    String url = baseUrl + path + "?" + param + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    response.sendRedirect(url);
  }

  /**
   * Constructs the redirect URI for the OAuth callback.
   * This MUST match the path registered in the Slack App's OAuth settings.
   */
  private String getRedirectUri() {
    return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/integrations/slack/callback")
        .build()
        .toUriString();
  }
}
