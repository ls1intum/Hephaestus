package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub member webhook events (repository collaborator changes).
 */
@Component
public class GitHubMemberMessageHandler extends GitHubMessageHandler<GitHubMemberEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMemberMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubUserProcessor userProcessor;
    private final RepositoryCollaboratorRepository collaboratorRepository;

    GitHubMemberMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubUserProcessor userProcessor,
        RepositoryCollaboratorRepository collaboratorRepository,
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubMemberEventDTO.class, deserializer);
        this.contextFactory = contextFactory;
        this.userProcessor = userProcessor;
        this.collaboratorRepository = collaboratorRepository;
    }

    @Override
    protected String getEventKey() {
        return "member";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubMemberEventDTO event) {
        var memberDto = event.member();

        if (memberDto == null) {
            logger.warn("Received member event with missing data");
            return;
        }

        logger.info(
            "Received member event: action={}, member={}, repo={}",
            event.action(),
            memberDto.login(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure user exists via processor
        User user = userProcessor.ensureExists(memberDto);
        if (user == null) {
            logger.warn("Could not create or find user for member: {}", memberDto.login());
            return;
        }

        Repository repository = context.repository();

        switch (event.actionType()) {
            case GitHubEventAction.Member.ADDED -> handleCollaboratorAdded(repository, user, event);
            case GitHubEventAction.Member.REMOVED -> handleCollaboratorRemoved(repository, user);
            default -> logger.debug("Unhandled member action: {}", event.action());
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
            logger.info(
                "Updated collaborator permission for {} in {}: {}",
                user.getLogin(),
                repository.getNameWithOwner(),
                permission
            );
        } else {
            // Create new collaborator
            RepositoryCollaborator collaborator = new RepositoryCollaborator(repository, user, permission);
            collaboratorRepository.save(collaborator);
            logger.info(
                "Added collaborator {} to {} with permission {}",
                user.getLogin(),
                repository.getNameWithOwner(),
                permission
            );
        }
    }

    private void handleCollaboratorRemoved(Repository repository, User user) {
        var existingCollaborator = collaboratorRepository.findByRepositoryIdAndUserId(repository.getId(), user.getId());

        if (existingCollaborator.isPresent()) {
            collaboratorRepository.delete(existingCollaborator.get());
            logger.info("Removed collaborator {} from {}", user.getLogin(), repository.getNameWithOwner());
        } else {
            logger.debug(
                "Collaborator {} not found in {} - may have been already removed",
                user.getLogin(),
                repository.getNameWithOwner()
            );
        }
    }
}
