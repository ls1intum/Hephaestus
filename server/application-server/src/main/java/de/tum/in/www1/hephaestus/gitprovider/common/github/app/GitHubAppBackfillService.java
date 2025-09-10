package de.tum.in.www1.hephaestus.gitprovider.common.github.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.workspace.RepositoryAlreadyMonitoredException;
import de.tum.in.www1.hephaestus.workspace.RepositoryNotFoundException;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubAppBackfillService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubAppBackfillService.class);

    private final GitHubAppTokenService tokenService;
    private final WorkspaceService workspaceService;

    public GitHubAppBackfillService(GitHubAppTokenService tokenService, WorkspaceService workspaceService) {
        this.tokenService = tokenService;
        this.workspaceService = workspaceService;
    }

    /**
     * Adds all installation-granted repositories to RepositoryToMonitor.
     * Uses REST /installation/repositories (installation token) and follows Link pagination.
     */
    public void seedReposForWorkspace(Workspace ws) {
        final Long installationId = ws.getInstallationId();
        final String org = ws.getAccountLogin();
        if (installationId == null) {
            logger.warn("seedReposForWorkspace: workspace {} has no installationId; skipping.", ws.getId());
            return;
        }

        final String token;
        try {
            token = tokenService.getInstallationToken(installationId);
        } catch (Exception e) {
            logger.error(
                "Failed to mint installation token for installationId={}: {}",
                installationId,
                e.getMessage(),
                e
            );
            return;
        }

        logger.info(
            "Seeding repositories for org={} installationId={} (selection={})",
            org,
            installationId,
            ws.getGithubRepositorySelection()
        );

        final HttpClient http = HttpClient.newHttpClient();
        final ObjectMapper om = new ObjectMapper();

        String nextUrl = "https://api.github.com/installation/repositories?per_page=100&page=1";
        int page = 1, totalAdded = 0;

        while (nextUrl != null) {
            try {
                var req = HttpRequest.newBuilder(URI.create(nextUrl))
                    .header("Authorization", "token " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "Hephaestus/Backfill")
                    .GET()
                    .build();

                var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                String body = resp.body();

                if (code == 401 || code == 403) {
                    logger.error(
                        "GitHub auth failed listing repos (installationId={}): {} {}",
                        installationId,
                        code,
                        body
                    );
                    break;
                }
                if (code < 200 || code >= 300) {
                    logger.warn(
                        "GitHub responded {} while listing repos (installationId={}): {}",
                        code,
                        installationId,
                        body
                    );
                    break;
                }

                var root = om.readTree(body);
                var repos = root.get("repositories");
                if (repos == null || !repos.isArray() || repos.isEmpty()) {
                    logger.info("No repositories on page {} for installationId={}. Stopping.", page, installationId);
                    break;
                }

                int addedThisPage = 0;
                for (var node : repos) {
                    var fullNameNode = node.get("full_name");
                    if (fullNameNode == null || fullNameNode.isNull()) continue;
                    String fullName = fullNameNode.asText();
                    try {
                        workspaceService.addRepositoryToMonitor(fullName);
                        addedThisPage++;
                        totalAdded++;
                    } catch (RepositoryAlreadyMonitoredException ignore) {
                        // already present → ok
                    } catch (RepositoryNotFoundException e) {
                        logger.warn("Repo not found (skipping): {}", fullName);
                    } catch (Exception ex) {
                        logger.warn("Failed to add repo {} to monitors: {}", fullName, ex.getMessage());
                    }
                }

                logger.info(
                    "Page {}: processed {} repos (added {}), installationId={}",
                    page,
                    repos.size(),
                    addedThisPage,
                    installationId
                );

                // Follow RFC5988 Link header for pagination (`rel="next"`).
                nextUrl = null;
                var link = resp.headers().firstValue("Link").orElse(null);
                if (link != null) {
                    for (String part : link.split(",")) {
                        String[] seg = part.trim().split(";");
                        if (seg.length >= 2 && seg[1].trim().equals("rel=\"next\"")) {
                            String url = seg[0].trim();
                            if (url.startsWith("<") && url.endsWith(">")) {
                                nextUrl = url.substring(1, url.length() - 1);
                            }
                        }
                    }
                }
                page++;
            } catch (Exception e) {
                logger.error(
                    "Error while listing installation repositories (installationId={}): {}",
                    installationId,
                    e.getMessage(),
                    e
                );
                break;
            }
        }

        logger.info(
            "Seeding complete for org={} installationId={} — total added this run: {}",
            org,
            installationId,
            totalAdded
        );
    }
}
