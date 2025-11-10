package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationSyncService;
import java.net.URL;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayloadOrganization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub Organization events.
 */
@Component
public class GitHubOrganizationMessageHandler extends GitHubMessageHandler<GHEventPayloadOrganization> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubOrganizationMessageHandler.class);

    private final OrganizationService organizationService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationSyncService organizationSyncService;

    public GitHubOrganizationMessageHandler(
        OrganizationService organizationService,
        OrganizationRepository organizationRepository,
        OrganizationSyncService organizationSyncService
    ) {
        super(GHEventPayloadOrganization.class);
        this.organizationService = organizationService;
        this.organizationRepository = organizationRepository;
        this.organizationSyncService = organizationSyncService;
    }

    // Intentionally no @Override: GHEventPayloadOrganization originates from hub4j fork without bridged signature.
    protected void handleEvent(GHEventPayloadOrganization payload) {
        String action = payload.getAction() == null ? "" : payload.getAction();
        if (payload.getOrganization() == null) {
            logger.warn("organization event missing organization body (action={})", action);
            return;
        }

        Organization organization = upsertOrganization(payload);

        switch (action) {
            case "member_added":
                handleMemberAdded(payload, organization);
                break;
            case "member_removed":
                handleMemberRemoved(payload, organization);
                break;
            case "member_invited":
                logger.debug("Ignoring organization member_invited event for orgId={}", organization.getGithubId());
                break;
            case "renamed":
                handleRename(payload, organization);
                break;
            default:
                logger.debug("Unhandled organization action={} orgId={} (no-op)", action, organization.getGithubId());
                break;
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.ORGANIZATION;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.ORGANIZATION;
    }

    private Organization upsertOrganization(GHEventPayloadOrganization payload) {
        long githubId = payload.getOrganization().getId();
        String login = payload.getOrganization().getLogin();
        Organization organization = organizationService.upsertIdentity(githubId, login);

        String avatarUrl = payload.getOrganization().getAvatarUrl();
        URL htmlUrl = payload.getOrganization().getHtmlUrl();

        if (avatarUrl != null) {
            if (!avatarUrl.equals(organization.getAvatarUrl())) {
                organization.setAvatarUrl(avatarUrl);
            }
        }
        if (htmlUrl != null) {
            String html = htmlUrl.toString();
            if (!html.equals(organization.getHtmlUrl())) {
                organization.setHtmlUrl(html);
            }
        }

        return organizationRepository.save(organization);
    }

    private void handleMemberAdded(GHEventPayloadOrganization payload, Organization organization) {
        var membership = payload.getMembership();
        if (membership == null || membership.getUser() == null) {
            logger.warn("member_added without membership/user orgId={}", organization.getGithubId());
            return;
        }

        String role = organizationSyncService.upsertMemberFromEvent(
            organization.getGithubId(),
            membership.getUser(),
            membership.getRole()
        );
        logger.info(
            "Added organization member orgId={} userId={} role={}",
            organization.getGithubId(),
            membership.getUser().getId(),
            role
        );
    }

    private void handleMemberRemoved(GHEventPayloadOrganization payload, Organization organization) {
        var membership = payload.getMembership();
        if (membership == null || membership.getUser() == null) {
            logger.warn("member_removed without membership/user orgId={}", organization.getGithubId());
            return;
        }

        long userId = membership.getUser().getId();
        organizationSyncService.removeMember(organization.getGithubId(), userId);
        logger.info(
            "Removed organization member orgId={} userId={} state={} role={}",
            organization.getGithubId(),
            userId,
            membership.getState(),
            membership.getRole()
        );
    }

    private void handleRename(GHEventPayloadOrganization payload, Organization organization) {
        var changes = payload.getChanges();
        if (changes == null || changes.getLogin() == null) {
            return;
        }

        String newLogin = payload.getOrganization().getLogin();
        String oldLogin = changes.getLogin().getFrom();

        if (newLogin != null && !newLogin.equalsIgnoreCase(organization.getLogin())) {
            organization.setLogin(newLogin);
            organizationRepository.save(organization);
        }

        logger.info("Renamed organization githubId={} from={} to={}", organization.getGithubId(), oldLogin, newLogin);
    }
}
