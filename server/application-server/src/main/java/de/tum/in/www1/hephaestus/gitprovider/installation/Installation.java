package de.tum.in.www1.hephaestus.gitprovider.installation;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTarget;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "installation")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Installation extends BaseGitServiceEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", foreignKey = @ForeignKey(name = "fk_installation_target"))
    @ToString.Exclude
    private InstallationTarget target;

    @Column(name = "target_github_id")
    private Long targetGithubId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 32)
    private InstallationTarget.TargetType targetType = InstallationTarget.TargetType.UNKNOWN;

    @Column(name = "app_id")
    private Long appId;

    @Column(name = "access_tokens_url", length = 512)
    private String accessTokensUrl;

    @Column(name = "repositories_url", length = 512)
    private String repositoriesUrl;

    @Column(name = "html_url", length = 512)
    private String htmlUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "repository_selection", length = 32, nullable = false)
    private RepositorySelection repositorySelection = RepositorySelection.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", length = 32, nullable = false)
    private LifecycleState lifecycleState = LifecycleState.ACTIVE;

    @Column(name = "single_file_name", length = 512)
    private String singleFileName;

    @ElementCollection
    @CollectionTable(name = "installation_event", joinColumns = @JoinColumn(name = "installation_id"))
    @Column(name = "event_name", length = 128)
    private Set<String> subscribedEvents = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "installation_permission", joinColumns = @JoinColumn(name = "installation_id"))
    @MapKeyColumn(name = "permission_name", length = 128)
    @Column(name = "permission_level", length = 32)
    @Enumerated(EnumType.STRING)
    private Map<String, PermissionLevel> permissions = new HashMap<>();

    @OneToMany(mappedBy = "installation", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<InstallationRepositoryLink> repositoryLinks = new HashSet<>();

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suspended_by_id", foreignKey = @ForeignKey(name = "fk_installation_suspended_by"))
    @ToString.Exclude
    private User suspendedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "last_webhook_received_at")
    private Instant lastWebhookReceivedAt;

    @Column(name = "last_repositories_sync_at")
    private Instant lastRepositoriesSyncAt;

    @Column(name = "last_permissions_accepted_at")
    private Instant lastPermissionsAcceptedAt;

    public enum RepositorySelection {
        ALL,
        SELECTED,
        UNKNOWN;

        public static RepositorySelection fromSymbol(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            return switch (value.toLowerCase()) {
                case "all" -> ALL;
                case "selected" -> SELECTED;
                default -> UNKNOWN;
            };
        }
    }

    public enum LifecycleState {
        ACTIVE,
        SUSPENDED,
        DELETED,
    }

    public enum PermissionLevel {
        ADMIN,
        WRITE,
        READ,
        NONE,
        UNKNOWN;

        public static PermissionLevel fromValue(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            return switch (value.toLowerCase()) {
                case "read" -> READ;
                case "write" -> WRITE;
                case "admin" -> ADMIN;
                case "none" -> NONE;
                default -> UNKNOWN;
            };
        }
    }
    /*
     * Supported webhook fields/relationships (GHEventPayload.Installation, REST `installation` payload):
     * Fields:
     * - installation.id → BaseGitServiceEntity `id`
     * - installation.created_at / updated_at → `createdAt` / `updatedAt`
     * - installation.app_id → `appId`
     * - installation.target_id → `targetGithubId`
     * - installation.target_type → `targetType`
     * - installation.access_tokens_url / repositories_url / html_url → respective URL fields
     * - installation.repository_selection → `repositorySelection`
     * - installation.single_file_name → `singleFileName`
     * - installation.events[] → `subscribedEvents`
     * - installation.permissions{key:value} → `permissions`
     * - installation.suspended_at → `suspendedAt`
     * - webhook action timestamps → `lastWebhookReceivedAt`, `lastRepositoriesSyncAt`, `lastPermissionsAcceptedAt`
     * Relationships:
     * - installation.account → `target`
     * - installation.suspended_by → `suspendedBy`
     * - installation.repositories / installation_repositories payloads → `repositoryLinks`
     *
     * Ignored although hub4j 2.0-rc.5 exposes them without extra fetch:
     * Fields:
     * - installation.node_id (GHObject#getNodeId())
     * - installation.html_url derived `_links` (duplicative)
     * - Minimal repository snapshots beyond id/full_name provided in webhook (handled by Repository sync service)
     * Relationships:
     * - installation.account nested avatar/login duplicates managed by InstallationTarget entity
     *
     * Desired but missing in hub4j 2.0-rc.5 (present in GitHub REST/GraphQL):
     * Fields:
     * - installation.client_id, app_slug, has_multiple_single_files, single_file_paths (documented by REST webhook)
     * - installation.pending_tasks / contacts (GraphQL `AppInstallationContact`)
     * Relationships:
     * - installation.suspended_by simple user lacks GraphQL actor data (e.g., `AppInstallationSuspendedEvent` edges)
     *
     * Requires extra REST/GraphQL fetch (explicitly out of scope now):
     * - `GET /app/installations/{id}` to hydrate account permissions per repository and marketplace plan
     * - GraphQL `AppInstallation.repositories` connection for paginated repository access, token revocation history
     * - Marketplace purchase metadata and billing state (GraphQL `MarketplacePurchase`).
     */
}
