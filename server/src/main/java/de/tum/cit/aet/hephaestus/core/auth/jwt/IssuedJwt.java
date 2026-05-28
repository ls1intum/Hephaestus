package de.tum.cit.aet.hephaestus.core.auth.jwt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

/**
 * Revocation list for Hephaestus-issued cookie JWTs. Every JWT we mint has a {@code jti}
 * inserted here at issuance; the {@code RevocationAwareJwtDecoder} consults a Caffeine-cached
 * view of this table on every request to short-circuit revoked tokens.
 *
 * <p>Cache invalidation across pods is propagated over NATS on the {@code auth.jwt.revoked}
 * subject — Caffeine TTL is the safety net, not the primary correctness mechanism.
 *
 * <p>Expired rows are swept by a scheduled job; the {@code expires_at} index makes that cheap.
 */
@Entity
@Table(name = "issued_jwt")
@Getter
@Setter
@NoArgsConstructor
public class IssuedJwt {

    @Id
    @Column(name = "jti", columnDefinition = "uuid")
    private UUID jti;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    @Nullable
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 32)
    @Nullable
    private RevokedReason revokedReason;

    @Column(name = "user_agent", length = 512)
    @Nullable
    private String userAgent;

    @Column(name = "ip_inet", columnDefinition = "inet")
    @Nullable
    private String ipInet;

    public IssuedJwt(UUID jti, Long accountId, Instant expiresAt) {
        this.jti = jti;
        this.accountId = accountId;
        this.expiresAt = expiresAt;
    }

    public enum RevokedReason {
        LOGOUT,
        ROTATE,
        SIGN_OUT_EVERYWHERE,
        ADMIN_REVOKE,
        IMPERSONATION_EXIT,
        ACCOUNT_DELETED,
    }
}
