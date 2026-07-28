package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Narrow read access to the SCM actor mirror ({@code integration.scm.domain.user.User}) for {@code core.auth},
 * which owns accounts but not actors.
 *
 * <p>Which actor is this federated git identity ({@link #findExternalActorId}), and what is that actor
 * called ({@link #loginsByActorIds})? Implemented in {@code integration}, which owns the {@code User}
 * aggregate — the same dependency inversion as {@link GitProviderRegistry}.
 *
 * <h2>Binding an actor at signup</h2>
 * {@link #findExternalActorId} lets {@code core.auth} stamp {@code identity_link.external_actor_id} at
 * account creation rather than leaving it for the lazy bind.
 *
 * <p>Why eagerly. An unbound link resolves to its actor by {@code (provider, username_at_signup)}, which is a
 * snapshot: rename the account upstream and the fallback stops matching, so surfaces keyed on the actor — a
 * developer's own practice report, workspace membership — quietly stop finding them. The actor <em>id</em>
 * survives renames. Binding at signup is the cheapest moment to capture it, because the OAuth subject is in
 * hand and is the provider-native user id.
 *
 * <p><b>Best-effort, and asymmetric on purpose.</b> An empty result costs nothing: the existing lazy bind
 * ({@code IdentityLinkRepository#linkExternalActorIfAbsent}) fills the column later. A <em>wrong</em> bind
 * costs everything and never self-heals, because that lazy bind only fills NULLs — it would permanently
 * attach one person's account to another person's work. Implementations must return empty on any uncertainty.
 */
public interface ExternalActorQuery {
    /**
     * Resolve the SCM actor id for a git identity.
     *
     * @param gitProviderId the {@code git_provider} row id (from {@link GitProviderRegistry})
     * @param subject       the IdP-stable subject — the provider-native numeric user id for GitHub/GitLab
     * @param username      the login at signup, a fallback key when the subject is not numeric or no actor
     *                      carries that native id; may be {@code null}
     * @return the actor id, or empty when no actor matches unambiguously
     */
    Optional<Long> findExternalActorId(long gitProviderId, String subject, @Nullable String username);

    /**
     * The logins of the given SCM actors, for rendering an actor id into a name a person can read.
     *
     * <p>Used by the GDPR export to answer Art. 15(1)(c) for the disclosure trail, which stores only actor
     * ids. Returns a login and nothing else: the minimum that identifies a colleague, since every extra
     * field would be another person's data travelling in this subject's export. Unknown ids are absent from
     * the map — an actor can be erased while the record naming them survives.
     *
     * @param actorIds the {@code "user"} row ids to resolve; empty input yields an empty map
     * @return actor id → login, for the ids that resolve
     */
    Map<Long, String> loginsByActorIds(Collection<Long> actorIds);
}
