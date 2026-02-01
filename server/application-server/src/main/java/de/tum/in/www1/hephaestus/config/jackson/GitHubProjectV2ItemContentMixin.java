package de.tum.in.www1.hephaestus.config.jackson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDraftIssue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequest;

/**
 * Jackson mixin for GitHub GraphQL ProjectV2ItemContent union type.
 * <p>
 * Configures polymorphic deserialization for project item content types
 * using the __typename field. This union represents the different content
 * types that can be associated with a project item:
 * <ul>
 *   <li>{@code Issue}: A GitHub Issue linked to the project</li>
 *   <li>{@code PullRequest}: A GitHub Pull Request linked to the project</li>
 *   <li>{@code DraftIssue}: A draft issue that hasn't been converted yet</li>
 * </ul>
 * <p>
 * Note: Using defaultImpl=GHIssue.class as fallback since Issues are the most
 * common content type in projects.
 *
 * @see <a href="https://docs.github.com/en/graphql/reference/unions#projectv2itemcontent">GitHub GraphQL API - ProjectV2ItemContent</a>
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "__typename",
    visible = true,
    defaultImpl = GHIssue.class
)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = GHIssue.class, name = "Issue"),
        @JsonSubTypes.Type(value = GHPullRequest.class, name = "PullRequest"),
        @JsonSubTypes.Type(value = GHDraftIssue.class, name = "DraftIssue"),
    }
)
public abstract class GitHubProjectV2ItemContentMixin {}
