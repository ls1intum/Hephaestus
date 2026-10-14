package de.tum.cit.aet.hephaestus.practices.review.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "review_batch_target",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_review_batch_target_tenant",
                    columnNames = {"batch_id", "item_id", "workspace_id"})
        })
@IdClass(ReviewBatchTarget.Id.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewBatchTarget {

    @jakarta.persistence.Id
    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @jakarta.persistence.Id
    @Column(name = "item_id", nullable = false)
    private long itemId;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    public record Id(UUID batchId, long itemId) implements Serializable {}
}
