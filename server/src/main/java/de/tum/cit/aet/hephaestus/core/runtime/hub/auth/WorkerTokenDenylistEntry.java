package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "worker_token_denylist")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkerTokenDenylistEntry {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "jti", length = 64, nullable = false)
    private String jti;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    /** Original JWT {@code exp}. Rows with {@code expiresAt < now()} can be GC'd by a sweeper. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public WorkerTokenDenylistEntry(String jti, Instant revokedAt, Instant expiresAt) {
        this.jti = jti;
        this.revokedAt = revokedAt;
        this.expiresAt = expiresAt;
    }
}
