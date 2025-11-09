package de.tum.in.www1.hephaestus.workspace;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "workspace_role_assignment",
    uniqueConstraints = @UniqueConstraint(name = "ux_wra_workspace_user", columnNames = { "workspace_id", "user_id" }),
    indexes = { @Index(name = "idx_wra_user_id", columnList = "user_id") }
)
@Getter
@Setter
@NoArgsConstructor
public class WorkspaceRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_wra_workspace"))
    private Workspace workspace;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkspaceRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum WorkspaceRole {
        OWNER,
        ADMIN,
        MEMBER,
        VIEWER,
    }
}
