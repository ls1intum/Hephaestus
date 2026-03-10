package de.tum.in.www1.hephaestus.gitprovider.team.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabDescendantGroupResponse;
import de.tum.in.www1.hephaestus.gitprovider.team.gitlab.dto.GitLabSubgroupEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitLab subgroup webhook events for real-time team structure updates.
 * <p>
 * Processes {@code subgroup_create} and {@code subgroup_destroy} events that are
 * normalized to the "subgroup" event key by the webhook-ingest layer.
 * <p>
 * On creation, delegates to {@link GitLabTeamProcessor#process} to upsert a Team entity.
 * On deletion, delegates to {@link GitLabTeamProcessor#delete} to remove the team and
 * cascade-clear memberships and repo permissions.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabSubgroupMessageHandler extends GitLabMessageHandler<GitLabSubgroupEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabSubgroupMessageHandler.class);

    private final GitLabTeamProcessor teamProcessor;
    private final GitProviderRepository gitProviderRepository;
    private final GitLabProperties gitLabProperties;

    GitLabSubgroupMessageHandler(
        GitLabTeamProcessor teamProcessor,
        GitProviderRepository gitProviderRepository,
        GitLabProperties gitLabProperties,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabSubgroupEventDTO.class, deserializer, transactionTemplate);
        this.teamProcessor = teamProcessor;
        this.gitProviderRepository = gitProviderRepository;
        this.gitLabProperties = gitLabProperties;
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.SUBGROUP;
    }

    @Override
    protected void handleEvent(GitLabSubgroupEventDTO event) {
        String safeFullPath = sanitizeForLog(event.fullPath());
        log.info(
            "Received subgroup event: eventName={}, fullPath={}, groupId={}, parentGroupId={}",
            event.eventName(),
            safeFullPath,
            event.groupId(),
            event.parentGroupId()
        );

        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, gitLabProperties.defaultServerUrl())
            .orElse(null);

        if (provider == null) {
            log.warn("GitProvider not found for GITLAB, skipping subgroup event");
            return;
        }

        if (event.isCreation()) {
            handleSubgroupCreate(event, provider);
        } else if (event.isDeletion()) {
            handleSubgroupDestroy(event, provider);
        } else {
            log.debug("Unhandled subgroup event action: eventName={}", event.eventName());
        }
    }

    private void handleSubgroupCreate(GitLabSubgroupEventDTO event, GitProvider provider) {
        // Determine root full path from parent hierarchy.
        // The workspace's accountLogin is the root group. We derive it from the
        // parent_full_path by taking the top-level segment, but since we don't know
        // the exact workspace root, we pass parentFullPath as rootFullPath.
        // GitLabTeamProcessor.computeRelativePath() handles the slug computation.
        String rootFullPath = event.parentFullPath() != null ? event.parentFullPath() : "";

        // Build a GitLabDescendantGroupResponse from the webhook DTO
        // to reuse the existing processor logic.
        GitLabDescendantGroupResponse groupResponse = new GitLabDescendantGroupResponse(
            "gid://gitlab/Group/" + event.groupId(),
            event.fullPath(),
            event.name(),
            null, // description not in webhook payload
            null, // webUrl not in webhook payload
            null, // visibility not in webhook payload
            event.parentFullPath() != null
                ? new GitLabDescendantGroupResponse.ParentRef(
                      "gid://gitlab/Group/" + event.parentGroupId(),
                      event.parentFullPath()
                  )
                : null
        );

        teamProcessor.process(groupResponse, rootFullPath, provider);
        log.info(
            "Created/updated team from subgroup event: fullPath={}, groupId={}",
            sanitizeForLog(event.fullPath()),
            event.groupId()
        );
    }

    private void handleSubgroupDestroy(GitLabSubgroupEventDTO event, GitProvider provider) {
        teamProcessor.delete(event.groupId(), provider.getId());
        log.info(
            "Deleted team from subgroup event: fullPath={}, groupId={}",
            sanitizeForLog(event.fullPath()),
            event.groupId()
        );
    }
}
