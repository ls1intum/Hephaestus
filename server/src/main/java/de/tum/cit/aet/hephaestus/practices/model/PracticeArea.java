package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
    name = "practice_area",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_practice_area_workspace_slug",
        columnNames = { "workspace_id", "slug" }
    ),
    indexes = @Index(
        name = "idx_practice_area_workspace_dashboard_visibility",
        columnList = "workspace_id, visible_in_practice_dashboards"
    )
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PracticeArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Owning workspace (tenancy binding). {@code fk_practice_area_workspace} carries no DB cascade: a
     * workspace purge removes its areas explicitly in application code (mirrors {@code fk_practice_workspace}).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_practice_area_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    /** Stable machine key, unique per workspace; survives a {@link #name} rename. */
    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    /** Admin-renameable display label. */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** Optional blurb shown on the area card. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Optional lucide icon name (PascalCase, e.g. {@code "ShieldAlert"}) giving the area a glanceable
     * identity on the dashboards. The webapp resolves the name to a component and falls back gracefully
     * when unset or unknown, so this is presentation-only and never load-bearing.
     */
    @Column(name = "icon", length = 64)
    private String icon;

    /**
     * Optional colour key (a palette family, e.g. {@code "rose"}) for the area's chip. Paired with the
     * {@link #icon} and {@link #name} so colour is a redundant cue, never the only signal. The webapp
     * maps the key to accessible classes and falls back when unset.
     */
    @Column(name = "color", length = 32)
    private String color;

    @Column(name = "visible_in_practice_dashboards", nullable = false)
    private boolean visibleInPracticeDashboards = true;

    /**
     * How much autonomy the system has over every practice in this area that holds no opinion of its own, or
     * {@code null} to inherit the workspace's default.
     *
     * <p>The middle level of the chain, and the one that makes the whole thing worth having: an area is the
     * grain a team actually reasons in ("we are not ready for the system to comment on our test practices
     * yet"), so one decision here covers the practices under it without touching any of them. Resolve with
     * {@link de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver}.
     */
    @Column(name = "review_tier", length = PracticeReviewTier.MAX_LENGTH)
    @Enumerated(EnumType.STRING)
    @Nullable
    private PracticeReviewTier reviewTier;

    /** Admin dashboard ordering. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** Catalog slug retained across workspace edits. */
    @Column(name = "source_curated_slug", length = 64)
    private String sourceCuratedSlug;

    /** Catalog comparison fingerprint captured when the workspace copy is created. */
    @Column(name = "source_curated_fingerprint", length = 64)
    private String sourceCuratedFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
