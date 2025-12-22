package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub organization webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 */
@Component
public class GitHubOrganizationMessageHandler extends GitHubMessageHandler<GitHubOrganizationEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubOrganizationMessageHandler.class);

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    GitHubOrganizationMessageHandler(OrganizationRepository organizationRepository, UserRepository userRepository) {
        super(GitHubOrganizationEventDTO.class);
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected String getEventKey() {
        return "organization";
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.ORGANIZATION;
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubOrganizationEventDTO event) {
        var orgDto = event.organization();

        if (orgDto == null) {
            logger.warn("Received organization event with missing data");
            return;
        }

        logger.info("Received organization event: action={}, org={}", event.action(), orgDto.login());

        switch (event.action()) {
            case "member_added" -> {
                if (event.membership() != null && event.membership().user() != null) {
                    var userDto = event.membership().user();
                    // Ensure user exists
                    if (!userRepository.existsById(userDto.id())) {
                        User user = new User();
                        user.setId(userDto.id());
                        user.setLogin(userDto.login());
                        user.setAvatarUrl(userDto.avatarUrl());
                        // Use login as fallback for name if null (name is @NonNull)
                        user.setName(userDto.name() != null ? userDto.name() : userDto.login());
                        userRepository.save(user);
                    }
                    logger.info("Member added to org {}: {}", orgDto.login(), userDto.login());
                }
            }
            case "member_removed" -> {
                if (event.membership() != null && event.membership().user() != null) {
                    logger.info("Member removed from org {}: {}", orgDto.login(), event.membership().user().login());
                }
            }
            case "renamed" -> {
                organizationRepository
                    .findById(orgDto.id())
                    .ifPresent(existing -> {
                        existing.setLogin(orgDto.login());
                        organizationRepository.save(existing);
                    });
            }
            case "deleted" -> {
                organizationRepository.deleteById(orgDto.id());
            }
            default -> {
                // For other events, update/create the organization
                processOrganization(orgDto);
            }
        }
    }

    private void processOrganization(GitHubOrganizationEventDTO.GitHubOrganizationDTO dto) {
        organizationRepository
            .findById(dto.id())
            .ifPresentOrElse(
                org -> {
                    if (dto.login() != null) org.setLogin(dto.login());
                    if (dto.avatarUrl() != null) org.setAvatarUrl(dto.avatarUrl());
                    organizationRepository.save(org);
                },
                () -> {
                    Organization org = new Organization();
                    org.setId(dto.id());
                    org.setLogin(dto.login());
                    org.setAvatarUrl(dto.avatarUrl());
                    organizationRepository.save(org);
                }
            );
    }
}
