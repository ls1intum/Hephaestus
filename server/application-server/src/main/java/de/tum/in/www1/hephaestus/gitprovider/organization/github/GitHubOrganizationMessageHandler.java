package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener.MembershipChangedEvent;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMemberRole;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub organization webhook events.
 */
@Component
public class GitHubOrganizationMessageHandler extends GitHubMessageHandler<GitHubOrganizationEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubOrganizationMessageHandler.class);

    private final GitHubOrganizationProcessor organizationProcessor;
    private final GitHubUserProcessor userProcessor;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationMembershipListener membershipListener;

    GitHubOrganizationMessageHandler(
        GitHubOrganizationProcessor organizationProcessor,
        GitHubUserProcessor userProcessor,
        OrganizationMembershipRepository membershipRepository,
        OrganizationMembershipListener membershipListener,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubOrganizationEventDTO.class, deserializer, transactionTemplate);
        this.organizationProcessor = organizationProcessor;
        this.userProcessor = userProcessor;
        this.membershipRepository = membershipRepository;
        this.membershipListener = membershipListener;
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
    protected void handleEvent(GitHubOrganizationEventDTO event) {
        var orgDto = event.organization();

        if (orgDto == null) {
            log.warn("Received organization event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received organization event: action={}, orgId={}, orgLogin={}",
            event.action(),
            orgDto.id(),
            sanitizeForLog(orgDto.login())
        );

        switch (event.actionType()) {
            case GitHubEventAction.Organization.MEMBER_ADDED -> {
                if (event.membership() != null && event.membership().user() != null) {
                    var userDto = event.membership().user();
                    User user = userProcessor.ensureExists(userDto);
                    OrganizationMemberRole role = parseRole(event.membership().role());
                    membershipRepository.upsertMembership(orgDto.id(), user.getId(), role);
                    log.info(
                        "Added member to organization: orgId={}, orgLogin={}, userLogin={}, role={}",
                        orgDto.id(),
                        sanitizeForLog(orgDto.login()),
                        sanitizeForLog(userDto.login()),
                        role
                    );

                    // Notify listeners about membership change
                    membershipListener.onMemberAdded(
                        new MembershipChangedEvent(
                            orgDto.id(),
                            orgDto.login(),
                            userDto.id(),
                            userDto.login(),
                            event.membership().role()
                        )
                    );
                }
            }
            case GitHubEventAction.Organization.MEMBER_REMOVED -> {
                if (event.membership() != null && event.membership().user() != null) {
                    var userDto = event.membership().user();
                    membershipRepository.deleteByOrganizationIdAndUserIdIn(orgDto.id(), List.of(userDto.id()));
                    log.info(
                        "Removed member from organization: orgId={}, orgLogin={}, userLogin={}",
                        orgDto.id(),
                        sanitizeForLog(orgDto.login()),
                        sanitizeForLog(userDto.login())
                    );

                    // Notify listeners about membership change
                    membershipListener.onMemberRemoved(
                        new MembershipChangedEvent(orgDto.id(), orgDto.login(), userDto.id(), userDto.login(), null)
                    );
                }
            }
            case GitHubEventAction.Organization.RENAMED -> organizationProcessor.rename(orgDto.id(), orgDto.login());
            case GitHubEventAction.Organization.DELETED -> organizationProcessor.delete(orgDto.id());
            default -> organizationProcessor.process(orgDto);
        }
    }

    /**
     * Parses a role string from webhook events to the OrganizationMemberRole enum.
     *
     * @param roleString the role string from the webhook (e.g., "admin", "member")
     * @return the corresponding OrganizationMemberRole, defaulting to MEMBER if unknown
     */
    private OrganizationMemberRole parseRole(String roleString) {
        if (roleString == null) {
            return OrganizationMemberRole.MEMBER;
        }
        return switch (roleString.toUpperCase()) {
            case "ADMIN" -> OrganizationMemberRole.ADMIN;
            default -> OrganizationMemberRole.MEMBER;
        };
    }
}
