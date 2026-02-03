package de.tum.in.www1.hephaestus.config.jackson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHOrganization;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;

/**
 * Jackson mixin for GitHub GraphQL ProjectV2Owner interface.
 * <p>
 * Configures polymorphic deserialization for ProjectV2Owner types using the __typename field.
 * A ProjectV2Owner can be either an Organization or a User.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "__typename",
    visible = true,
    defaultImpl = GHOrganization.class
)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = GHOrganization.class, name = "Organization"),
        @JsonSubTypes.Type(value = GHUser.class, name = "User"),
    }
)
public abstract class GitHubProjectV2OwnerMixin {}
