package de.tum.cit.aet.hephaestus.practices.observation.reaction;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of a developer's response to delivered {@link Feedback}. The newest row is the current
 * response; a row with neither optional dimension is a deletion marker. The legacy table name is retained for
 * migration compatibility.
 */
@Entity
@Immutable
@Table(
    name = "reaction",
    indexes = {
        @Index(name = "idx_reaction_reactor_created", columnList = "reactor_user_id, created_at DESC"),
        @Index(name = "idx_reaction_feedback_reactor", columnList = "feedback_id, reactor_user_id, created_at DESC"),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reaction {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * The delivered {@link Feedback} unit this reaction responds to. DB FK {@code fk_reaction_feedback} with
     * {@code ON DELETE CASCADE}: a reaction has no meaning without the unit it reacts to, so deleting the unit
     * removes its immutable reaction rows rather than orphaning them.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reaction_feedback"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Feedback feedback;

    /** Read-only scalar view of the {@link #feedback} foreign key. */
    @Column(name = "feedback_id", nullable = false, insertable = false, updatable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    /**
     * The user who submitted this reaction — always the feedback's recipient, since only the recipient may
     * react. Scalar {@code Long} FK to {@code user} with no {@code @ManyToOne}, matching the identity columns
     * elsewhere (e.g. {@code Observation.aboutUserId}): the relationship is declared in Liquibase as the
     * Hibernate-invisible {@code sfk_reaction_reactor}, keeping the cross-module user reference out of the JPA
     * graph and off the schema-drift gate. {@code ON DELETE RESTRICT} (the default, no cascade) — a reaction is
     * research evidence and must not vanish with a user-row deletion.
     */
    @NotNull
    @Column(name = "reactor_user_id", nullable = false)
    private Long reactorUserId;

    /** How useful the recipient found the unit. */
    @Enumerated(EnumType.STRING)
    @Column(name = "usefulness", length = 16)
    private @Nullable FeedbackUsefulness usefulness;

    /**
     * What the recipient decided to do.
     *
     * <p>Column {@code action} rather than {@code resolution}: the column shipped under that name and a
     * released one is renamed only across two releases. The field says what the value means.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 16)
    private @Nullable FeedbackResolution resolution;

    /**
     * The recipient's free-text rationale. NULL means none was given. Coupled to {@link #resolution} by the DB
     * CHECK {@code chk_reaction_disputed_explanation}: a {@link FeedbackResolution#DISPUTED} row must carry a
     * non-blank explanation (the reasoned rejection IS the evaluative judgement), while {@code ADDRESSED} and
     * {@code NOT_APPLICABLE} may leave it NULL.
     */
    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
