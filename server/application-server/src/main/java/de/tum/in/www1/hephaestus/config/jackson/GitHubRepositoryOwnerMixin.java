package de.tum.in.www1.hephaestus.config.jackson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Organization;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.User;

/**
 * Jackson mixin for GitHub GraphQL RepositoryOwner interface.
 * <p>
 * Configures polymorphic deserialization for RepositoryOwner types using the __typename field.
 * A RepositoryOwner can be either an Organization or a User.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "__typename",
    visible = true,
    defaultImpl = User.class
)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = Organization.class, name = "Organization"),
        @JsonSubTypes.Type(value = User.class, name = "User"),
    }
)
public abstract class GitHubRepositoryOwnerMixin {}
