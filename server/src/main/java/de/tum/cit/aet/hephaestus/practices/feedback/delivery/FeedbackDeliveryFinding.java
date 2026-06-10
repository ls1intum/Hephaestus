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
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Many-to-many link between a {@link FeedbackDelivery} (one message bundles many findings) and a
 * {@link PracticeFinding} (one finding recurs across channels and across re-posts over time). This
 * join is the analytic backbone connecting "what was delivered" to "what was found" to (via
 * {@code FindingReaction}) "what the contributor did about it".
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
@Setter
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
