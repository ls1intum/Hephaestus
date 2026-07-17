package de.tum.cit.aet.hephaestus.core.auth.jwt;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The session-scoped constraints baked into a minted access JWT — everything about the token that is
 * NOT the principal. One explicit contract for {@link HephaestusJwtIssuer#issue}, so a re-mint path
 * (refresh, impersonate begin/exit) cannot silently drop a constraint it was meant to carry forward.
 * Claim semantics: {@code docs/auth-glossary.md}.
 *
 * @param impersonatorId         sets the RFC 8693 {@code act} claim when non-null.
 * @param impersonationExpiresAt absolute impersonation ceiling ({@code imp_exp}).
 * @param sessionExpiresAt       absolute session ceiling ({@code session_exp}); carried across re-mints.
 * @param authTime               last INTERACTIVE authentication ({@code auth_time}); carried across
 *                               re-mints — a silent refresh is not an authentication event.
 */
public record TokenConstraints(
    @Nullable Long impersonatorId,
    @Nullable Instant impersonationExpiresAt,
    @Nullable Instant sessionExpiresAt,
    @Nullable Instant authTime
) {
    /** No constraints — a bare {@code iat + accessTtl} token. Test seam; no production path mints one. */
    public static TokenConstraints none() {
        return new TokenConstraints(null, null, null, null);
    }

    /** A login or non-impersonation re-mint under the given session ceiling + {@code auth_time}. */
    public static TokenConstraints session(@Nullable Instant sessionExpiresAt, @Nullable Instant authTime) {
        return new TokenConstraints(null, null, sessionExpiresAt, authTime);
    }

    /** An impersonation (re-)mint under the OPERATOR's carried ceiling + {@code auth_time}. */
    public static TokenConstraints impersonation(
        Long impersonatorId,
        Instant impersonationExpiresAt,
        @Nullable Instant sessionExpiresAt,
        @Nullable Instant authTime
    ) {
        return new TokenConstraints(impersonatorId, impersonationExpiresAt, sessionExpiresAt, authTime);
    }
}
