package de.tum.in.www1.hephaestus.gitprovider.common.github.graphql;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;

/**
 * Immutable description of a GitHub GraphQL request.
 */
public record GitHubGraphQlQuery<T>(
    String document,
    Map<String, Object> variables,
    Consumer<HttpHeaders> headerCustomizer,
    GitHubGraphQlResponseMapper<T> responseMapper,
    boolean failOnErrors
) {

    public GitHubGraphQlQuery {
        if (document == null || document.isBlank()) {
            throw new IllegalArgumentException("GraphQL document must be provided");
        }
        variables = variables == null ? Collections.emptyMap() : Map.copyOf(variables);
        headerCustomizer = headerCustomizer == null ? headers -> {} : headerCustomizer;
        responseMapper = Objects.requireNonNull(responseMapper, "responseMapper must be provided");
    }

    public static <T> Builder<T> builder(String document, GitHubGraphQlResponseMapper<T> responseMapper) {
        return new Builder<>(document, responseMapper);
    }

    public static final class Builder<T> {

        private final String document;
        private final GitHubGraphQlResponseMapper<T> responseMapper;
        private Map<String, Object> variables = Collections.emptyMap();
        private Consumer<HttpHeaders> headerCustomizer;
        private boolean failOnErrors = true;

        private Builder(String document, GitHubGraphQlResponseMapper<T> responseMapper) {
            this.document = document;
            this.responseMapper = responseMapper;
        }

        public Builder<T> variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public Builder<T> headers(Consumer<HttpHeaders> headerCustomizer) {
            this.headerCustomizer = headerCustomizer;
            return this;
        }

        public Builder<T> failOnErrors(boolean failOnErrors) {
            this.failOnErrors = failOnErrors;
            return this;
        }

        public GitHubGraphQlQuery<T> build() {
            return new GitHubGraphQlQuery<>(document, variables, headerCustomizer, responseMapper, failOnErrors);
        }
    }
}