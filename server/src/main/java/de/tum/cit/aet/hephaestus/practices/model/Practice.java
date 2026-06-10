package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * Workspace-scoped practice definition for evaluating developer contributions.
 *
 * <p>A practice represents a specific coding or process standard that can be
 * detected in pull requests, commits, or reviews. Each practice belongs to a
 * workspace and is identified by a unique slug within that workspace.
 *
 * <p>The {@link #triggerEvents} field (JSONB) specifies which domain events
 * should trigger detection for this practice (e.g., PullRequestCreated, ReviewSubmitted).
 *
 * @see PracticeFinding for detection results
 */
@Entity
@Table(
    name = "practice",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_practice_workspace_slug",
        columnNames = { "workspace_id", "slug" }
    ),
    indexes = @Index(name = "idx_practice_workspace_active", columnList = "workspace_id, is_active")
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Practice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_practice_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "category", length = 64)
    private String category;

    /**
     * Optional {@link PracticeGoal} this practice rolls up to (NULL = ungrouped). 1:N (one goal owns
     * many practices; a practice belongs to at most one goal): the single owning bucket keeps the
     * per-goal acted-on/total progress denominator unambiguous. Do not loosen to a join table without
     * also switching progress math to per-(goal, practice) rows. Orthogonal to {@link #category}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_goal_id", foreignKey = @ForeignKey(name = "fk_practice_goal"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @ToString.Exclude
    private PracticeGoal goal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_events", columnDefinition = "jsonb", nullable = false)
    private JsonNode triggerEvents;

    @Column(name = "criteria", columnDefinition = "TEXT", nullable = false)
    private String criteria;

    /**
     * Optional Bun/TypeScript static analysis script that runs before the AI agent.
     * Produces structured hints (not verdicts) that the agent uses as starting points.
     * When null, no precomputation runs for this practice.
     */
    @Column(name = "precompute_script", columnDefinition = "TEXT")
    private String precomputeScript;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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
