package de.tum.cit.aet.hephaestus.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.Nullable;

/** One account's membership in a workspace. */
@Entity
@Table(name = "membership")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Membership {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("workspaceId")
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "sfk_membership_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    /**
     * Whether this member is left out of workspace activity lists and totals. Any member of the
     * workspace can still read their activity by login.
     */
    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    @Column(name = "review_selected", nullable = false)
    private boolean reviewSelected;

    @Column(name = "onboarding_seen_revision")
    private @Nullable Long onboardingSeenRevision;

    @Column(name = "public_activity_seen", nullable = false)
    private boolean publicActivitySeen;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "left_at")
    private @Nullable Instant leftAt;

    public @Nullable Long getAccountId() {
        return id.getAccountId();
    }

    /** Fields are populated by {@code @MapsId} from the workspace relationship and account id. */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Nullable
        private Long workspaceId;

        @Nullable
        private Long accountId;
    }

    public enum WorkspaceRole {
        OWNER,
        ADMIN,
        MEMBER;

        /** Declaration order is descending privilege: OWNER, ADMIN, MEMBER. */
        public boolean isAtLeast(WorkspaceRole requiredRole) {
            return compareTo(requiredRole) <= 0;
        }

        /** Unknown organization roles receive MEMBER access. */
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
