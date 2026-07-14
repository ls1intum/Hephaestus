package de.tum.cit.aet.hephaestus.integration.slack.mentor;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountWorkspaceMembershipQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a Slack sender {@code (team_id, user_id)} to the SCM developer login the mentor should draw its
 * practice history from.
 *
 * <p>The chain is deliberately SPI-only so this adapter never imports {@code core.auth} domain types:
 * <ol>
 *   <li>{@link GitProviderRegistry#resolveProviderId} → the {@code identity_provider} row id for SLACK,</li>
 *   <li>{@link AccountIdentityQuery#resolveAccountId} → the Hephaestus {@code Account} keyed by the nOAuth-safe
 *       {@code (SLACK, U…, team)} triple (Slack subjects are only unique within their workspace, so {@code teamId}
 *       is load-bearing here — a null team would alias every Slack user of a shared {@code U…} across tenants),</li>
 *   <li>the account's active identity links → their SCM logins, narrowed to the one that is actually a member of
 *       {@code workspaceId} via {@link AccountWorkspaceMembershipQuery}. The Slack link's own
 *       {@code usernameAtSignup} is a Slack display name and matches no SCM membership, so it drops out naturally.</li>
 * </ol>
 *
 * <p>Resolution is provider-scoped and membership-gated, never login-only: an account that links a GitHub and a
 * GitLab identity resolves to the login whose workspace it is being addressed in, so a Slack DM can only ever pull
 * up the developer's OWN practice history within that workspace.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackMentorIdentityResolver {

    /** The canonical Slack server URL the seeded {@code identity_provider} row is keyed by. */
    static final String SLACK_SERVER_URL = "https://slack.com";

    private final GitProviderRegistry gitProviderRegistry;
    private final AccountIdentityQuery accountIdentityQuery;
    private final AccountWorkspaceMembershipQuery workspaceMembershipQuery;
    private final UserRepository userRepository;

    public SlackMentorIdentityResolver(
        GitProviderRegistry gitProviderRegistry,
        AccountIdentityQuery accountIdentityQuery,
        AccountWorkspaceMembershipQuery workspaceMembershipQuery,
        UserRepository userRepository
    ) {
        this.gitProviderRegistry = gitProviderRegistry;
        this.accountIdentityQuery = accountIdentityQuery;
        this.workspaceMembershipQuery = workspaceMembershipQuery;
        this.userRepository = userRepository;
    }

    /**
     * @param workspaceId the workspace the DM is scoped to
     * @param teamId      the Slack {@code T…} workspace id (part of the identity key)
     * @param slackUserId the Slack {@code U…} sender id
     * @return the SCM login of the account's identity that belongs to {@code workspaceId}, or empty when the Slack
     *         user is not linked to an account with membership in that workspace
     */
    @Transactional(readOnly = true)
    public Optional<String> resolveDeveloperLogin(long workspaceId, @Nullable String teamId, String slackUserId) {
        if (slackUserId == null || slackUserId.isBlank()) {
            return Optional.empty();
        }
        long slackProviderId = gitProviderRegistry.resolveProviderId("SLACK", SLACK_SERVER_URL);
        return accountIdentityQuery
            .resolveAccountId(slackProviderId, slackUserId, teamId)
            .map(accountIdentityQuery::activeLinksForAccount)
            .orElseGet(List::of)
            .stream()
            .map(AccountIdentityQuery.IdentityLinkView::usernameAtSignup)
            .filter(login -> login != null && !login.isBlank())
            .distinct()
            .filter(login -> isWorkspaceMember(login, workspaceId))
            .findFirst();
    }

    /**
     * The workspace {@code User} (member) id the Slack sender resolves to — the firewall stamp written onto
     * {@code slack_message.author_member_id} and unioned into {@code slack_thread.participant_member_ids} by the
     * Ingest write-path. Resolves through the same provider-scoped, membership-gated chain as
     * {@link #resolveDeveloperLogin} and then maps the workspace-scoped login to its SCM {@code User} row, so the
     * stamped id is exactly the {@code MentorChatRequest.developerId()} the participant projector matches against
     * (a Slack user only ever stamps as the member they are linked to within this workspace).
     *
     * @return the SCM {@code User} id, or empty when the Slack user is not linked to a member of {@code workspaceId}
     */
    @Transactional(readOnly = true)
    public Optional<Long> resolveMemberId(long workspaceId, @Nullable String teamId, String slackUserId) {
        return resolveDeveloperLogin(workspaceId, teamId, slackUserId)
            .flatMap(userRepository::findByLogin)
            .map(User::getId);
    }

    /**
     * Reverse of {@link #resolveMemberId}: the Slack {@code U…} subject a workspace member can be DMed at. Walks
     * {@code memberId → account (via the wired actor mirror) → active SLACK link}, narrowed to {@code teamId} (the
     * ACTIVE connection's team) so a member with Slack identities in several teams is never addressed through the
     * wrong workspace's bot token.
     *
     * @return the Slack user id, or empty when the member never signed in or has no Slack link in that team
     */
    @Transactional(readOnly = true)
    public Optional<String> resolveSlackUserId(long memberId, @Nullable String teamId) {
        long slackProviderId = gitProviderRegistry.resolveProviderId("SLACK", SLACK_SERVER_URL);
        return accountIdentityQuery
            .resolveAccountIdForActor(memberId)
            .map(accountIdentityQuery::activeLinksForAccount)
            .orElseGet(List::of)
            .stream()
            .filter(link -> link.gitProviderId() != null && link.gitProviderId() == slackProviderId)
            .filter(link -> teamId == null || teamId.equals(link.teamId()))
            .map(AccountIdentityQuery.IdentityLinkView::subject)
            .filter(subject -> subject != null && !subject.isBlank())
            .findFirst();
    }

    private boolean isWorkspaceMember(String login, long workspaceId) {
        return workspaceMembershipQuery
            .membershipsForLogins(Set.of(login))
            .stream()
            .anyMatch(view -> view.workspaceId() != null && view.workspaceId() == workspaceId);
    }
}
