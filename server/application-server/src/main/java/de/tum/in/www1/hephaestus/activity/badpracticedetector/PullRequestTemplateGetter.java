package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
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
            logger.info("Error getting pull request template: {}", e.getMessage());
            return "";
        }
    }

    @CacheEvict(value = "pullRequestTemplates", allEntries = true)
    @Scheduled(fixedRateString = "3600000")
    public void evictPullRequestTemplateCache() {}
}
