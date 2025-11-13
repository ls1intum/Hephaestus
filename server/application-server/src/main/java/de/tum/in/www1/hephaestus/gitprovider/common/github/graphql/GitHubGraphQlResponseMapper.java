package de.tum.in.www1.hephaestus.gitprovider.common.github.graphql;

import org.springframework.graphql.client.ClientGraphQlResponse;

@FunctionalInterface
public interface GitHubGraphQlResponseMapper<T> {
    T map(ClientGraphQlResponse response);
}
