package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module read query: does the account behind a git-provider {@code login} hold a given
 * role / feature flag? Exposed so callers (e.g. the practices {@code UserRoleChecker} in the
 * notification module) can check roles without reaching into {@code core.auth}'s domain types.
 *
 * <p>Roles map 1:1 to {@code FeatureFlag} keys ({@code mentor_access}, {@code run_practice_review},
 * {@code notification_access}, {@code admin}). Resolution is {@code login → IdentityLink →
 * Account → account_feature} (appRole-based admin is resolved at JWT-mint time in
 * {@code JwtPrincipalFactory}, not by this query).
 */
public interface AccountRoleQuery {
    /**
     * @param login the git-provider login (case-insensitive)
     * @param flag  the role / feature-flag key
     * @return true if any active account for that login holds the flag; false otherwise (incl. errors)
     */
    boolean hasFeatureFlag(String login, String flag);
}
