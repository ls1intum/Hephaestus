package de.tum.cit.aet.hephaestus.integration.slack.preferences;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountWorkspaceMembershipQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackParticipantConsentRepository;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackParticipantConsentService;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackPersonErasureService;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspaceSummaryQuery;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackUserPreferencesService {

    static final String SLACK_SERVER_URL = "https://slack.com";

    private final AccountIdentityQuery accountIdentityQuery;
    private final AccountWorkspaceMembershipQuery membershipQuery;
    private final GitProviderRegistry gitProviderRegistry;
    private final ConnectionRepository connectionRepository;
    private final SlackParticipantConsentRepository participantConsentRepository;
    private final SlackMonitoredChannelRepository monitoredChannelRepository;
    private final SlackParticipantConsentService participantConsentService;
    private final SlackPersonErasureService erasureService;
    private final SlackMentorIdentityResolver identityResolver;
    private final WorkspaceSummaryQuery workspaceSummaryQuery;

    public SlackUserPreferencesService(
        AccountIdentityQuery accountIdentityQuery,
        AccountWorkspaceMembershipQuery membershipQuery,
        GitProviderRegistry gitProviderRegistry,
        ConnectionRepository connectionRepository,
        SlackParticipantConsentRepository participantConsentRepository,
        SlackMonitoredChannelRepository monitoredChannelRepository,
        SlackParticipantConsentService participantConsentService,
        SlackPersonErasureService erasureService,
        SlackMentorIdentityResolver identityResolver,
        WorkspaceSummaryQuery workspaceSummaryQuery
    ) {
        this.accountIdentityQuery = accountIdentityQuery;
        this.membershipQuery = membershipQuery;
        this.gitProviderRegistry = gitProviderRegistry;
        this.connectionRepository = connectionRepository;
        this.participantConsentRepository = participantConsentRepository;
        this.monitoredChannelRepository = monitoredChannelRepository;
        this.participantConsentService = participantConsentService;
        this.erasureService = erasureService;
        this.identityResolver = identityResolver;
        this.workspaceSummaryQuery = workspaceSummaryQuery;
    }

    @Transactional(readOnly = true)
    public SlackUserPreferencesDTO listForAccount(long accountId) {
        long slackProviderId = slackProviderId();
        List<AccountIdentityQuery.IdentityLinkView> links = accountIdentityQuery.activeLinksForAccount(accountId);
        Map<String, AccountIdentityQuery.IdentityLinkView> slackLinksByTeam = slackLinksByTeam(links, slackProviderId);
        if (slackLinksByTeam.isEmpty()) {
            return new SlackUserPreferencesDTO(List.of());
        }

        Set<Long> workspaceIds = accessibleWorkspaceIds(links, slackProviderId);
        if (workspaceIds.isEmpty()) {
            return new SlackUserPreferencesDTO(List.of());
        }

        List<SlackWorkspacePreferencesDTO> workspaces = connectionRepository
            .findAllByKindAndInstanceKeyInAndState(
                IntegrationKind.SLACK,
                slackLinksByTeam.keySet(),
                IntegrationState.ACTIVE
            )
            .stream()
            .filter(connection -> hasText(connection.getInstanceKey()))
            .filter(connection -> slackLinksByTeam.containsKey(connection.getInstanceKey()))
            .filter(connection -> workspaceIds.contains(connection.toRef().workspaceId()))
            .flatMap(connection -> toDto(connection, slackLinksByTeam.get(connection.getInstanceKey()), null).stream())
            .sorted(Comparator.comparing(SlackWorkspacePreferencesDTO::workspaceName))
            .toList();
        return new SlackUserPreferencesDTO(workspaces);
    }

    @Transactional
    public SlackWorkspacePreferencesDTO updateChannelMessagesAllowed(
        long workspaceId,
        long accountId,
        boolean channelMessagesAllowed
    ) {
        Connection connection = connectionRepository
            .findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
                workspaceId,
                IntegrationKind.SLACK,
                IntegrationState.ACTIVE
            )
            .orElseThrow(() -> notFound("Slack is not connected for this workspace."));
        String teamId = connection.getInstanceKey();
        if (!hasText(teamId)) {
            throw notFound("Slack is not connected for this workspace.");
        }

        AccountIdentityQuery.IdentityLinkView slackLink = accountIdentityQuery
            .activeLinksForAccount(accountId)
            .stream()
            .filter(link -> Objects.equals(link.gitProviderId(), slackProviderId()))
            .filter(link -> Objects.equals(link.teamId(), teamId))
            .filter(link -> hasText(link.subject()))
            .findFirst()
            .orElseThrow(() -> notFound("Slack account is not linked for this workspace."));

        if (channelMessagesAllowed) {
            participantConsentService.recordChannelMessageOptIn(workspaceId, slackLink.subject());
        } else {
            participantConsentService.recordChannelMessageOptOut(workspaceId, slackLink.subject());
            Long memberId = identityResolver.resolveMemberId(workspaceId, teamId, slackLink.subject()).orElse(null);
            erasureService.erasePerson(workspaceId, memberId, slackLink.subject());
        }
        return toDto(connection, slackLink, channelMessagesAllowed).orElseThrow(() ->
            notFound("Workspace is not available.")
        );
    }

    private Map<String, AccountIdentityQuery.IdentityLinkView> slackLinksByTeam(
        List<AccountIdentityQuery.IdentityLinkView> links,
        long slackProviderId
    ) {
        return links
            .stream()
            .filter(link -> Objects.equals(link.gitProviderId(), slackProviderId))
            .filter(link -> hasText(link.teamId()))
            .collect(
                Collectors.toMap(
                    AccountIdentityQuery.IdentityLinkView::teamId,
                    Function.identity(),
                    (first, ignored) -> first,
                    LinkedHashMap::new
                )
            );
    }

    private Set<Long> accessibleWorkspaceIds(List<AccountIdentityQuery.IdentityLinkView> links, long slackProviderId) {
        Set<String> logins = links
            .stream()
            .filter(link -> !Objects.equals(link.gitProviderId(), slackProviderId))
            .map(AccountIdentityQuery.IdentityLinkView::usernameAtSignup)
            .filter(SlackUserPreferencesService::hasText)
            .collect(Collectors.toSet());
        if (logins.isEmpty()) {
            return Set.of();
        }
        return membershipQuery
            .membershipsForLogins(logins)
            .stream()
            .map(AccountWorkspaceMembershipQuery.WorkspaceMembershipView::workspaceId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private Optional<SlackWorkspacePreferencesDTO> toDto(
        Connection connection,
        AccountIdentityQuery.IdentityLinkView slackLink,
        Boolean channelMessagesAllowed
    ) {
        long workspaceId = connection.toRef().workspaceId();
        return workspaceSummaryQuery
            .findById(workspaceId)
            .map(workspace -> toDto(connection, slackLink, channelMessagesAllowed, workspace));
    }

    private SlackWorkspacePreferencesDTO toDto(
        Connection connection,
        AccountIdentityQuery.IdentityLinkView slackLink,
        Boolean channelMessagesAllowed,
        WorkspaceSummaryQuery.WorkspaceSummary workspace
    ) {
        long workspaceId = workspace.id();
        String slackUserId = slackLink.subject();
        boolean allowed =
            channelMessagesAllowed != null
                ? channelMessagesAllowed
                : !participantConsentRepository.existsByWorkspaceIdAndSlackUserIdAndIngestionOptedOutTrue(
                      workspaceId,
                      slackUserId
                  );
        return new SlackWorkspacePreferencesDTO(
            workspace.slug(),
            workspace.displayName(),
            connection.getInstanceKey(),
            connection.getDisplayName(),
            slackUserId,
            slackLink.displayName() != null ? slackLink.displayName() : slackLink.usernameAtSignup(),
            allowed,
            monitoredChannelRepository.countByWorkspaceIdAndConsentState(workspaceId, ConsentState.ACTIVE)
        );
    }

    private long slackProviderId() {
        return gitProviderRegistry.resolveProviderId("SLACK", SLACK_SERVER_URL);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
