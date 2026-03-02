package de.tum.in.www1.hephaestus.gitprovider.issue.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.dto.GitLabIssueEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitLab issue webhook events.
 * <p>
 * Processes both {@code event_type: "issue"} and {@code event_type: "confidential_issue"} payloads.
 * Both arrive on the same NATS subject ({@code object_kind: "issue"}).
 * <p>
 * Confidential issues are skipped entirely — they are never stored in the database.
 * <p>
 * Routes to {@link GitLabIssueProcessor} based on the action:
 * <ul>
 *   <li>{@code open} / {@code update} → {@link GitLabIssueProcessor#process}</li>
 *   <li>{@code close} → {@link GitLabIssueProcessor#processClosed}</li>
 *   <li>{@code reopen} → {@link GitLabIssueProcessor#processReopened}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabIssueMessageHandler extends GitLabMessageHandler<GitLabIssueEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabIssueMessageHandler.class);

    private final GitLabIssueProcessor issueProcessor;
    private final RepositoryRepository repositoryRepository;
    private final RepositoryScopeFilter repositoryScopeFilter;
    private final ScopeIdResolver scopeIdResolver;

    GitLabIssueMessageHandler(
        GitLabIssueProcessor issueProcessor,
        RepositoryRepository repositoryRepository,
        RepositoryScopeFilter repositoryScopeFilter,
        ScopeIdResolver scopeIdResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabIssueEventDTO.class, deserializer, transactionTemplate);
        this.issueProcessor = issueProcessor;
        this.repositoryRepository = repositoryRepository;
        this.repositoryScopeFilter = repositoryScopeFilter;
        this.scopeIdResolver = scopeIdResolver;
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.ISSUE;
    }

    @Override
    protected void handleEvent(GitLabIssueEventDTO event) {
        if (event.objectAttributes() == null) {
            log.warn("Received issue event with missing object_attributes");
            return;
        }

        if (event.project() == null) {
            log.warn("Received issue event with missing project data");
            return;
        }

        // Skip confidential issues entirely
        if (event.isConfidential()) {
            log.debug("Skipped confidential issue event: iid={}", event.objectAttributes().iid());
            return;
        }

        String projectPath = event.project().pathWithNamespace();
        String safeProjectPath = sanitizeForLog(projectPath);
        GitLabEventAction action = event.actionType();

        log.info(
            "Processing issue event: projectPath={}, iid={}, action={}",
            safeProjectPath,
            event.objectAttributes().iid(),
            action
        );

        ProcessingContext context = resolveContext(projectPath, action.getValue());
        if (context == null) {
            return;
        }

        switch (action) {
            case OPEN, UPDATE -> issueProcessor.process(event, context);
            case CLOSE -> issueProcessor.processClosed(event, context);
            case REOPEN -> issueProcessor.processReopened(event, context);
            default -> log.debug("Unhandled issue action: projectPath={}, action={}", safeProjectPath, action);
        }
    }

    private ProcessingContext resolveContext(String pathWithNamespace, String action) {
        String safePath = sanitizeForLog(pathWithNamespace);

        if (!repositoryScopeFilter.isRepositoryAllowed(pathWithNamespace)) {
            log.debug("Skipped issue event: reason=repositoryFiltered, repoName={}", safePath);
            return null;
        }

        Repository repository = repositoryRepository
            .findByNameWithOwnerWithOrganization(pathWithNamespace)
            .orElse(null);

        if (repository == null) {
            log.debug("Skipped issue event: reason=repositoryNotFound, repoName={}", safePath);
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
