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
 * Resolves an Outline document author {@code (server, teamId, user UUID)} to the workspace member the
 * projection attributes authorship to, through auth SPI ports only — never {@code core.auth} or SCM domain
 * types.
 *
 * <p>The registry canonicalizes the server URL to its origin (scheme://host[:port]) so a Connection's
 * {@code serverUrl} and a login provider's {@code base_url} resolve to the same provider row. {@code teamId}
 * (the Connection's {@code instance_key}) is part of the identity key so a shared self-hosted instance
 * cannot alias users across tenants.
 *
 * <p>Resolution is lazy (projection-time) and never stamped onto {@code outline_document}: documents are
 * mutable and re-synced, so a stamp would strand stale member ids when a user links later. It is
 * membership-gated — an author only resolves to a member linked within THAT workspace (cross-workspace
 * firewall).
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

    /** The workspace member id the author resolves to, or empty when it is not linked to a member of {@code workspaceId}. */
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
