package de.tum.cit.aet.hephaestus.practices.feedback;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

/** A recipient's usefulness rating for one delivered feedback unit. */
@Entity
@Table(name = "feedback_helpfulness_vote")
@Getter
@NoArgsConstructor
public class FeedbackHelpfulnessVote {

    @Id
    @Column(name = "feedback_id", columnDefinition = "UUID")
    private UUID feedbackId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "feedback_id",
        insertable = false,
        updatable = false,
        foreignKey = @ForeignKey(name = "fk_feedback_helpfulness_vote_feedback")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    @JsonIgnore
    private Feedback feedback;

    @NotNull
    @Column(name = "helpful", nullable = false)
    private Boolean helpful;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
