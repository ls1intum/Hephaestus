package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
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
 */
@Component
public class GitHubOrganizationMessageHandler extends GitHubMessageHandler<GitHubOrganizationEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubOrganizationMessageHandler.class);

    private final GitHubOrganizationProcessor organizationProcessor;
    private final GitHubUserProcessor userProcessor;
    private final OrganizationMembershipRepository membershipRepository;

    GitHubOrganizationMessageHandler(
        GitHubOrganizationProcessor organizationProcessor,
        GitHubUserProcessor userProcessor,
        OrganizationMembershipRepository membershipRepository,
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubOrganizationEventDTO.class, deserializer);
        this.organizationProcessor = organizationProcessor;
        this.userProcessor = userProcessor;
        this.membershipRepository = membershipRepository;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.ORGANIZATION;
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
            log.warn("Received organization event with missing data");
            return;
        }

        log.info("Received organization event: action={}, org={}", event.action(), orgDto.login());

        switch (event.actionType()) {
            case GitHubEventAction.Organization.MEMBER_ADDED -> {
                if (event.membership() != null && event.membership().user() != null) {
                    var userDto = event.membership().user();
                    User user = userProcessor.ensureExists(userDto);
                    String role = event.membership().role() != null
                        ? event.membership().role().toUpperCase()
                        : "MEMBER";
                    membershipRepository.upsertMembership(orgDto.id(), user.getId(), role);
                    log.info("Member added to org {}: {} with role {}", orgDto.login(), userDto.login(), role);
                }
            }
            case GitHubEventAction.Organization.MEMBER_REMOVED -> {
                if (event.membership() != null && event.membership().user() != null) {
                    var userDto = event.membership().user();
                    membershipRepository.deleteByOrganizationIdAndUserIdIn(orgDto.id(), List.of(userDto.id()));
                    log.info("Member removed from org {}: {}", orgDto.login(), userDto.login());
                }
            }
            case GitHubEventAction.Organization.RENAMED -> organizationProcessor.rename(orgDto.id(), orgDto.login());
            case GitHubEventAction.Organization.DELETED -> organizationProcessor.delete(orgDto.id());
            default -> organizationProcessor.process(orgDto);
        }
    }
}
