package de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.dto.GitLabMergeRequestEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitLab merge request webhook events.
 * <p>
 * Routes to {@link GitLabMergeRequestProcessor} based on the action:
 * <ul>
 *   <li>{@code open} / {@code update} → {@link GitLabMergeRequestProcessor#process}</li>
 *   <li>{@code close} → {@link GitLabMergeRequestProcessor#processClosed}</li>
 *   <li>{@code reopen} → {@link GitLabMergeRequestProcessor#processReopened}</li>
 *   <li>{@code merge} → {@link GitLabMergeRequestProcessor#processMerged}</li>
 *   <li>{@code approved} → {@link GitLabMergeRequestProcessor#processApproved}</li>
 *   <li>{@code unapproved} → {@link GitLabMergeRequestProcessor#processUnapproved}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabMergeRequestMessageHandler extends GitLabMessageHandler<GitLabMergeRequestEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabMergeRequestMessageHandler.class);

    private final GitLabMergeRequestProcessor mergeRequestProcessor;
    private final RepositoryRepository repositoryRepository;
    private final RepositoryScopeFilter repositoryScopeFilter;
    private final ScopeIdResolver scopeIdResolver;

    GitLabMergeRequestMessageHandler(
        GitLabMergeRequestProcessor mergeRequestProcessor,
        RepositoryRepository repositoryRepository,
        RepositoryScopeFilter repositoryScopeFilter,
        ScopeIdResolver scopeIdResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabMergeRequestEventDTO.class, deserializer, transactionTemplate);
        this.mergeRequestProcessor = mergeRequestProcessor;
        this.repositoryRepository = repositoryRepository;
        this.repositoryScopeFilter = repositoryScopeFilter;
        this.scopeIdResolver = scopeIdResolver;
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.MERGE_REQUEST;
    }

    @Override
    protected void handleEvent(GitLabMergeRequestEventDTO event) {
        if (event.objectAttributes() == null) {
            log.warn("Received merge request event with missing object_attributes");
            return;
        }

        if (event.project() == null) {
            log.warn("Received merge request event with missing project data");
            return;
        }

        if (event.isConfidential()) {
            log.debug("Skipped confidential merge request event: iid={}", event.objectAttributes().iid());
            return;
        }

        String projectPath = event.project().pathWithNamespace();
        String safeProjectPath = sanitizeForLog(projectPath);
        GitLabEventAction action = event.actionType();

        log.info(
            "Processing merge request event: projectPath={}, iid={}, action={}",
            safeProjectPath,
            event.objectAttributes().iid(),
            action
        );

        ProcessingContext context = resolveContext(projectPath, action.getValue());
        if (context == null) {
            return;
        }

        switch (action) {
            case OPEN, UPDATE -> mergeRequestProcessor.process(event, context);
            case CLOSE -> mergeRequestProcessor.processClosed(event, context);
            case REOPEN -> mergeRequestProcessor.processReopened(event, context);
            case MERGE -> mergeRequestProcessor.processMerged(event, context);
            case APPROVED -> mergeRequestProcessor.processApproved(event, context);
            case UNAPPROVED -> mergeRequestProcessor.processUnapproved(event, context);
            case APPROVAL, UNAPPROVAL -> log.debug(
                "Skipped group-level approval rule event: projectPath={}, action={}",
                safeProjectPath,
                action
            );
            default -> log.debug("Unhandled merge request action: projectPath={}, action={}", safeProjectPath, action);
        }
    }

    private ProcessingContext resolveContext(String pathWithNamespace, String action) {
        String safePath = sanitizeForLog(pathWithNamespace);

        if (!repositoryScopeFilter.isRepositoryAllowed(pathWithNamespace)) {
            log.debug("Skipped merge request event: reason=repositoryFiltered, repoName={}", safePath);
            return null;
        }

        Repository repository = repositoryRepository
            .findByNameWithOwnerWithOrganization(pathWithNamespace)
            .orElse(null);

        if (repository == null) {
            log.debug("Skipped merge request event: reason=repositoryNotFound, repoName={}", safePath);
            return null;
        }

        Long scopeId = resolveScopeId(repository);
        return ProcessingContext.forWebhook(scopeId, repository, action);
    }

    private Long resolveScopeId(Repository repository) {
        if (repository.getOrganization() != null) {
            String orgLogin = repository.getOrganization().getLogin();
            Long scopeId = scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null);
            if (scopeId != null) {
                return scopeId;
            }
        }
        return scopeIdResolver.findScopeIdByRepositoryName(repository.getNameWithOwner()).orElse(null);
    }
}
