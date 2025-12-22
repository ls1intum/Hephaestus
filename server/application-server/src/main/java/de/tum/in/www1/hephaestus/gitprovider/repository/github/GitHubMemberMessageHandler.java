package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubMemberEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub member webhook events (repository collaborator changes).
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 */
@Component
public class GitHubMemberMessageHandler extends GitHubMessageHandler<GitHubMemberEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMemberMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final UserRepository userRepository;

    GitHubMemberMessageHandler(ProcessingContextFactory contextFactory, UserRepository userRepository) {
        super(GitHubMemberEventDTO.class);
        this.contextFactory = contextFactory;
        this.userRepository = userRepository;
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

        // Ensure user exists
        if (!userRepository.existsById(memberDto.id())) {
            User user = new User();
            user.setId(memberDto.id());
            user.setLogin(memberDto.login());
            user.setAvatarUrl(memberDto.avatarUrl());
            // Use login as fallback for name if null (name is @NonNull)
            user.setName(memberDto.name() != null ? memberDto.name() : memberDto.login());
            userRepository.save(user);
        }

        // Log the action - actual collaborator tracking can be added later
        switch (event.action()) {
            case "added" -> logger.info(
                "Collaborator added to {}: {}",
                context.repository().getNameWithOwner(),
                memberDto.login()
            );
            case "removed" -> logger.info(
                "Collaborator removed from {}: {}",
                context.repository().getNameWithOwner(),
                memberDto.login()
            );
            default -> logger.debug("Unhandled member action: {}", event.action());
        }
    }
}
