package de.tum.cit.aet.hephaestus.integration.outline.identity;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountWorkspaceMembershipQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountWorkspaceMembershipQuery.WorkspaceMembershipView;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an Outline document author {@code (server, team_id, user UUID)} to the workspace member the
 * projection should attribute authorship to.
 *
 * <p>The chain is deliberately SPI-only so this adapter never imports {@code core.auth} domain types —
 * or the SCM schema:
 * <ol>
 *   <li>{@link GitProviderRegistry#resolveProviderId} → the {@code identity_provider} row id for
 *       {@code (OUTLINE, serverUrl)}. The registry canonicalizes the URL to its origin
 *       (scheme://host[:port]), so the Connection's {@code serverUrl} and a login provider's
 *       {@code base_url} land on the SAME provider row even when one carries a path or trailing slash —
 *       the two write paths cannot silently split providers,</li>
 *   <li>{@link AccountIdentityQuery#resolveAccountId} → the Hephaestus {@code Account} keyed by the
 *       nOAuth-safe {@code (OUTLINE, uuid, team)} triple (an Outline user UUID is scoped to its team;
 *       {@code teamId} — the Connection's {@code instance_key} — keeps a shared self-hosted instance from
 *       aliasing users across tenants),</li>
 *   <li>the account's active identity links → their SCM logins, narrowed to the one that is actually a
 *       member of {@code workspaceId} via {@link AccountWorkspaceMembershipQuery} — whose membership view
 *       carries the member's SCM {@code User} id, so no SCM repository read is needed here. The Outline
 *       link's own {@code usernameAtSignup} is an Outline display name and matches no SCM membership, so
 *       it drops out naturally.</li>
 * </ol>
 *
 * <p>Resolution is lazy (projection-time, never stamped onto {@code outline_document} — documents are
 * mutable and re-synced, so a stamp would strand stale member ids when a user links later) and
 * membership-gated: an author only ever resolves to the member they are linked to within THAT workspace.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineIdentityResolver {

    private final GitProviderRegistry gitProviderRegistry;
    private final AccountIdentityQuery accountIdentityQuery;
    private final AccountWorkspaceMembershipQuery workspaceMembershipQuery;

    public OutlineIdentityResolver(
        GitProviderRegistry gitProviderRegistry,
        AccountIdentityQuery accountIdentityQuery,
        AccountWorkspaceMembershipQuery workspaceMembershipQuery
    ) {
        this.gitProviderRegistry = gitProviderRegistry;
        this.accountIdentityQuery = accountIdentityQuery;
        this.workspaceMembershipQuery = workspaceMembershipQuery;
    }

    /**
     * The SCM developer login the Outline author resolves to within {@code workspaceId}, or empty when
     * the author is not linked to an account with membership in that workspace.
     *
     * @param workspaceId    the workspace the projection is scoped to
     * @param serverUrl      the Outline instance URL (from the ACTIVE connection's config; canonicalized
     *                       to its origin by the registry)
     * @param teamId         the Outline team UUID (= the Connection's {@code instance_key}; part of the
     *                       identity key)
     * @param outlineSubject the Outline user UUID captured on the document row
     */
    @Transactional(readOnly = true)
    public Optional<String> resolveDeveloperLogin(
        long workspaceId,
        String serverUrl,
        @Nullable String teamId,
        String outlineSubject
    ) {
        return linkedLogins(workspaceId, serverUrl, teamId, outlineSubject)
            .filter(login -> membershipIn(login, workspaceId).isPresent())
            .findFirst();
    }

    /**
     * The workspace {@code User} (member) id the Outline author resolves to — what the document
     * projection exposes so the mentor/review context can attribute a mirrored document to a developer.
     * Resolves through the same provider-scoped, membership-gated chain as
     * {@link #resolveDeveloperLogin}; the member id comes straight off the membership view.
     *
     * @return the SCM {@code User} id, or empty when the author is not linked to a member of {@code workspaceId}
     */
    @Transactional(readOnly = true)
    public Optional<Long> resolveMemberId(
        long workspaceId,
        String serverUrl,
        @Nullable String teamId,
        String outlineSubject
    ) {
        return linkedLogins(workspaceId, serverUrl, teamId, outlineSubject)
            .map(login -> membershipIn(login, workspaceId))
            .flatMap(Optional::stream)
            .map(WorkspaceMembershipView::memberId)
            .filter(Objects::nonNull)
            .findFirst();
    }

    /** The distinct non-blank logins of the account the {@code (provider, subject, team)} triple resolves to. */
    private Stream<String> linkedLogins(
        long workspaceId,
        String serverUrl,
        @Nullable String teamId,
        String outlineSubject
    ) {
        if (outlineSubject == null || outlineSubject.isBlank() || serverUrl == null || serverUrl.isBlank()) {
            return Stream.empty();
        }
        long outlineProviderId = gitProviderRegistry.resolveProviderId("OUTLINE", serverUrl);
        return accountIdentityQuery
            .resolveAccountId(outlineProviderId, outlineSubject, teamId)
            .map(accountIdentityQuery::activeLinksForAccount)
            .orElseGet(List::of)
            .stream()
            .map(AccountIdentityQuery.IdentityLinkView::usernameAtSignup)
            .filter(login -> login != null && !login.isBlank())
            .distinct();
    }

    /** The login's membership row in {@code workspaceId}, if any — the cross-workspace firewall. */
    private Optional<WorkspaceMembershipView> membershipIn(String login, long workspaceId) {
        return workspaceMembershipQuery
            .membershipsForLogins(Set.of(login))
            .stream()
            .filter(view -> view.workspaceId() != null && view.workspaceId() == workspaceId)
            .findFirst();
    }
}
