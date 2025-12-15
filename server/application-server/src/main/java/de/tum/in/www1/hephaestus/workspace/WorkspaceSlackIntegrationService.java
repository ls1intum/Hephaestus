package de.tum.in.www1.hephaestus.workspace;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.oauth.OAuthV2AccessRequest;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for handling the "Add to Slack" OAuth flow for Workspaces.
 * <p>
 * This enables the Hephaestus bot to be installed into a user's Slack
 * workspace.
 * The service implements proper CSRF protection using HMAC-signed state tokens.
 * <p>
 * Security measures:
 * <ul>
 * <li>HMAC-SHA256 signed state tokens with 10-minute expiry</li>
 * <li>Constant-time signature comparison to prevent timing attacks</li>
 * <li>Proper URL encoding of all OAuth parameters</li>
 * </ul>
 */
@Service
public class WorkspaceSlackIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceSlackIntegrationService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long STATE_EXPIRY_SECONDS = 600; // 10 minutes

    private final WorkspaceRepository workspaceRepository;
    private final String clientId;
    private final String clientSecret;
    private final Slack slack;

    @Autowired
    public WorkspaceSlackIntegrationService(
            WorkspaceRepository workspaceRepository,
            @Value("${hephaestus.slack.client-id}") String clientId,
            @Value("${hephaestus.slack.client-secret}") String clientSecret) {
        this(workspaceRepository, clientId, clientSecret, Slack.getInstance());
    }

    WorkspaceSlackIntegrationService(
            WorkspaceRepository workspaceRepository,
            String clientId,
            String clientSecret,
            Slack slack) {
        this.workspaceRepository = workspaceRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.slack = slack;
    }

    /**
     * Generates the "Add to Slack" URL with a signed state parameter.
     * <p>
     * The state contains: base64(workspaceSlug|timestamp|signature)
     * This provides CSRF protection without requiring server-side session storage.
     *
     * @param workspaceSlug The slug of the workspace initiating the installation
     * @param redirectUri   The global callback URI
     * @return The Slack authorization URL with properly encoded parameters
     */
    public String generateInstallUrl(String workspaceSlug, String redirectUri) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Slack client credentials are not configured.");
        }

        String state = generateSignedState(workspaceSlug);

        // Bot scopes for the leaderboard notification feature
        String scope = "chat:write,channels:read,users:read,team:read";

        // All parameters must be URL-encoded per OAuth 2.0 spec
        return "https://slack.com/oauth/v2/authorize" +
                "?client_id=" + urlEncode(clientId) +
                "&scope=" + urlEncode(scope) +
                "&redirect_uri=" + urlEncode(redirectUri) +
                "&state=" + urlEncode(state);
    }

    /**
     * Completes the installation by exchanging the code for an access token.
     * <p>
     * Validates the state parameter's signature and expiry before proceeding.
     *
     * @param code        The OAuth code from Slack
     * @param state       The signed state parameter
     * @param redirectUri The redirect URI used in the request
     * @return The slug of the workspace that was connected
     * @throws IllegalArgumentException if state validation fails
     * @throws IllegalStateException    if Slack API call fails
     */
    @Transactional
    public String completeInstallation(String code, String state, String redirectUri) {
        String workspaceSlug = validateAndParseState(state);

        Workspace workspace = workspaceRepository
                .findByWorkspaceSlug(workspaceSlug)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceSlug));

        try {
            OAuthV2AccessRequest request = OAuthV2AccessRequest.builder()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .code(code)
                    .redirectUri(redirectUri)
                    .build();

            OAuthV2AccessResponse response = slack.methods().oauthV2Access(request);

            if (!response.isOk()) {
                logger.error("Slack OAuth failed: {}", response.getError());
                throw new IllegalStateException("Slack OAuth failed: " + response.getError());
            }

            workspace.setSlackToken(response.getAccessToken());
            workspaceRepository.save(workspace);

            logger.info(
                    "Successfully connected Workspace '{}' to Slack Team '{}' ({})",
                    workspaceSlug,
                    response.getTeam().getName(),
                    response.getTeam().getId());

            return workspaceSlug;
        } catch (IOException | SlackApiException e) {
            logger.error("Failed to exchange Slack code", e);
            throw new IllegalStateException("Failed to connect to Slack: " + e.getMessage(), e);
        }
    }

    /**
     * Generates an HMAC-signed state token.
     * <p>
     * Format: base64url(base64url(workspaceSlug) + "|" + timestamp + "|" +
     * signature)
     * <p>
     * The workspace slug is base64-encoded to safely handle any special characters
     * (including the pipe delimiter) without risking injection attacks.
     */
    private String generateSignedState(String workspaceSlug) {
        // Encode the slug to safely handle any characters including "|"
        String encodedSlug = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(workspaceSlug.getBytes(StandardCharsets.UTF_8));
        long timestamp = Instant.now().getEpochSecond();
        String payload = encodedSlug + "|" + timestamp;
        String signature = sign(payload);
        String fullState = payload + "|" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(fullState.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates the state token's signature and expiry, then returns the workspace
     * slug.
     * <p>
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @throws IllegalArgumentException if validation fails
     */
    private String validateAndParseState(String encodedState) {
        if (encodedState == null || encodedState.isBlank()) {
            throw new IllegalArgumentException("Missing state parameter");
        }

        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(encodedState), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid state encoding");
        }

        String[] parts = decoded.split("\\|");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed state parameter");
        }

        String encodedSlug = parts[0];
        String workspaceSlug;
        try {
            workspaceSlug = new String(Base64.getUrlDecoder().decode(encodedSlug), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid workspace slug encoding");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid state timestamp");
        }
        String providedSignature = parts[2];

        // Verify signature using constant-time comparison to prevent timing attacks
        String payload = encodedSlug + "|" + timestamp;
        String expectedSignature = sign(payload);
        if (!constantTimeEquals(expectedSignature, providedSignature)) {
            logger.warn("State signature mismatch for workspace '{}' - possible CSRF attempt", workspaceSlug);
            throw new IllegalArgumentException("Invalid state signature");
        }

        // Verify expiry
        long now = Instant.now().getEpochSecond();
        if (now - timestamp > STATE_EXPIRY_SECONDS) {
            throw new IllegalArgumentException("State token expired");
        }

        return workspaceSlug;
    }

    /**
     * Computes HMAC-SHA256 signature using the client secret as the key.
     */
    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     * <p>
     * Uses MessageDigest.isEqual which is specifically designed for cryptographic
     * comparisons.
     */
    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    /**
     * URL-encodes a string for use in query parameters.
     */
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
