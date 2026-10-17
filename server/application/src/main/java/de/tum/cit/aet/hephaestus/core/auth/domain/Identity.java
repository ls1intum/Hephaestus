package de.tum.cit.aet.hephaestus.core.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

/** An exact provider identity. Display data belongs to the provider mirror. */
@Entity
@Table(name = "identity")
@Getter
@Setter
@NoArgsConstructor
public class Identity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "sfk_identity_account"))
    private Account account;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "provider_user_key", nullable = false, length = 255)
    private String providerUserKey;

    @Column(name = "team_key", nullable = false, length = 255)
    @ColumnDefault("''")
    private String teamKey = "";

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "linked_via", nullable = false, length = 16)
    private LinkedVia linkedVia = LinkedVia.SIGN_IN;

    @Column(name = "last_login_at")
    private @Nullable Instant lastLoginAt;

    @Column(name = "disabled_at")
    private @Nullable Instant disabledAt;

    public enum LinkedVia {
        ROSTER,
        SIGN_IN,
        MANUAL,
    }
}
