package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload for GitHub Organization events.
 *
 * Supports actions such as:
 * - member_added / member_removed (via {@link #membership})
 * - member_invited (via {@link #invitation} and {@link #user})
 * - renamed (via {@link #changes})
 *
 * Unknown JSON fields are ignored to remain forward-compatible with GitHub's API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadOrganization extends GHEventPayload {

    /** Details about a membership change (member_added / member_removed). */
    private Membership membership;

    /** Details about an invitation (member_invited). */
    private Invitation invitation;

    /** Details about changes (e.g., renamed -> changes.login.from). */
    private Changes changes;

    /** The affected user for certain actions (e.g., member_invited). */
    private GHUser user;

    /**
     * The membership object when the action relates to membership changes.
     */
    public Membership getMembership() {
        return membership;
    }

    /**
     * The invitation object when the action relates to invitations.
     */
    public Invitation getInvitation() {
        return invitation;
    }

    /**
     * The changes object when the action contains a changes descriptor (e.g., renamed).
     */
    public Changes getChanges() {
        return changes;
    }

    /**
     * The user affected by the action (e.g., invited user for member_invited).
     */
    public GHUser getUser() {
        return user;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Membership {

        private String url;
        private String state;
        private String role;

        @JsonProperty("organization_url")
        private String organizationUrl;

        private GHUser user;

        public String getUrl() {
            return url;
        }

        public String getState() {
            return state;
        }

        public String getRole() {
            return role;
        }

        public String getOrganizationUrl() {
            return organizationUrl;
        }

        public GHUser getUser() {
            return user;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Invitation {

        private long id;

        @JsonProperty("node_id")
        private String nodeId;

        private String login;
        private String email;
        private String role;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("failed_at")
        private String failedAt;

        @JsonProperty("failed_reason")
        private String failedReason;

        private GHUser inviter;

        @JsonProperty("team_count")
        private int teamCount;

        @JsonProperty("invitation_teams_url")
        private String invitationTeamsUrl;

        @JsonProperty("invitation_source")
        private String invitationSource;

        public long getId() {
            return id;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getLogin() {
            return login;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getFailedAt() {
            return failedAt;
        }

        public String getFailedReason() {
            return failedReason;
        }

        public GHUser getInviter() {
            return inviter;
        }

        public int getTeamCount() {
            return teamCount;
        }

        public String getInvitationTeamsUrl() {
            return invitationTeamsUrl;
        }

        public String getInvitationSource() {
            return invitationSource;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Changes {

        private LoginChange login;

        public LoginChange getLogin() {
            return login;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class LoginChange {

            private String from;

            public String getFrom() {
                return from;
            }
        }
    }
}
