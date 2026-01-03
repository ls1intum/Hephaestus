package de.tum.in.www1.hephaestus.practices.detection;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Service for fetching pull request templates from GitHub repositories.
 *
 * <p>Templates are cached and periodically refreshed to minimize API calls
 * while ensuring reasonable freshness.
 *
 * <p>Workspace-agnostic: This is a cache management component. The scheduled
 * eviction is infrastructure-level maintenance, not tenant-specific. Template
 * fetching is keyed by repository name which has implicit workspace scope.
 */
@Component
@WorkspaceAgnostic("Cache management - scheduled eviction is infrastructure maintenance")
public class PullRequestTemplateGetter {

    private static final Logger logger = LoggerFactory.getLogger(PullRequestTemplateGetter.class);

    private static final String TEMPLATE_URL =
        "https://raw.githubusercontent.com/%s/main/.github/PULL_REQUEST_TEMPLATE.md";

    private final RestTemplate restTemplate = new RestTemplate();

    @Cacheable(value = "pullRequestTemplates")
    public String getPullRequestTemplate(String nameWithOwner) {
        String url = String.format(TEMPLATE_URL, nameWithOwner);
        try {
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            logger.warn("Error getting pull request template: {}", e.getMessage());
            return "";
        }
    }

    @CacheEvict(value = "pullRequestTemplates", allEntries = true)
    @Scheduled(fixedRateString = "3600000")
    public void evictPullRequestTemplateCache() {}
}
