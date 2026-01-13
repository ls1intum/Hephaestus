package de.tum.in.www1.hephaestus.gitprovider.user.github.dto;

import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.uriToString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHActor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHBot;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHEnterpriseUserAccount;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMannequin;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHOrganization;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub user references.
 * <p>
 * Used across all entities: issues, pull requests, comments, reviews, etc.
 * Provides factory methods for creating from both REST (webhook) and GraphQL responses.
 * <p>
 * <b>Profile Fields:</b> Contains optional profile fields (bio, company, location, blog)
 * that are populated when fetching full user details via GraphQL GetUser query.
 * These may be null for minimal user references from webhooks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUserDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("database_id") Long databaseId,
    @JsonProperty("login") String login,
    @JsonProperty("avatar_url") String avatarUrl,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("name") String name,
    @JsonProperty("email") String email,
    // Profile fields - populated from full user fetch, may be null from webhooks
    @JsonProperty("bio") String bio,
    @JsonProperty("company") String company,
    @JsonProperty("location") String location,
    @JsonProperty("blog") String blog,
    @JsonProperty("followers") Integer followers,
    @JsonProperty("following") Integer following,
    // Timestamp fields - populated from full user fetch via GraphQL
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt
) {
    /**
     * Compact constructor for backward compatibility with minimal user references.
     * Profile fields default to null.
     */
    public GitHubUserDTO(
        Long id,
        Long databaseId,
        String login,
        String avatarUrl,
        String htmlUrl,
        String name,
        String email
    ) {
        this(id, databaseId, login, avatarUrl, htmlUrl, name, email, null, null, null, null, null, null, null, null);
    }

    /**
     * Get the database ID, preferring databaseId over id for GraphQL responses.
     */
    public Long getDatabaseId() {
        return databaseId != null ? databaseId : id;
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubUserDTO from a GraphQL GHActor interface.
     * <p>
     * Handles all GHActor implementations: GHUser, GHBot, GHMannequin, GHOrganization, GHEnterpriseUserAccount.
     * Uses pattern matching to extract fields specific to each implementation.
     *
     * @param actor the GHActor (may be null)
     * @return GitHubUserDTO or null if actor is null
     */
    @Nullable
    public static GitHubUserDTO fromActor(@Nullable GHActor actor) {
        if (actor == null) {
            return null;
        }
        return switch (actor) {
            case GHUser user -> fromUser(user);
            case GHBot bot -> fromBot(bot);
            case GHMannequin mannequin -> fromMannequin(mannequin);
            case GHOrganization org -> fromOrganization(org);
            case GHEnterpriseUserAccount enterprise -> fromEnterpriseUserAccount(enterprise);
            default -> fromActorBase(actor);
        };
    }

    /**
     * Creates a GitHubUserDTO from a GraphQL GHUser model.
     * <p>
     * Captures all profile fields available from the GetUser query:
     * bio, company, location, websiteUrl, followers, following.
     */
    @Nullable
    public static GitHubUserDTO fromUser(@Nullable GHUser user) {
        if (user == null) {
            return null;
        }
        return new GitHubUserDTO(
            null,
            user.getDatabaseId() != null ? user.getDatabaseId().longValue() : null,
            user.getLogin(),
            uriToString(user.getAvatarUrl()),
            uriToString(user.getUrl()),
            user.getName(),
            user.getEmail(),
            // Profile fields
            user.getBio(),
            user.getCompany(),
            user.getLocation(),
            uriToString(user.getWebsiteUrl()),
            user.getFollowers() != null ? user.getFollowers().getTotalCount() : null,
            user.getFollowing() != null ? user.getFollowing().getTotalCount() : null,
            // Timestamp fields
            toInstant(user.getCreatedAt()),
            toInstant(user.getUpdatedAt())
        );
    }

    /**
     * Creates a GitHubUserDTO from a GraphQL GHBot model.
     */
    @Nullable
    public static GitHubUserDTO fromBot(@Nullable GHBot bot) {
        if (bot == null) {
            return null;
        }
        return new GitHubUserDTO(
            null,
            bot.getDatabaseId() != null ? bot.getDatabaseId().longValue() : null,
            bot.getLogin(),
            uriToString(bot.getAvatarUrl()),
            uriToString(bot.getUrl()),
            null,
            null
        );
    }

    /**
     * Creates a GitHubUserDTO from a GraphQL GHMannequin model.
     */
    @Nullable
    public static GitHubUserDTO fromMannequin(@Nullable GHMannequin mannequin) {
        if (mannequin == null) {
            return null;
        }
        return new GitHubUserDTO(
            null,
            mannequin.getDatabaseId() != null ? mannequin.getDatabaseId().longValue() : null,
            mannequin.getLogin(),
            uriToString(mannequin.getAvatarUrl()),
            uriToString(mannequin.getUrl()),
            null,
            mannequin.getEmail()
        );
    }

    /**
     * Creates a GitHubUserDTO from a GraphQL GHOrganization model (when acting as GHActor).
     */
    @Nullable
    public static GitHubUserDTO fromOrganization(@Nullable GHOrganization org) {
        if (org == null) {
            return null;
        }
        return new GitHubUserDTO(
            null,
            org.getDatabaseId() != null ? org.getDatabaseId().longValue() : null,
            org.getLogin(),
            uriToString(org.getAvatarUrl()),
            null,
            org.getName(),
            org.getEmail()
        );
    }

    /**
     * Creates a GitHubUserDTO from a GraphQL GHEnterpriseUserAccount model.
     */
    @Nullable
    public static GitHubUserDTO fromEnterpriseUserAccount(@Nullable GHEnterpriseUserAccount enterprise) {
        if (enterprise == null) {
            return null;
        }
        return new GitHubUserDTO(
            null,
            null,
            enterprise.getLogin(),
            uriToString(enterprise.getAvatarUrl()),
            uriToString(enterprise.getUrl()),
            enterprise.getName(),
            null
        );
    }

    /**
     * Fallback factory for GHActor interface when concrete type is unknown.
     */
    private static GitHubUserDTO fromActorBase(GHActor actor) {
        return new GitHubUserDTO(
            null,
            null,
            actor.getLogin(),
            uriToString(actor.getAvatarUrl()),
            uriToString(actor.getUrl()),
            null,
            null
        );
    }
}
