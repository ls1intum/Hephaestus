package de.tum.in.www1.hephaestus.config.jackson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMannequin;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHTeam;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;

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
    defaultImpl = GHUser.class
)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = GHUser.class, name = "User"),
        @JsonSubTypes.Type(value = GHTeam.class, name = "Team"),
        @JsonSubTypes.Type(value = GHMannequin.class, name = "Mannequin"),
    }
)
public abstract class GitHubRequestedReviewerMixin {}
