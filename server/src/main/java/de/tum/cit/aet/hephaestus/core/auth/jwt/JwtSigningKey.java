package de.tum.cit.aet.hephaestus.core.auth.jwt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

/**
 * Hephaestus's own JWT-signing key set. Backs Spring Security's {@code NimbusJwtEncoder}
 * through a custom {@code JWKSource<SecurityContext>} that reads this table.
 *
 * <h2>Rotation</h2>
 * Two active keys at a time. New JWTs sign with the most recently inserted active key;
 * verification accepts any key with {@code active=true} OR {@code rotated_at} within the
 * max-TTL window. Pods refresh their in-memory copy on the {@code auth.signing-key.rotated}
 * NATS subject.
 *
 * <h2>Encryption envelope</h2>
 * {@code private_key_pem} is sealed using the <em>system</em> master key (AAD =
 * {@code "system:jwt_signing_key:private_key_pem"}). Distinct from the tenant-bound AAD
 * domain used by {@link de.tum.cit.aet.hephaestus.integration.core.connection.CredentialBundleConverter}
 * for per-workspace integration secrets — confused-deputy defense.
 */
@Entity
@Table(name = "jwt_signing_key")
@Getter
@Setter
@NoArgsConstructor
public class JwtSigningKey {

    @Id
    @Column(name = "kid", length = 64)
    private String kid;

    @Column(name = "algorithm", nullable = false, length = 16)
    @ColumnDefault("'ES256'")
    private String algorithm = "ES256";

    @Column(name = "public_key_pem", nullable = false, columnDefinition = "text")
    private String publicKeyPem;

    @Column(name = "private_key_pem", nullable = false)
    private byte[] privateKeyPem;

    @Column(name = "active", nullable = false)
    @ColumnDefault("true")
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set when a newer key supersedes this one; verification still accepts it during the rollover window. */
    @Column(name = "rotated_at")
    @Nullable
    private Instant rotatedAt;

    @Column(name = "encryption_key_id", nullable = false, length = 64)
    private String encryptionKeyId;
}
