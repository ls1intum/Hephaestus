package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.security.EncryptedStringConverter;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/**
 * Root tenant entity in Hephaestus's multi-tenant architecture.
 *
 * <p>A Workspace represents an isolated tenant boundary, scoping all domain data
 * (repositories, users, issues, PRs, teams) to a single GitHub organization or user account.
 * All workspace-scoped queries are filtered by {@code workspace_id} to enforce tenant isolation.
 *
 * <h2>Multi-Tenancy Model</h2>
 * <ul>
 *   <li><b>Isolation:</b> Each workspace operates independently with its own data, settings, and members</li>
 *   <li><b>Identity:</b> Uniquely identified by {@link #workspaceSlug} (URL-safe) and internal {@link #id}</li>
 *   <li><b>Ownership:</b> Linked to a GitHub account via {@link #accountLogin} and optionally a GitHub App installation</li>
 * </ul>
 *
 * <h2>Authentication Modes</h2>
 * The workspace supports multiple authentication modes for git provider API access:
 * <ul>
 *   <li>{@link GitProviderMode#PAT_ORG} – GitHub PAT for local development (stored encrypted)</li>
 *   <li>{@link GitProviderMode#GITHUB_APP_INSTALLATION} – GitHub App installation for production (preferred)</li>
 *   <li>{@link GitProviderMode#GITLAB_PAT} – GitLab Personal Access Token</li>
 * </ul>
 *
 * <p>See {@link WorkspaceProperties} for configuration of local development PAT mode.
 *
 * <h2>Lifecycle States</h2>
 * Workspaces follow a defined lifecycle managed by {@link WorkspaceLifecycleService}:
 * <pre>
 *   ACTIVE ──suspend──► SUSPENDED ──resume──► ACTIVE
 *      │                    │
 *      └──────purge────────►│
 *                           └──────purge─────► PURGED (terminal)
 * </pre>
 *
 * <h2>Key Relationships</h2>
 * <ul>
 *   <li>{@link #repositoriesToMonitor} – Repositories tracked for webhook events</li>
 *   <li>{@link #organization} – Optional link to synced GitHub Organization entity</li>
 *   <li>{@link WorkspaceMembership} – User memberships with roles (OWNER/ADMIN/MEMBER)</li>
 * </ul>
 *
 * @see WorkspaceContext
 * @see WorkspaceMembership
 * @see WorkspaceLifecycleService
 */
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

    // ========================================================================
    // Sync Timestamps - Track last successful sync for each data domain
    // Used by sync services to determine incremental fetch windows
    // ========================================================================

    /** Last sync time for organization users (collaborators with repo access) */
    private Instant usersSyncedAt;

    /** Last sync time for organization teams structure */
    private Instant teamsSyncedAt;

    /** Last sync time for team membership assignments */
    private Instant membersSyncedAt;

    /** Last sync time for sub-issue relationships (parent-child) via GraphQL */
    private Instant subIssuesSyncedAt;

    /** Last sync time for organization issue types via GraphQL */
    private Instant issueTypesSyncedAt;

    /** Last sync time for issue dependencies (blocked_by/blocking) via GraphQL */
    private Instant issueDependenciesSyncedAt;

    // ========================================================================
    // Identity & Display
    // ========================================================================

    /**
     * URL-safe unique identifier for the workspace.
     * Used in URLs: {@code /api/workspaces/{slug}/...}
     * <p>
     * Format: lowercase alphanumeric with hyphens, 3-51 chars, must start with alphanumeric.
     * Immutable after creation (renames create redirect entries in {@link WorkspaceSlugHistory}).
     */
    @Column(name = "slug", unique = true, nullable = false, length = 64)
    @NotBlank(message = "Workspace slug is required")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,50}$")
    private String workspaceSlug;

    /** Human-readable name shown in UI (e.g., "TUM Applied Software Engineering") */
    @Column(name = "display_name", nullable = false, length = 120)
    @NotBlank(message = "Display name is required")
    private String displayName;

    /**
     * When {@code true}, unauthenticated users can view public workspace data
     * (leaderboards, public stats). Defaults to {@code false} for privacy.
     */
    @Column(name = "is_publicly_viewable", nullable = false)
    @NotNull(message = "Public viewable flag is required")
    private Boolean isPubliclyViewable = false;

    /**
     * Current lifecycle state of the workspace.
     *
     * @see WorkspaceStatus for valid states and transitions
     * @see WorkspaceLifecycleService for state transition operations
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull(message = "Status is required")
    private WorkspaceStatus status = WorkspaceStatus.ACTIVE;

    // ========================================================================
    // Repository Monitoring
    // ========================================================================

    /**
     * Repositories this workspace monitors for webhook events.
     * <p>
     * Note: EAGER fetch is intentional - workspace queries typically need repository list,
     * and the set is bounded (typically &lt;100 repos per workspace).
     */
    @OneToMany(
        mappedBy = "workspace",
        fetch = FetchType.EAGER,
        cascade = { CascadeType.PERSIST, CascadeType.REMOVE },
        orphanRemoval = true
    )
    @ToString.Exclude
    private Set<RepositoryToMonitor> repositoriesToMonitor = new HashSet<>();

    // ========================================================================
    // Git Provider Configuration
    // ========================================================================

    /**
     * Authentication mode for git provider API access.
     * Determines authentication strategy (PAT, GitHub App, OAuth) and provider identity.
     */
    @Enumerated(EnumType.STRING)
    private GitProviderMode gitProviderMode = GitProviderMode.PAT_ORG;

    /**
     * Custom server URL for self-hosted git provider instances.
     * <p>
     * When {@code null}, defaults are derived from the provider type:
     * <ul>
     *   <li>GitHub: {@code https://api.github.com}</li>
     *   <li>GitLab: {@code https://gitlab.com}</li>
     * </ul>
     * Set this for GitHub Enterprise Server or self-hosted GitLab instances.
     */
    @Column(name = "server_url", length = 512)
    private String serverUrl;

    /** GitHub App installation ID (null for PAT-based and GitLab workspaces) */
    private Long installationId;

    /** Git provider account login (organization or user login, e.g., "ls1intum") */
    @Column(name = "account_login", nullable = false, length = 120)
    @NotBlank(message = "Account login is required")
    private String accountLogin;

    /** Whether the GitHub account is an organization or individual user */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    @NotNull(message = "Account type is required")
    private AccountType accountType;

    /**
     * Personal Access Token for GitHub API (legacy authentication mode).
     * Encrypted at rest using AES-256-GCM via {@link EncryptedStringConverter}.
     * Null when using GitHub App installation authentication.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "personal_access_token", columnDefinition = "TEXT")
    @ToString.Exclude
    private String personalAccessToken;

    /** GitHub App repository selection mode: ALL repositories or SELECTED subset */
    @Enumerated(EnumType.STRING)
    private RepositorySelection githubRepositorySelection;

    /** Timestamp when GitHub App installation was linked to this workspace */
    private Instant installationLinkedAt;

    /**
     * Synced GitHub Organization entity (populated after initial organization sync).
     * Null for user-type workspaces or before first sync completes.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", unique = true, foreignKey = @ForeignKey(name = "fk_workspace_organization"))
    @ToString.Exclude
    private Organization organization;

    // ========================================================================
    // Audit Timestamps
    // ========================================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ========================================================================
    // Leaderboard Configuration
    // ========================================================================

    /** Day of week for scheduled leaderboard generation (1=Monday, 7=Sunday) */
    @Column(name = "leaderboard_schedule_day")
    private Integer leaderboardScheduleDay;

    /** Time of day for scheduled leaderboard generation (format: "HH:mm", e.g., "09:00") */
    @Column(name = "leaderboard_schedule_time", length = 10)
    private String leaderboardScheduleTime;

    /** Whether to send Slack notifications when leaderboard is generated */
    @Column(name = "leaderboard_notification_enabled")
    private Boolean leaderboardNotificationEnabled;

    /** Slack team/workspace ID for leaderboard notifications */
    @Column(name = "leaderboard_notification_team", length = 100)
    private String leaderboardNotificationTeam;

    /** Slack channel ID where leaderboard notifications are posted */
    @Column(name = "leaderboard_notification_channel_id", length = 100)
    private String leaderboardNotificationChannelId;

    // ========================================================================
    // Slack Integration (encrypted credentials)
    // ========================================================================

    /**
     * Slack Bot OAuth token for posting messages.
     * Encrypted at rest using AES-256-GCM via {@link EncryptedStringConverter}.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "slack_token", columnDefinition = "TEXT")
    @ToString.Exclude
    private String slackToken;

    /**
     * Slack signing secret for webhook signature verification.
     * Encrypted at rest using AES-256-GCM via {@link EncryptedStringConverter}.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "slack_signing_secret", columnDefinition = "TEXT")
    @ToString.Exclude
    private String slackSigningSecret;

    /**
     * Returns the high-level provider type (GITHUB or GITLAB) derived from the authentication mode.
     *
     * @return the provider type, defaulting to {@link GitProviderType#GITHUB} if mode is null
     */
    public GitProviderType getProviderType() {
        if (gitProviderMode == null) {
            return GitProviderType.GITHUB;
        }
        return switch (gitProviderMode) {
            case PAT_ORG, GITHUB_APP_INSTALLATION -> GitProviderType.GITHUB;
            case GITLAB_PAT -> GitProviderType.GITLAB;
        };
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ========================================================================
    // Nested Enums
    // ========================================================================

    /**
     * Authentication mode for GitHub API access.
     *
     * <h2>Production vs Development</h2>
     * <ul>
     *   <li><b>{@link #GITHUB_APP_INSTALLATION}</b> - Production mode, automatic workspace provisioning via GitHub App</li>
     *   <li><b>{@link #PAT_ORG}</b> - Local development mode, manual workspace bootstrap with a GitHub PAT</li>
     * </ul>
     *
     * <h2>Migration Path</h2>
     * PAT workspaces without a stored token are automatically promoted to GitHub App mode
     * when the GitHub App is installed on the same organization. PAT workspaces with a
     * stored token are preserved to support ongoing local development.
     *
     * @see WorkspaceProvisioningService#bootstrapDefaultPatWorkspace() for PAT workspace creation
     * @see WorkspaceInstallationService#createOrUpdateFromInstallation for migration logic
     */
    public enum GitProviderMode {
        /**
         * Personal Access Token authentication for local development.
         * <p>
         * This mode is intended for local development environments where setting up
         * a full GitHub App with webhooks is impractical. Configure via:
         * <ul>
         *   <li>{@code hephaestus.workspace.init-default: true}</li>
         *   <li>{@code hephaestus.workspace.default.token: <your-PAT>}</li>
         *   <li>{@code hephaestus.workspace.default.login: <org-or-user>}</li>
         * </ul>
         * <p>
         * <b>Not recommended for production</b> - use GitHub App installation instead.
         */
        PAT_ORG,
        /**
         * GitHub App installation authentication (production mode).
         * <p>
         * Uses short-lived installation tokens with automatic rotation.
         * Workspaces are automatically created when the GitHub App is installed.
         */
        GITHUB_APP_INSTALLATION,
        /**
         * GitLab Personal Access Token authentication.
         * <p>
         * Uses a long-lived PAT to access the GitLab API.
         * Suitable for self-hosted GitLab instances or GitLab.com.
         */
        GITLAB_PAT,
    }

    /**
     * Workspace lifecycle states with defined transition rules.
     *
     * <h2>State Machine</h2>
     * <pre>
     *   ACTIVE ──suspend──► SUSPENDED ──resume──► ACTIVE
     *      │                    │
     *      └──────purge────────►│
     *                           └──────purge─────► PURGED (terminal)
     * </pre>
     *
     * <h2>Behavior by State</h2>
     * <ul>
     *   <li><b>ACTIVE:</b> Normal operation - syncs run, webhooks processed, full access</li>
     *   <li><b>SUSPENDED:</b> Read-only mode - no syncs, webhooks queued, limited access</li>
     *   <li><b>PURGED:</b> Soft-deleted - excluded from queries, pending hard delete</li>
     * </ul>
     *
     * @see WorkspaceLifecycleService for state transition operations
     */
    public enum WorkspaceStatus {
        /** Workspace is fully operational */
        ACTIVE,
        /** Workspace is temporarily disabled (billing, maintenance, etc.) */
        SUSPENDED,
        /** Workspace is soft-deleted and pending permanent removal */
        PURGED,
    }
}
