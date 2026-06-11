package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Link rows of a {@link FeedbackDelivery} to the {@link PracticeFinding}s it rendered, with each
 * finding's display role. A delivery bundles many findings; within one delivery a finding appears at
 * most once (enforced by the unique constraint), so this is effectively a 1:N child of the delivery —
 * recurrence across re-reviews is a new finding row + a new delivery in the {@code supersedes} chain,
 * never a second link here. Progress queries must therefore {@code COUNT(DISTINCT finding_id)} across
 * the supersedes chain. This is the analytic backbone connecting "what was delivered" to "what was
 * found" to (via {@code FindingReaction}) "what the contributor did about it".
 */
@Entity
@Immutable
@Table(
    name = "feedback_delivery_finding",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_feedback_delivery_finding",
        columnNames = { "delivery_id", "finding_id" }
    )
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FeedbackDeliveryFinding {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "delivery_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_feedback_delivery_finding_delivery")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private FeedbackDelivery delivery;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "finding_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_feedback_delivery_finding_finding")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private PracticeFinding finding;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "display_role", nullable = false, length = 16)
    private FindingDisplayRole displayRole;

    /** Ordinal within the delivery (presentation order). */
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
