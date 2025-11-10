package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;
import lombok.*;

@Entity
@Table(name = "workspace_membership")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class WorkspaceMembership {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("workspaceId")
    @JoinColumn(
        name = "workspace_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_workspace_membership_workspace")
    )
    @ToString.Exclude
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_workspace_membership_user"))
    @ToString.Exclude
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    @Column(name = "league_points", nullable = false)
    private int leaguePoints = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        private Long workspaceId;
        private Long userId;
    }

    public enum WorkspaceRole {
        OWNER,
        ADMIN,
        MEMBER;

        public static WorkspaceRole fromOrganizationRole(String organizationRole) {
            if (organizationRole == null) {
                return MEMBER;
            }
            return switch (organizationRole.toUpperCase(Locale.ROOT)) {
                case "OWNER" -> OWNER;
                case "ADMIN" -> ADMIN;
                default -> MEMBER;
            };
        }
    }
}
