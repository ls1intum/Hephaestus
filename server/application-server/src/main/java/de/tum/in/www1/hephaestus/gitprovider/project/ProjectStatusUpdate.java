package de.tum.in.www1.hephaestus.gitprovider.project;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;
import org.springframework.lang.NonNull;

/**
 * Represents a status update for a GitHub Projects V2 project.
 * Status updates track project health with statuses like ON_TRACK, AT_RISK, OFF_TRACK.
 */
@Entity
@Table(
    name = "project_status_update",
    uniqueConstraints = { @UniqueConstraint(name = "uk_project_status_update_node_id", columnNames = { "node_id" }) },
    indexes = {
        @Index(name = "idx_project_status_update_project_id", columnList = "project_id"),
        @Index(name = "idx_project_status_update_created_at", columnList = "project_id, created_at DESC"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class ProjectStatusUpdate extends BaseGitServiceEntity {

    public enum Status {
        INACTIVE,
        ON_TRACK,
        AT_RISK,
        OFF_TRACK,
    }

    /**
     * GitHub's node ID for the status update.
     */
    @Column(name = "node_id", length = 64)
    private String nodeId;

    /**
     * The project this status update belongs to.
     */
    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @ToString.Exclude
    private Project project;

    /**
     * The body/content of the status update (markdown).
     */
    @Column(columnDefinition = "TEXT")
    private String body;

    /**
     * The start date for the status update period.
     */
    @Column(name = "start_date")
    private LocalDate startDate;

    /**
     * The target date for the status update period.
     */
    @Column(name = "target_date")
    private LocalDate targetDate;

    /**
     * The status of the project (ON_TRACK, AT_RISK, OFF_TRACK, INACTIVE).
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status;

    /**
     * The user who created the status update.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    @ToString.Exclude
    private User creator;
}
