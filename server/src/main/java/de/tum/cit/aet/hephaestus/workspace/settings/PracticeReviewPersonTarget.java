package de.tum.cit.aet.hephaestus.workspace.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "practice_review_person_target",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_practice_review_person_target",
        columnNames = { "workspace_id", "user_id" }
    ),
    indexes = @Index(name = "idx_practice_review_person_target_workspace", columnList = "workspace_id")
)
@Getter
@Setter
@NoArgsConstructor
public class PracticeReviewPersonTarget {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
