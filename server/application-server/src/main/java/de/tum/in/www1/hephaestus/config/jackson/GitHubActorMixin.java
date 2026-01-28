package de.tum.in.www1.hephaestus.config.jackson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHBot;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHEnterpriseUserAccount;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMannequin;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHOrganization;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;

/**
 * Jackson mixin for GitHub GraphQL Actor interface.
 * <p>
 * Configures polymorphic deserialization for Actor types using the __typename field.
 * This is required because GitHub's Actor interface can be User, Bot, Organization,
 * Mannequin, or EnterpriseUserAccount.
 * <p>
 * Note: Using defaultImpl=User.class as fallback when __typename is missing. This handles
 * the common case where only User inline fragments are used in GraphQL queries.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "__typename",
    visible = true,
    defaultImpl = GHUser.class
)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = GHUser.class, name = "User"),
        @JsonSubTypes.Type(value = GHBot.class, name = "Bot"),
        @JsonSubTypes.Type(value = GHOrganization.class, name = "Organization"),
        @JsonSubTypes.Type(value = GHMannequin.class, name = "Mannequin"),
        @JsonSubTypes.Type(value = GHEnterpriseUserAccount.class, name = "EnterpriseUserAccount"),
    }
)
public abstract class GitHubActorMixin {}
