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
    name = "practice_review_repository_branch",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_practice_review_repository_branch",
        columnNames = { "workspace_id", "repository_target_id", "base_branch" }
    ),
    indexes = @Index(name = "idx_practice_review_repository_branch_workspace", columnList = "workspace_id")
)
@Getter
@Setter
@NoArgsConstructor
public class PracticeReviewRepositoryBranch {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "repository_target_id", nullable = false, columnDefinition = "UUID")
    private UUID repositoryTargetId;

    @Column(name = "base_branch", nullable = false, length = 255)
    private String baseBranch;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
