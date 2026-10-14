package de.tum.cit.aet.hephaestus.core.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * Minimal exact identity held separately from non-content erasure receipts to prevent reprocessing.
 */
@Entity
@Table(
        name = "identity_restriction",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_identity_restriction_key",
                        columnNames = {"provider_id", "provider_user_key", "team_key", "kind"}))
@Getter
@Setter
public class IdentityRestriction {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "active_request_id")
    private @Nullable UUID activeRequestId;

    @Column(name = "provider_id", nullable = false)
    private long providerId;

    @Column(name = "provider_user_key", nullable = false, length = 255)
    private String providerUserKey;

    @Column(name = "team_key", nullable = false, length = 255)
    private String teamKey = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    public enum Kind {
        ERASED,
        HIDE_PUBLIC
    }
}
