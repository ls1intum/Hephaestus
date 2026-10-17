package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitLabMrResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmVersion;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.gitlab.enabled", havingValue = "true")
class GitLabDiffVersionReader {
    private final GitLabTokenService tokens;
    private final WebClient client;

    GitLabDiffVersionReader(GitLabTokenService tokens, WebClient.Builder builder) {
        this.tokens = tokens;
        client = builder.clone()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    GitLabDiffPositions.@Nullable Version read(
            long workspaceId, GitLabMrResolver.MrCoordinates mr, ItemVersion pinned) {
        String head = ScmVersion.reviewedHead(pinned);
        String root = tokens.resolveServerUrl(workspaceId).replaceAll("/+$", "") + "/api/v4/projects/"
                + URLEncoder.encode(mr.projectPath(), StandardCharsets.UTF_8) + "/merge_requests/" + mr.iid()
                + "/versions";
        try {
            for (int page = 1; page <= 50; page++) {
                var versions = get(workspaceId, root + "?per_page=100&page=" + page)
                        .bodyToMono(new ParameterizedTypeReference<List<GitLabDiffPositions.Version>>() {})
                        .block(GRAPHQL_TIMEOUT);
                if (versions == null) return null;
                for (var version : versions) {
                    if (version.id() <= 0
                            || !head.equals(version.head())
                            || !Objects.equals(pinned.baseVersion(), version.base())) continue;
                    var detail = get(workspaceId, root + "/" + version.id())
                            .bodyToMono(GitLabDiffPositions.Version.class)
                            .block(GRAPHQL_TIMEOUT);
                    if (detail != null
                            && detail.id() == version.id()
                            && head.equals(detail.head())
                            && Objects.equals(pinned.baseVersion(), detail.base())
                            && Objects.equals(version.start(), detail.start())) return detail;
                    return null;
                }
                if (versions.size() < 100) return null;
            }
        } catch (RuntimeException unavailable) {
            // An unavailable position can use the static link without claiming an inline delivery.
            return null;
        }
        return null;
    }

    private WebClient.ResponseSpec get(long workspaceId, String url) {
        return client.get()
                .uri(URI.create(url))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.getAccessToken(workspaceId))
                .attribute(GitLabGraphQlClientProvider.SCOPE_ID_ATTRIBUTE, workspaceId)
                .retrieve();
    }
}
