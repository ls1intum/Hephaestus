package de.tum.cit.aet.hephaestus.core.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.NamedInterface;

/**
 * One human principal across workspaces. Roster identities can own an unclaimed account. A verified
 * sign-in claims its exact provider key. Email is contact data, never ownership proof.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    // Contact email is never an authentication lookup key.
    @Column(name = "primary_email", columnDefinition = "citext")
    @Nullable
    private String primaryEmail;

    @Column(name = "primary_email_verified_at")
    @Nullable
    private Instant primaryEmailVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "app_role", nullable = false, length = 16)
    @ColumnDefault("'USER'")
    private AppRole appRole = AppRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @ColumnDefault("'ACTIVE'")
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    /**
     * Set when the account enters {@link Status#DELETING} (start of the GDPR-cooldown window,
     * {@code hephaestus.auth.delete-cooldown}, default 48h). Once it is older than the cooldown,
     * {@code AccountHardDeleteSweeper} purges the account's personal/auth child rows and flips the
     * row to {@link Status#DELETED}.
     */
    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;

    @Column(name = "public_activity_visible", nullable = false)
    @ColumnDefault("true")
    private boolean publicActivityVisible = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_choice", length = 24)
    private @Nullable AiChoice aiChoice;

    @Column(name = "ai_choice_at")
    private @Nullable Instant aiChoiceAt;

    @Column(name = "feedback_delivery_enabled", nullable = false)
    @ColumnDefault("true")
    private boolean feedbackDeliveryEnabled = true;

    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private @Nullable Long version;

    public Account(String displayName) {
        this.displayName = displayName;
    }

    public enum AppRole {
        USER,
        APP_ADMIN,
    }

    @NamedInterface("auth-types")
    public enum Status {
        UNCLAIMED,
        ACTIVE,
        SUSPENDED,
        /**
         * Soft-deleted: sessions revoked and login blocked immediately; the row is purged after the
         * 48h cooldown ({@code AccountHardDeleteSweeper}). There is no self-service recovery path
         * today.
         */
        DELETING,
        DELETED,
    }
}
