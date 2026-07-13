package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Narrow SPI that resolves the SCM-side actor mirror ({@code integration.scm.domain.user.User}) for a
 * federated git identity, so {@code core.auth} can stamp {@code identity_link.external_actor_id} at
 * JIT account creation when the actor is already synced. Without the eager bind, a freshly created
 * account carries a NULL {@code external_actor_id} until the lazy provisioning path runs, and
 * account-to-actor resolution (e.g. the developer's own practice report) cannot find the caller.
 *
 * <p>Implemented in {@code integration} (which owns the {@code User} aggregate); consumed by
 * {@code core.auth} — the same dependency-inversion shape as {@link GitProviderRegistry}. Resolution is
 * best-effort: an empty result simply leaves the link unbound for the existing lazy bind
 * ({@code IdentityLinkRepository#linkExternalActorIfAbsent}) to fill later. That lazy bind fills only
 * NULL columns, so a WRONG eager bind never self-heals — implementations must prefer returning empty
 * over any uncertain match.
 */
public interface ExternalActorQuery {
    /**
     * Resolve the SCM actor ({@code "user"} row) id for a git identity.
     *
     * @param gitProviderId the {@code git_provider} row id (from {@link GitProviderRegistry})
     * @param subject       the IdP-stable subject — the provider-native numeric user id for GitHub/GitLab
     * @param username      the login at signup, used as a fallback lookup key when the subject is not
     *                      numeric or no actor carries that native id; may be {@code null}
     * @return the actor id, or empty when no synced actor matches
     */
    Optional<Long> findExternalActorId(long gitProviderId, String subject, @Nullable String username);
}
