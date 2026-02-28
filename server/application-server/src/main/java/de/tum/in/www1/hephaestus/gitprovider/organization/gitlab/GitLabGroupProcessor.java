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
 *   <li>Numeric ID extracted from GID, negated via {@link GitLabSyncConstants#toEntityId} → {@code id}</li>
 *   <li>{@code webUrl} → {@code htmlUrl}</li>
 *   <li>{@code provider} = {@link GitProviderType#GITLAB}</li>
 * </ul>
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
     * its mutable fields (name, avatar, URL) are updated. IDs are negated to avoid
     * collision with GitHub organization IDs.
     *
     * @param group the GitLab group GraphQL response
     * @return the persisted Organization entity, or null if the response is invalid
     */
    @Transactional
    @Nullable
    public Organization process(GitLabGroupResponse group, Long providerId) {
        if (group == null || group.id() == null || group.fullPath() == null || group.webUrl() == null) {
            log.warn("Skipped group processing: reason=nullOrMissingFields");
            return null;
        }

        long nativeId;
        try {
            nativeId = GitLabSyncConstants.extractNumericId(group.id());
        } catch (IllegalArgumentException e) {
            log.warn("Skipped group processing: reason=invalidGlobalId, gid={}", group.id());
            return null;
        }

        String login = group.fullPath();
        String name = group.name() != null ? group.name() : login;
        String avatarUrl = group.avatarUrl();
        String htmlUrl = group.webUrl();

        organizationRepository.upsert(
            nativeId,
            providerId,
            login,
            name,
            avatarUrl,
            htmlUrl
        );
        Organization organization = organizationRepository.findByNativeIdAndProviderId(nativeId, providerId).orElse(null);
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
