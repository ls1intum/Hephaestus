package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.kohsuke.github.GHRepositorySelection;

@Entity
@Table(name = "workspace")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant usersSyncedAt;

    private Instant teamsSyncedAt;

    private Instant membersSyncedAt;

    @Column(name = "slug", unique = true, nullable = false, length = 64)
    @NotBlank(message = "Workspace slug is required")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,50}$")
    private String workspaceSlug;

    @Column(name = "display_name", nullable = false, length = 120)
    @NotBlank(message = "Display name is required")
    private String displayName;

    @Column(name = "is_publicly_viewable", nullable = false)
    @NotNull(message = "Public viewable flag is required")
    private Boolean isPubliclyViewable = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull(message = "Status is required")
    private WorkspaceStatus status = WorkspaceStatus.ACTIVE;

    @OneToMany(
        mappedBy = "workspace",
        fetch = FetchType.EAGER,
        cascade = { CascadeType.PERSIST, CascadeType.REMOVE },
        orphanRemoval = true
    )
    @ToString.Exclude
    private Set<RepositoryToMonitor> repositoriesToMonitor = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private GitProviderMode gitProviderMode = GitProviderMode.PAT_ORG;

    private Long installationId;

    @Column(name = "account_login", nullable = false, length = 120)
    @NotBlank(message = "Account login is required")
    private String accountLogin;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @Column(name = "personal_access_token", columnDefinition = "TEXT")
    @ToString.Exclude
    private String personalAccessToken;

    @Enumerated(EnumType.STRING)
    private GHRepositorySelection githubRepositorySelection; // ALL / SELECTED

    private Instant installationLinkedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", unique = true, foreignKey = @ForeignKey(name = "fk_workspace_organization"))
    @ToString.Exclude
    private Organization organization;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "leaderboard_schedule_day")
    private Integer leaderboardScheduleDay; // 1=Monday, 7=Sunday

    @Column(name = "leaderboard_schedule_time", length = 10)
    private String leaderboardScheduleTime; // Format: "HH:mm"

    @Column(name = "leaderboard_notification_enabled")
    private Boolean leaderboardNotificationEnabled;

    @Column(name = "leaderboard_notification_team", length = 100)
    private String leaderboardNotificationTeam;

    @Column(name = "leaderboard_notification_channel_id", length = 100)
    private String leaderboardNotificationChannelId;

    // TODO: Encrypt at rest using JPA AttributeConverter with AES-256-GCM
    @Column(name = "slack_token", columnDefinition = "TEXT")
    @ToString.Exclude
    private String slackToken;

    // TODO: Encrypt at rest using JPA AttributeConverter with AES-256-GCM
    @Column(name = "slack_signing_secret", columnDefinition = "TEXT")
    @ToString.Exclude
    private String slackSigningSecret;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    //TODO: Only temporary to differentiate between ls1intum <-> orgs installed via GHApp. To be deleted in the future
    public enum GitProviderMode {
        PAT_ORG,
        GITHUB_APP_INSTALLATION,
    }

    public enum WorkspaceStatus {
        ACTIVE,
        SUSPENDED,
        PURGED,
    }
}
