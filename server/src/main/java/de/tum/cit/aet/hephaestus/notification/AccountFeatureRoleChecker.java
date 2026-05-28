package de.tum.cit.aet.hephaestus.notification;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRoleQuery;
import de.tum.cit.aet.hephaestus.practices.spi.UserRoleChecker;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * DB-backed {@link UserRoleChecker} — the post-Keycloak replacement for
 * {@code KeycloakUserRoleChecker}.
 *
 * <p>Delegates to {@link AccountRoleQuery} (the {@code core.auth} SPI) so role membership is
 * resolved from {@code account_feature} without this adapter reaching into the auth module's
 * domain types. Always {@link #isHealthy() healthy} — it is a local DB lookup, no external
 * dependency to trip a breaker; per the SPI contract, failures fail-closed inside the query.
 */
@Component
@WorkspaceAgnostic("Role checks are user-scoped (login → account → account_feature)")
public class AccountFeatureRoleChecker implements UserRoleChecker {

    private static final Logger log = LoggerFactory.getLogger(AccountFeatureRoleChecker.class);

    private final AccountRoleQuery accountRoleQuery;

    public AccountFeatureRoleChecker(AccountRoleQuery accountRoleQuery) {
        this.accountRoleQuery = accountRoleQuery;
    }

    @Override
    public boolean hasRole(@NonNull String username, @NonNull String roleName) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(roleName, "roleName must not be null");
        if (roleName.isBlank()) {
            throw new IllegalArgumentException("roleName must not be blank");
        }
        return accountRoleQuery.hasFeatureFlag(username, roleName);
    }

    @Override
    public boolean isHealthy() {
        return true; // local DB lookup; no external dependency to gate on
    }
}
