package de.tum.in.www1.hephaestus.account;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.List;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing Slack account linking via Keycloak Identity Provider.
 *
 * <p>
 * Users can link their Slack account to their Hephaestus account through
 * Keycloak's
 * account management interface, enabling direct @-mentions in Slack
 * notifications.
 *
 * <p>
 * This service fetches the linked Slack identity from Keycloak and stores the
 * Slack User ID in the Hephaestus User entity for use in the weekly leaderboard
 * Slack mentions.
 */
@Service
public class SlackConnectionService {

    private static final Logger logger = LoggerFactory.getLogger(SlackConnectionService.class);
    private static final String SLACK_IDP_ALIAS = "slack";

    private final UserRepository userRepository;
    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${hephaestus.slack.enabled:false}")
    private boolean slackEnabled;

    public SlackConnectionService(UserRepository userRepository, Keycloak keycloak) {
        this.userRepository = userRepository;
        this.keycloak = keycloak;
    }

    /**
     * Checks if Slack integration is enabled for this instance.
     */
    public boolean isSlackEnabled() {
        return slackEnabled;
    }

    /**
     * Gets the Slack connection status for the current user.
     *
     * <p>
     * First checks the User entity for a stored slackUserId. If not found,
     * queries Keycloak for linked identities.
     *
     * @param user            the Hephaestus user
     * @param keycloakBaseUrl the base URL of Keycloak (e.g.
     *                        https://auth.example.com)
     * @return the connection status with link URL
     */
    public SlackConnectionDTO getConnectionStatus(User user, String keycloakBaseUrl) {
        String linkUrl = getLinkUrl(keycloakBaseUrl);
        String slackUserId = user.getSlackUserId();

        if (slackUserId != null && !slackUserId.isEmpty()) {
            return SlackConnectionDTO.connected(slackUserId, slackEnabled, null);
        }
        return SlackConnectionDTO.disconnected(slackEnabled, linkUrl);
    }

    /**
     * Syncs the Slack User ID from Keycloak federated identity to the User entity.
     *
     * <p>
     * This should be called after a user links their Slack account in Keycloak.
     *
     * @param user            the Hephaestus user
     * @param keycloakUserId  the user's Keycloak ID
     * @param keycloakBaseUrl the base URL of Keycloak
     * @return the updated connection status
     */
    @Transactional
    public SlackConnectionDTO syncSlackIdentity(User user, String keycloakUserId, String keycloakBaseUrl) {
        String linkUrl = getLinkUrl(keycloakBaseUrl);

        if (keycloakUserId == null || keycloakUserId.isEmpty()) {
            logger.warn("Cannot sync Slack identity: Keycloak user ID is null or empty");
            return SlackConnectionDTO.disconnected(slackEnabled, linkUrl);
        }

        try {
            List<FederatedIdentityRepresentation> identities = keycloak
                .realm(realm)
                .users()
                .get(keycloakUserId)
                .getFederatedIdentity();

            String slackUserId = identities
                .stream()
                .filter(identity -> SLACK_IDP_ALIAS.equals(identity.getIdentityProvider()))
                .map(FederatedIdentityRepresentation::getUserId)
                .findFirst()
                .orElse(null);

            if (slackUserId != null) {
                user.setSlackUserId(slackUserId);
                userRepository.save(user);
                logger.info("Synced Slack User ID '{}' for GitHub user '{}'", slackUserId, user.getLogin());
                return SlackConnectionDTO.connected(slackUserId, slackEnabled, null);
            } else {
                // User has not linked Slack, clear any stale slackUserId
                if (user.getSlackUserId() != null) {
                    user.setSlackUserId(null);
                    userRepository.save(user);
                    logger.info("Cleared Slack User ID for GitHub user '{}'", user.getLogin());
                }
                return SlackConnectionDTO.disconnected(slackEnabled, linkUrl);
            }
        } catch (Exception e) {
            logger.error("Failed to sync Slack identity for user '{}': {}", user.getLogin(), e.getMessage());
            // Return current status without updating
            return getConnectionStatus(user, keycloakBaseUrl);
        }
    }

    /**
     * Disconnects the Slack account by clearing the slackUserId from the user.
     *
     * <p>
     * Note: This only clears the local copy. The user must unlink in Keycloak
     * to fully disconnect.
     *
     * @param user            the Hephaestus user
     * @param keycloakBaseUrl the base URL of Keycloak
     * @return the disconnected status
     */
    @Transactional
    public SlackConnectionDTO disconnect(User user, String keycloakBaseUrl) {
        user.setSlackUserId(null);
        userRepository.save(user);

        logger.info("Cleared Slack User ID for GitHub user '{}'", user.getLogin());

        return SlackConnectionDTO.disconnected(slackEnabled, getLinkUrl(keycloakBaseUrl));
    }

    /**
     * Returns the URL for the Keycloak account management page where users can
     * link their Slack account.
     */
    public String getKeycloakAccountLinkUrl(String keycloakBaseUrl) {
        return getLinkUrl(keycloakBaseUrl);
    }

    private String getLinkUrl(String keycloakBaseUrl) {
        if (!slackEnabled || keycloakBaseUrl == null) {
            return null;
        }
        // Keycloak Account Console v3 - Identity Providers section
        return keycloakBaseUrl + "/realms/" + realm + "/account/#/security/linked-accounts";
    }
}
