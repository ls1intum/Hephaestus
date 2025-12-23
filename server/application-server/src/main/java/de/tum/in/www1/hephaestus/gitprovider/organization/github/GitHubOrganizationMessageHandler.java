package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub organization webhook events.
 * <p>
 * Uses DTOs directly for complete field coverage.
 * Delegates all business logic to {@link GitHubOrganizationProcessor} and {@link GitHubUserProcessor}.
 */
@Component
public class GitHubOrganizationMessageHandler extends GitHubMessageHandler<GitHubOrganizationEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubOrganizationMessageHandler.class);

    private final GitHubOrganizationProcessor organizationProcessor;
    private final GitHubUserProcessor userProcessor;
    private final OrganizationMembershipRepository membershipRepository;

    GitHubOrganizationMessageHandler(
        GitHubOrganizationProcessor organizationProcessor,
        GitHubUserProcessor userProcessor,
        OrganizationMembershipRepository membershipRepository
    ) {
        super(GitHubOrganizationEventDTO.class);
        this.organizationProcessor = organizationProcessor;
        this.userProcessor = userProcessor;
        this.membershipRepository = membershipRepository;
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
                    User user = userProcessor.ensureExists(userDto);
                    String role = event.membership().role() != null
                        ? event.membership().role().toUpperCase()
                        : "MEMBER";
                    membershipRepository.upsertMembership(orgDto.id(), user.getId(), role);
                    logger.info("Member added to org {}: {} with role {}", orgDto.login(), userDto.login(), role);
                }
            }
            case "member_removed" -> {
                if (event.membership() != null && event.membership().user() != null) {
                    var userDto = event.membership().user();
                    membershipRepository.deleteByOrganizationIdAndUserIdIn(orgDto.id(), List.of(userDto.id()));
                    logger.info("Member removed from org {}: {}", orgDto.login(), userDto.login());
                }
            }
            case "renamed" -> organizationProcessor.rename(orgDto.id(), orgDto.login());
            case "deleted" -> organizationProcessor.delete(orgDto.id());
            default -> organizationProcessor.process(orgDto);
        }
    }
}
