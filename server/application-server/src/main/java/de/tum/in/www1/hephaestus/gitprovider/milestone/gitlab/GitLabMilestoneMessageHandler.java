package de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookContextResolver;
import de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.dto.GitLabMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.dto.GitLabMilestoneEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitLab milestone webhook events.
 * <p>
 * Routes to {@link GitLabMilestoneProcessor} based on the action:
 * <ul>
 *   <li>{@code create} / {@code update} / {@code close} / {@code reopen} &rarr;
 *       {@link GitLabMilestoneProcessor#process} (state is encoded in the DTO)</li>
 * </ul>
 * <p>
 * GitLab does not send a webhook when a milestone is deleted — stale milestones
 * are cleaned up by the GraphQL sync service's stale removal logic.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabMilestoneMessageHandler extends GitLabMessageHandler<GitLabMilestoneEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabMilestoneMessageHandler.class);

    private final GitLabMilestoneProcessor milestoneProcessor;
    private final GitLabWebhookContextResolver contextResolver;

    GitLabMilestoneMessageHandler(
        GitLabMilestoneProcessor milestoneProcessor,
        GitLabWebhookContextResolver contextResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabMilestoneEventDTO.class, deserializer, transactionTemplate);
        this.milestoneProcessor = milestoneProcessor;
        this.contextResolver = contextResolver;
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.MILESTONE;
    }

    @Override
    protected void handleEvent(GitLabMilestoneEventDTO event) {
        if (event.objectAttributes() == null) {
            log.warn("Received milestone event with missing object_attributes");
            return;
        }

        if (event.project() == null) {
            log.warn("Received milestone event with missing project data");
            return;
        }

        String projectPath = event.project().pathWithNamespace();
        if (projectPath == null || projectPath.isBlank()) {
            log.warn("Received milestone event with missing project path");
            return;
        }
        String safeProjectPath = sanitizeForLog(projectPath);
        GitLabEventAction action = event.actionType();

        log.info(
            "Processing milestone event: projectPath={}, iid={}, action={}",
            safeProjectPath,
            event.objectAttributes().iid(),
            action
        );

        ProcessingContext context = contextResolver.resolve(projectPath, action.getValue(), "milestone");
        if (context == null) {
            return;
        }

        GitLabMilestoneDTO dto = GitLabMilestoneDTO.fromWebhookEvent(event);
        if (dto == null) {
            log.warn("Failed to create milestone DTO from webhook event: projectPath={}", safeProjectPath);
            return;
        }

        milestoneProcessor.process(dto, context.repository(), context);
    }
}
