package de.tum.in.www1.hephaestus.gitprovider.organization.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener.MembershipChangedEvent;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMemberRole;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.dto.GitLabMemberEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitLab member webhook events for real-time group membership updates.
 * <p>
 * Processes {@code user_add_to_group}, {@code user_remove_from_group}, and
 * {@code user_update_for_group} events that are normalized to the "member"
 * event key by the webhook-ingest layer.
 * <p>
 * This closes the gap where membership changes between scheduled syncs were
 * invisible, allowing removed users to retain access until the next restart.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabMemberMessageHandler extends GitLabMessageHandler<GitLabMemberEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabMemberMessageHandler.class);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final GitProviderRepository gitProviderRepository;
    private final GitLabProperties gitLabProperties;

    @Nullable
    private final OrganizationMembershipListener membershipListener;

    private final TransactionTemplate requiresNewTransaction;

    GitLabMemberMessageHandler(
        OrganizationRepository organizationRepository,
        OrganizationMembershipRepository membershipRepository,
        UserRepository userRepository,
        GitProviderRepository gitProviderRepository,
        GitLabProperties gitLabProperties,
        @Nullable OrganizationMembershipListener membershipListener,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabMemberEventDTO.class, deserializer, transactionTemplate);
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.gitLabProperties = gitLabProperties;
        this.membershipListener = membershipListener;
        this.requiresNewTransaction = new TransactionTemplate(transactionTemplate.getTransactionManager());
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.MEMBER;
    }

    @Override
    protected void handleEvent(GitLabMemberEventDTO event) {
        String safeGroupPath = sanitizeForLog(event.groupPath());
        String safeUsername = sanitizeForLog(event.userUsername());

        log.info(
            "Received member event: eventName={}, groupPath={}, user={}, access={}",
            event.eventName(),
            safeGroupPath,
            safeUsername,
            event.groupAccess()
        );

        Long providerId = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, gitLabProperties.defaultServerUrl())
            .orElseThrow(() ->
                new IllegalStateException(
                    "GitProvider not found for type=GITLAB, serverUrl=" + gitLabProperties.defaultServerUrl()
                )
            )
            .getId();

        // Look up the organization by the group's native ID
        Organization org = organizationRepository.findByNativeIdAndProviderId(event.groupId(), providerId).orElse(null);

        if (org == null) {
            log.debug(
                "Organization not yet synced, skipping member event: groupId={}, groupPath={}",
                event.groupId(),
                safeGroupPath
            );
            return;
        }

        if (event.isAddition() || event.isUpdate()) {
            handleMemberAddOrUpdate(event, org, providerId);
        } else if (event.isRemoval()) {
            handleMemberRemoval(event, org, providerId);
        } else {
            log.debug("Unhandled member event action: eventName={}, groupPath={}", event.eventName(), safeGroupPath);
        }
    }

    private void handleMemberAddOrUpdate(GitLabMemberEventDTO event, Organization org, Long providerId) {
        // Upsert user in isolated transaction (same pattern as GitLabGroupMemberSyncService)
        long nativeUserId = event.userId();
        String login = event.userUsername();
        String name = event.userName();

        requiresNewTransaction.executeWithoutResult(status -> {
            boolean locked = userRepository.tryAcquireLoginLock(login, providerId);
            if (locked) {
                userRepository.freeLoginConflicts(login, nativeUserId, providerId);
            }
            String avatarUrl = event.userAvatar() != null ? event.userAvatar() : "";
            userRepository.upsertUser(
                nativeUserId,
                providerId,
                login,
                name,
                avatarUrl,
                "", // htmlUrl not available in member webhook payload
                User.Type.USER.name(),
                null,
                null,
                null
            );
        });

        User user = userRepository.findByNativeIdAndProviderId(nativeUserId, providerId).orElse(null);
        if (user == null) {
            log.warn("Failed to upsert user from member event: userId={}, login={}", nativeUserId, login);
            return;
        }

        OrganizationMemberRole role = mapGroupAccess(event.groupAccess());
        membershipRepository.upsertMembership(org.getId(), user.getId(), role);

        log.info(
            "Added/updated group member: orgId={}, orgLogin={}, userLogin={}, role={}",
            org.getId(),
            sanitizeForLog(org.getLogin()),
            sanitizeForLog(login),
            role
        );

        if (membershipListener != null) {
            membershipListener.onMemberAdded(
                new MembershipChangedEvent(org.getId(), org.getLogin(), user.getId(), login, event.groupAccess())
            );
        }
    }

    private void handleMemberRemoval(GitLabMemberEventDTO event, Organization org, Long providerId) {
        User user = userRepository.findByNativeIdAndProviderId(event.userId(), providerId).orElse(null);
        if (user == null) {
            log.debug(
                "User not found for member removal: userId={}, login={}",
                event.userId(),
                sanitizeForLog(event.userUsername())
            );
            return;
        }

        membershipRepository.deleteByOrganizationIdAndUserIdIn(org.getId(), List.of(user.getId()));

        log.info(
            "Removed group member: orgId={}, orgLogin={}, userLogin={}",
            org.getId(),
            sanitizeForLog(org.getLogin()),
            sanitizeForLog(event.userUsername())
        );

        if (membershipListener != null) {
            membershipListener.onMemberRemoved(
                new MembershipChangedEvent(org.getId(), org.getLogin(), user.getId(), event.userUsername(), null)
            );
        }
    }

    /**
     * Maps GitLab group_access string to OrganizationMemberRole.
     * Owner and Maintainer → ADMIN; all others → MEMBER.
     */
    private static OrganizationMemberRole mapGroupAccess(@Nullable String groupAccess) {
        if (groupAccess == null) {
            return OrganizationMemberRole.MEMBER;
        }
        return switch (groupAccess) {
            case "Owner", "Maintainer" -> OrganizationMemberRole.ADMIN;
            default -> OrganizationMemberRole.MEMBER;
        };
    }
}
