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

/**
 * Hephaestus's own JWT-signing key set. Backs Spring Security's {@code NimbusJwtEncoder}
 * through a custom {@code JWKSource<SecurityContext>} that reads this table.
 *
 * <h2>Key lifecycle</h2>
 * New JWTs sign with the most recently inserted {@code active=true} key; all active keys verify.
 * Zero-downtime rotation is not yet implemented (see {@code JwtSigningKeyService} / ADR 0017).
 *
 * <h2>Encryption envelope</h2>
 * {@code private_key_pem} is sealed using the <em>system</em> master key (AAD =
 * {@link JwtSigningKeySealer#AAD_STRING}). Distinct from the tenant-bound AAD
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

    @Column(name = "encryption_key_id", nullable = false, length = 64)
    private String encryptionKeyId;
}
