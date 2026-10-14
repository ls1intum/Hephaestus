package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The JWT subject identifies the account. Provider identities come from its verified links, not
 * from display names or upstream claims copied into the session token.
 */
public interface AccountIdentityQuery {
    /** Enabled federated identities, oldest link first; empty for a missing or unlinked account. */
    List<IdentityView> activeLinksForAccount(Long accountId);

    Map<Long, List<IdentityView>> activeLinksForAccounts(Collection<Long> accountIds);

    /**
     * Resolves an enabled link by immutable provider, subject and team. Account status is not
     * checked, so this also supports historical attribution; use {@link #resolveActiveAccountId}
     * for live access. The team is part of the identity key for Slack/Outline and null for
     * GitHub/GitLab.
     */
    Optional<Long> resolveAccountId(Long providerId, String subject, @Nullable String teamId);

    /**
     * Like {@link #resolveAccountId}, but requires an active account for admission to a new
     * interaction.
     */
    Optional<Long> resolveActiveAccountId(Long providerId, String subject, @Nullable String teamId);

    record IdentityView(Long identityId, Long providerId, String providerUserKey, String teamKey) {}
}
