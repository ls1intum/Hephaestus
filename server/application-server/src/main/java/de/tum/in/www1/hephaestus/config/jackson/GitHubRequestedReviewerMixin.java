package de.tum.in.www1.hephaestus.config.jackson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Mannequin;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Team;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.User;

/**
 * Jackson mixin for GitHub GraphQL RequestedReviewer interface.
 * <p>
 * Configures polymorphic deserialization for RequestedReviewer types.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "__typename",
    visible = true,
    defaultImpl = User.class
)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = User.class, name = "User"),
        @JsonSubTypes.Type(value = Team.class, name = "Team"),
        @JsonSubTypes.Type(value = Mannequin.class, name = "Mannequin"),
    }
)
public abstract class GitHubRequestedReviewerMixin {}
