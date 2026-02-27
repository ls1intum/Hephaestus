package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto.GitLabPushEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitLab push webhook events.
 * <p>
 * On each push event, upserts the project as a {@link de.tum.in.www1.hephaestus.gitprovider.repository.Repository}
 * using the embedded project metadata from the webhook payload. This ensures the repository
 * entity exists before any commit processing (future scope).
 * <p>
 * Branch deletions are skipped. All other pushes (to any branch) trigger a project upsert
 * because the project metadata is branch-independent.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabPushMessageHandler extends GitLabMessageHandler<GitLabPushEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabPushMessageHandler.class);

    private final GitLabProjectProcessor projectProcessor;

    GitLabPushMessageHandler(
        GitLabProjectProcessor projectProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabPushEventDTO.class, deserializer, transactionTemplate);
        this.projectProcessor = projectProcessor;
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.PUSH;
    }

    @Override
    protected void handleEvent(GitLabPushEventDTO event) {
        if (event.project() == null) {
            log.warn("Received push event with missing project data");
            return;
        }

        String projectPath = event.project().pathWithNamespace();
        String safeProjectPath = sanitizeForLog(projectPath);

        if (event.isBranchDeletion()) {
            log.debug("Skipped push event: reason=branchDeletion, projectPath={}", safeProjectPath);
            return;
        }

        log.info(
            "Received push event: projectPath={}, ref={}, commits={}",
            safeProjectPath,
            event.ref(),
            event.totalCommitsCount()
        );

        // Upsert the project as a Repository entity from the webhook payload.
        // This ensures the repository exists for future commit/MR processing.
        var repository = projectProcessor.processPushEvent(event.project());

        if (repository != null) {
            log.debug(
                "Upserted project from push event: projectPath={}, repoId={}",
                safeProjectPath,
                repository.getId()
            );
        } else {
            log.warn("Failed to upsert project from push event: projectPath={}", safeProjectPath);
        }
    }
}
