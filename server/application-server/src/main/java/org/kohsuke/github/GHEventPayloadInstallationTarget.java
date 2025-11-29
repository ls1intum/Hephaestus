package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload for GitHub installation_target webhook events.
 * <p>
 * This event is sent when the installation target (user/organization) is renamed.
 * hub4j/github-api (as of 2.0-rc.5) does not provide a {@code GHEventPayload} subclass
 * for this event type. Once upstream adds native support, this class should be
 * deprecated in favor of the official implementation.
 * </p>
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#installation_target">
 *      GitHub Docs: installation_target webhook</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadInstallationTarget extends GHEventPayload {

    private Account account;

    @JsonProperty("target_type")
    private String targetType;

    private Changes changes;

    private GHAppInstallation installation;

    /**
     * Gets the account (user or organization) that owns the installation.
     *
     * @return the account
     */
    public Account getAccount() {
        return account;
    }

    /**
     * Gets the target type (User or Organization).
     *
     * @return the target type
     */
    public String getTargetType() {
        return targetType;
    }

    /**
     * Gets the changes object containing rename information.
     *
     * @return the changes
     */
    public Changes getChanges() {
        return changes;
    }

    /**
     * Gets the installation affected by this event.
     *
     * @return the installation
     */
    @Override
    public GHAppInstallation getInstallation() {
        return installation;
    }

    /**
     * Account information for the installation target.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Account {

        private Long id;
        private String login;

        /**
         * Gets the account ID.
         *
         * @return the id
         */
        public Long getId() {
            return id;
        }

        /**
         * Gets the account login name.
         *
         * @return the login
         */
        public String getLogin() {
            return login;
        }
    }

    /**
     * Changes descriptor for rename events.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Changes {

        private LoginChange login;

        /**
         * Gets the login change details.
         *
         * @return the login change
         */
        public LoginChange getLogin() {
            return login;
        }

        /**
         * Login change details containing the previous value.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class LoginChange {

            private String from;

            /**
             * Gets the previous login value.
             *
             * @return the previous login
             */
            public String getFrom() {
                return from;
            }
        }
    }
}
