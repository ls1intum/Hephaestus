package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Cross-module read/link surface over an {@code Account}'s federated identities.
 *
 * <p>The Hephaestus-native cookie-JWT carries only {@code sub = Account.id} — it does <em>not</em>
 * carry the upstream provider's numeric user id (there is no {@code gitlab_id} / {@code github_id}
 * claim; see {@code HephaestusJwtIssuer}). So the SCM-side actor mirror ({@code integration.scm.domain.user.User})
 * must be provisioned from the account's {@link IdentityLinkView}s, not from JWT claims. The
 * authoritative identity chain is {@code sub → Account → active IdentityLink(s)}.
 *
 * <p>Implemented in {@code core.auth} (which owns the {@code IdentityLink} aggregate); consumed by
 * {@code integration} so the SCM module never imports {@code core.auth} domain types — same
 * dependency-inversion shape as {@link GitProviderRegistry} / {@link AccountRoleQuery}. Each view
 * exposes the scalar {@code gitProviderId} (FK into the integration-owned {@code git_provider} row),
 * so the consumer resolves the provider <em>type</em>/<em>server URL</em> through its own
 * repository — keeping vendor knowledge out of {@code core.auth}.
 */
public interface AccountIdentityQuery {
    /**
     * The account's active (non-disabled) federated identities, in no guaranteed order.
     *
     * @param accountId the Hephaestus-native account id (the JWT {@code sub})
     * @return one view per active {@code IdentityLink}; empty if the account has none / does not exist
     */
    List<IdentityLinkView> activeLinksForAccount(Long accountId);

    /**
     * Resolve the {@code Account} id backing an active federated identity, keyed by the immutable
     * {@code (provider, subject, team)} triple — the same nOAuth-safe lookup used at login. Lets a
     * non-auth module (e.g. the Slack DM mentor) turn a provider-native subject into a Hephaestus
     * principal without importing the {@code IdentityLink} domain entity.
     *
     * @param providerId the {@code identity_provider} row id (from the consumer's own registry)
     * @param subject    the IdP-stable subject (Slack {@code U…}, GitHub/GitLab numeric id)
     * @param teamId     the multi-instance tenant id (Slack {@code T…}); {@code null} for single-tenant IdPs
     * @return the account id of the active link, or empty when no active link matches
     */
    Optional<Long> resolveAccountId(Long providerId, String subject, @Nullable String teamId);

    /**
     * Reverse of {@link #linkExternalActor}: the {@code Account} owning an active identity link already wired
     * to {@code externalActorId} (the SCM actor mirror / {@code User} id). Lets an integration adapter start
     * from a workspace member and reach the account's OTHER identities via {@link #activeLinksForAccount}
     * (e.g. the Slack subject for a proactive DM) without importing {@code core.auth} domain types. Only links
     * provisioned through a login carry the wiring, so a member who never signed in resolves to empty.
     * Deterministic when multiple active links match (lowest link id wins).
     *
     * @param externalActorId the {@code User} (actor mirror) row id
     * @return the owning account id, or empty when no active link is wired to that actor
     */
    Optional<Long> resolveAccountIdForActor(Long externalActorId);

    /**
     * Point an {@code IdentityLink} at its git-provider actor mirror. Idempotent: a no-op when the
     * link already references {@code externalActorId} (or the link no longer exists). Closes the
     * {@code IdentityLink → ExternalActor} gap so profile surfaces can render "your activity" without
     * a {@code (provider, subject) → (provider_id, native_id)} join.
     *
     * @param identityLinkId  the {@code IdentityLink} row id (from a prior {@link IdentityLinkView})
     * @param externalActorId the {@code User} (actor mirror) row id to associate
     */
    void linkExternalActor(Long identityLinkId, Long externalActorId);

    /**
     * Vendor-neutral projection of an {@code IdentityLink}. Carries the scalar {@code gitProviderId}
     * (not a {@code IdentityProvider} entity) so {@code core.auth} stays free of integration imports.
     *
     * @param identityLinkId  the link row id (needed to call {@link #linkExternalActor})
     * @param gitProviderId   FK into the integration-owned {@code identity_provider} row
     * @param subject         the IdP-stable numeric provider user id (GitLab / GitHub numeric id)
     * @param usernameAtSignup the provider login captured at link time (maps to {@code User.login})
     * @param displayName     the provider display name at link time
     * @param avatarUrl       the provider avatar URL at link time
     * @param profileUrl      the provider profile/web URL at link time
     * @param externalActorId the already-linked actor-mirror id, or {@code null} if not yet wired
     * @param teamId          the multi-instance tenant id (Slack workspace id), or {@code null}
     */
    record IdentityLinkView(
        Long identityLinkId,
        Long gitProviderId,
        String subject,
        @Nullable String usernameAtSignup,
        @Nullable String displayName,
        @Nullable String avatarUrl,
        @Nullable String profileUrl,
        @Nullable Long externalActorId,
        @Nullable String teamId
    ) {}
}
