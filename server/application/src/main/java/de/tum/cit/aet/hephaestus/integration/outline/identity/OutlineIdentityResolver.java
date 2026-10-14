package de.tum.cit.aet.hephaestus.integration.outline.identity;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountMembershipQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves authors at projection time so later identity linking is reflected without rewriting documents.
 * Server, team and subject identify the provider account; workspace membership bounds attribution.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineIdentityResolver {

    private final GitProviderRegistry gitProviderRegistry;
    private final AccountIdentityQuery accountIdentityQuery;
    private final AccountMembershipQuery workspaceMembershipQuery;

    public OutlineIdentityResolver(
            GitProviderRegistry gitProviderRegistry,
            AccountIdentityQuery accountIdentityQuery,
            AccountMembershipQuery workspaceMembershipQuery) {
        this.gitProviderRegistry = gitProviderRegistry;
        this.accountIdentityQuery = accountIdentityQuery;
        this.workspaceMembershipQuery = workspaceMembershipQuery;
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolveAccountId(
            long workspaceId, String serverUrl, @Nullable String teamId, String outlineSubject) {
        if (outlineSubject.isBlank() || serverUrl.isBlank()) {
            return Optional.empty();
        }
        return gitProviderRegistry
                .findProviderId("OUTLINE", serverUrl)
                .flatMap(outlineProviderId ->
                        accountIdentityQuery.resolveAccountId(outlineProviderId, outlineSubject, teamId))
                .filter(accountId -> workspaceMembershipQuery.membershipsForAccount(accountId).stream()
                        .anyMatch(view -> view.workspaceId() == workspaceId));
    }
}
