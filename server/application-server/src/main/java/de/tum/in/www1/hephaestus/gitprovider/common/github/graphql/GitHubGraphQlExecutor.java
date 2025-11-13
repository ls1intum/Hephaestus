package de.tum.in.www1.hephaestus.gitprovider.common.github.graphql;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Central entry point for executing GitHub GraphQL requests per workspace.
 */
@Component
public class GitHubGraphQlExecutor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubGraphQlExecutor.class);

    private final HttpGraphQlClient baseClient;
    private final WorkspaceRepository workspaceRepository;
    private final GitHubAppTokenService appTokenService;

    public GitHubGraphQlExecutor(
        WebClient.Builder webClientBuilder,
        @Value("${github.api.graphql-url:https://api.github.com/graphql}") String graphQlUrl,
        WorkspaceRepository workspaceRepository,
        GitHubAppTokenService appTokenService
    ) {
        this.baseClient = HttpGraphQlClient
            .builder(
                webClientBuilder
                    .clone()
                    .baseUrl(graphQlUrl)
                    .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .defaultHeader("X-Github-Next-Global-ID", "1")
                    .exchangeStrategies(
                        ExchangeStrategies
                            .builder()
                            .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(256 * 1024 * 1024))
                            .build()
                    )
                    .build()
            )
            .build();
        this.workspaceRepository = workspaceRepository;
        this.appTokenService = appTokenService;
    }

    public <T> T execute(Long workspaceId, GitHubGraphQlQuery<T> query) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        String token = resolveToken(workspace);
        var builder = baseClient
            .mutate()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        builder.headers(query.headerCustomizer());

        HttpGraphQlClient client = builder.build();

        var response = client
            .document(query.document())
            .variables(query.variables())
            .executeSync();

        if (query.failOnErrors() && !CollectionUtils.isEmpty(response.getErrors())) {
            logger.error("GitHub GraphQL returned errors {}", response.getErrors());
            throw new GitHubGraphQlException("GitHub GraphQL request failed", response.getErrors());
        }

        return query.responseMapper().map(response);
    }

    private String resolveToken(Workspace workspace) {
        return switch (workspace.getGitProviderMode()) {
            case GITHUB_APP_INSTALLATION -> {
                Long installationId = workspace.getInstallationId();
                if (installationId == null) {
                    throw new IllegalStateException("Workspace " + workspace.getId() + " has no installation id");
                }
                yield appTokenService.getInstallationToken(installationId);
            }
            case PAT_ORG -> {
                String token = workspace.getPersonalAccessToken();
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("Workspace " + workspace.getId() + " has no personal access token");
                }
                yield token;
            }
        };
    }
}