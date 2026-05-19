package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaboratorRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubMemberEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub member webhook events (repository collaborator changes).
 */
@Component
public class GitHubMemberMessageHandler extends GitHubMessageHandler<GitHubMemberEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubMemberMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubUserProcessor userProcessor;
    private final RepositoryCollaboratorRepository collaboratorRepository;

    GitHubMemberMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubUserProcessor userProcessor,
        RepositoryCollaboratorRepository collaboratorRepository,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubMemberEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.userProcessor = userProcessor;
        this.collaboratorRepository = collaboratorRepository;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.MEMBER;
    }

    @Override
    protected void handleEvent(GitHubMemberEventDTO event) {
        var memberDto = event.member();

        if (memberDto == null) {
            log.warn("Received member event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received member event: action={}, userLogin={}, repoName={}",
            event.action(),
            sanitizeForLog(memberDto.login()),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure user exists via processor
        User user = userProcessor.ensureExists(memberDto, context.providerId());
        if (user == null) {
            log.warn("Skipped member event: reason=userNotFound, userLogin={}", sanitizeForLog(memberDto.login()));
            return;
        }

        Repository repository = context.repository();

        switch (event.actionType()) {
            case GitHubEventAction.Member.ADDED -> handleCollaboratorAdded(repository, user, event);
            case GitHubEventAction.Member.REMOVED -> handleCollaboratorRemoved(repository, user);
            default -> log.debug("Skipped member event: reason=unhandledAction, action={}", event.action());
        }
    }

    private void handleCollaboratorAdded(Repository repository, User user, GitHubMemberEventDTO event) {
        // Extract permission from event changes
        String permissionValue = event.getPermission();
        RepositoryCollaborator.Permission permission = RepositoryCollaborator.Permission.fromGitHubValue(
            permissionValue
        );

        // Check if collaborator already exists
        var existingCollaborator = collaboratorRepository.findByRepositoryIdAndUserId(repository.getId(), user.getId());

        if (existingCollaborator.isPresent()) {
            // Update permission if changed
            RepositoryCollaborator collaborator = existingCollaborator.get();
            collaborator.updatePermission(permission);
            collaboratorRepository.save(collaborator);
            log.info(
                "Updated collaborator permission: userLogin={}, repoName={}, permission={}",
                sanitizeForLog(user.getLogin()),
                sanitizeForLog(repository.getNameWithOwner()),
                permission
            );
        } else {
            // Create new collaborator
            RepositoryCollaborator collaborator = new RepositoryCollaborator(repository, user, permission);
            collaboratorRepository.save(collaborator);
            log.info(
                "Added collaborator: userLogin={}, repoName={}, permission={}",
                sanitizeForLog(user.getLogin()),
                sanitizeForLog(repository.getNameWithOwner()),
                permission
            );
        }
    }

    private void handleCollaboratorRemoved(Repository repository, User user) {
        var existingCollaborator = collaboratorRepository.findByRepositoryIdAndUserId(repository.getId(), user.getId());

        if (existingCollaborator.isPresent()) {
            collaboratorRepository.delete(existingCollaborator.get());
            log.info(
                "Removed collaborator: userLogin={}, repoName={}",
                sanitizeForLog(user.getLogin()),
                sanitizeForLog(repository.getNameWithOwner())
            );
        } else {
            log.debug(
                "Skipped collaborator removal: reason=notFound, userLogin={}, repoName={}",
                sanitizeForLog(user.getLogin()),
                sanitizeForLog(repository.getNameWithOwner())
            );
        }
    }
}
