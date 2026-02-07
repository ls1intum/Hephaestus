package de.tum.in.www1.hephaestus.config.jackson;

import com.fasterxml.jackson.annotation.JsonAlias;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestState;

/**
 * Jackson mixin for GitHub GraphQL PullRequest type.
 * <p>
 * Maps the aliased "prState" field back to the "state" property.
 * This is needed because GraphQL queries use aliases to avoid field conflicts
 * when Issue.state (IssueState!) and PullRequest.state (PullRequestState!)
 * are queried in the same fragment.
 */
public abstract class GitHubPullRequestMixin {

    @JsonAlias("prState")
    abstract GHPullRequestState getState();
}
