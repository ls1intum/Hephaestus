package de.tum.in.www1.hephaestus.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Tracks workspace slug rename history for redirect support.
 * Enables temporary redirects from old slugs to new slugs with configurable TTL.
 */
@Entity
@Table(name = "workspace_slug_history")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class WorkspaceSlugHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "workspace_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_workspace_slug_history_workspace")
    )
    @NotNull(message = "Workspace is required")
    @ToString.Exclude
    private Workspace workspace;

    @Column(name = "old_slug", nullable = false, length = 255)
    @NotBlank(message = "Old slug is required")
    private String oldSlug;

    @Column(name = "new_slug", nullable = false, length = 255)
    @NotBlank(message = "New slug is required")
    private String newSlug;

    @Column(name = "changed_at", nullable = false)
    @NotNull(message = "Changed at timestamp is required")
    private Instant changedAt;

    @Column(name = "redirect_expires_at")
    private Instant redirectExpiresAt;

    /**
     * Check if this redirect is still valid (not expired).
     *
     * @return true if redirect_expires_at is null or in the future
     */
    public boolean isRedirectValid() {
        return redirectExpiresAt == null || redirectExpiresAt.isAfter(Instant.now());
    }

    /**
     * Check if this redirect has expired.
     *
     * @return true if redirect_expires_at is set and in the past
     */
    public boolean isRedirectExpired() {
        return redirectExpiresAt != null && redirectExpiresAt.isBefore(Instant.now());
    }
}
