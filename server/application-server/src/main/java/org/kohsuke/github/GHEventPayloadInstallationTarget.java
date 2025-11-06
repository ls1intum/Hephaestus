package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true, value = { "issue" })
public class GHEventPayloadInstallationTarget extends GHEventPayload {

    private String action;

    @JsonProperty("target_type")
    private String targetType;

    private Account account;

    private Changes changes;

    private Sender sender;

    private InstallationRef installation;

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Account getAccount() {
        return account;
    }

    public Changes getChanges() {
        return changes;
    }

    public Sender getSenderRef() {
        return sender;
    }

    public InstallationRef getInstallationRef() {
        return installation;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Account {

        private String login;

        private long id;

        @JsonProperty("node_id")
        private String nodeId;

        @JsonProperty("avatar_url")
        private String avatarUrl;

        @JsonProperty("html_url")
        private String htmlUrl;

        private String description;

        @JsonProperty("is_verified")
        private Boolean verified;

        @JsonProperty("has_organization_projects")
        private Boolean hasOrganizationProjects;

        @JsonProperty("has_repository_projects")
        private Boolean hasRepositoryProjects;

        @JsonProperty("public_repos")
        private Integer publicRepos;

        @JsonProperty("public_gists")
        private Integer publicGists;

        private Integer followers;

        private Integer following;

        @JsonProperty("archived_at")
        private OffsetDateTime archivedAt;

        @JsonProperty("created_at")
        private OffsetDateTime createdAt;

        @JsonProperty("updated_at")
        private OffsetDateTime updatedAt;

        @JsonProperty("site_admin")
        private Boolean siteAdmin;

        private String type;

        public String getLogin() {
            return login;
        }

        public long getId() {
            return id;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public String getHtmlUrl() {
            return htmlUrl;
        }

        public String getDescription() {
            return description;
        }

        public Boolean getVerified() {
            return verified;
        }

        public Boolean getHasOrganizationProjects() {
            return hasOrganizationProjects;
        }

        public Boolean getHasRepositoryProjects() {
            return hasRepositoryProjects;
        }

        public Integer getPublicRepos() {
            return publicRepos;
        }

        public Integer getPublicGists() {
            return publicGists;
        }

        public Integer getFollowers() {
            return followers;
        }

        public Integer getFollowing() {
            return following;
        }

        public Instant getArchivedAt() {
            return archivedAt != null ? archivedAt.toInstant() : null;
        }

        public Instant getCreatedAt() {
            return createdAt != null ? createdAt.toInstant() : null;
        }

        public Instant getUpdatedAt() {
            return updatedAt != null ? updatedAt.toInstant() : null;
        }

        public Boolean getSiteAdmin() {
            return siteAdmin;
        }

        public String getType() {
            return type;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Changes {

        private Login login;

        public Login getLogin() {
            return login;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Login {

            private String from;

            public String getFrom() {
                return from;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sender {

        private String login;
        private long id;

        public String getLogin() {
            return login;
        }

        public long getId() {
            return id;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstallationRef {

        private long id;

        @JsonProperty("node_id")
        private String nodeId;

        public long getId() {
            return id;
        }

        public String getNodeId() {
            return nodeId;
        }
    }
}
