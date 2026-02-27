package de.tum.in.www1.hephaestus.gitprovider.organization.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupResponse;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitLab groups, mapping them to {@link Organization} entities.
 * <p>
 * Uses the thread-safe PostgreSQL upsert pattern via {@link OrganizationRepository#upsert}
 * to handle concurrent webhook and sync events for the same group.
 * <p>
 * <b>Key mapping decisions:</b>
 * <ul>
 *   <li>{@code fullPath} → {@code login} (unique identifier, supports nested groups)</li>
 *   <li>Numeric ID extracted from GID → {@code id} and {@code githubId}</li>
 *   <li>{@code webUrl} → {@code htmlUrl}</li>
 * </ul>
 *
 * @implNote The {@code Organization} entity uses {@code id} and {@code githubId} as
 * provider-specific numeric IDs. GitLab group IDs and GitHub organization IDs are
 * independent sequences that may produce overlapping values. Concurrent use of both
 * providers in the same database requires a future schema migration to add a provider
 * discriminator column. Until then, deployments should use only one git provider.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabGroupProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitLabGroupProcessor.class);

    private final OrganizationRepository organizationRepository;

    public GitLabGroupProcessor(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /**
     * Processes a GitLab group GraphQL response into an Organization entity.
     * <p>
     * Uses upsert for thread-safe concurrent inserts. If the group already exists,
     * its mutable fields (name, avatar, URL) are updated.
     *
     * @param group the GitLab group GraphQL response
     * @return the persisted Organization entity, or null if the response is invalid
     */
    @Transactional
    @Nullable
    public Organization process(GitLabGroupResponse group) {
        if (group == null || group.id() == null || group.fullPath() == null || group.webUrl() == null) {
            log.warn("Skipped group processing: reason=nullOrMissingFields");
            return null;
        }

        long numericId;
        try {
            numericId = GitLabSyncConstants.extractNumericId(group.id());
        } catch (IllegalArgumentException e) {
            log.warn("Skipped group processing: reason=invalidGlobalId, gid={}", group.id());
            return null;
        }

        String login = group.fullPath();
        String name = group.name() != null ? group.name() : login;
        String avatarUrl = group.avatarUrl();
        String htmlUrl = group.webUrl();

        organizationRepository.upsert(numericId, numericId, login, name, avatarUrl, htmlUrl);
        Organization organization = organizationRepository.findById(numericId).orElse(null);
        if (organization != null) {
            Instant now = Instant.now();
            organization.setLastSyncAt(now);
            organization.setUpdatedAt(now);
            if (organization.getCreatedAt() == null) {
                organization.setCreatedAt(now);
            }
        }
        return organization;
    }
}
