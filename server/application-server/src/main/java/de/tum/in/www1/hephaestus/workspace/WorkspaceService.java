package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.user.AuthenticatedUserService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.UpdateWorkspaceFeaturesRequestDTO;
import de.tum.in.www1.hephaestus.workspace.exception.*;
import de.tum.in.www1.hephaestus.workspace.settings.WorkspaceTeamSettingsService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central service for workspace management operations.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li><b>Workspace creation:</b> Creates workspaces with owner membership</li>
 *   <li><b>Slug management:</b> Renames with redirect history via {@link WorkspaceSlugService}</li>
 *   <li><b>Settings delegation:</b> Forwards to {@link WorkspaceSettingsService}</li>
 *   <li><b>League points:</b> Triggers recalculation via {@link LeaguePointsRecalculator}</li>
 * </ul>
 *
 * <h2>Related Services</h2>
 * For other workspace operations, use the specialized services:
 * <ul>
 *   <li>{@link WorkspaceQueryService} – Read-only queries and lookups</li>
 *   <li>{@link WorkspaceLifecycleService} – Status transitions (suspend/resume/purge)</li>
 *   <li>{@link WorkspaceInstallationService} – GitHub App installation handling</li>
 *   <li>{@link WorkspaceRepositoryMonitorService} – Repository monitoring configuration</li>
 *   <li>{@link WorkspaceActivationService} – Activation/startup orchestration</li>
 *   <li>{@link WorkspaceTeamSettingsService} – Workspace-scoped team settings</li>
 *   <li>{@link WorkspaceTeamLabelService} – Team/label associations</li>
 * </ul>
 *
 * <h2>Multi-Tenancy Note</h2>
 * This service is workspace-agnostic: it manages workspaces themselves (the tenant root),
 * not data within workspaces. Methods take workspace slug or {@link WorkspaceContext} as parameters.
 *
 * @see Workspace
 * @see WorkspaceContext
 * @see WorkspaceTeamSettingsService
 */
@Service
@WorkspaceAgnostic("Manages workspaces themselves - the tenant root, not data within workspaces")
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private static final boolean DEFAULT_PUBLIC_VISIBILITY = false;

    // Core repositories
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserService authenticatedUserService;

    // Services
    private final WorkspaceSlugService workspaceSlugService;
    private final WorkspaceSettingsService workspaceSettingsService;
    private final LeaguePointsRecalculator leaguePointsRecalculator;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final GitLabWorkspaceInitializationService gitLabWorkspaceInitializationService;

    public WorkspaceService(
        WorkspaceRepository workspaceRepository,
        UserRepository userRepository,
        AuthenticatedUserService authenticatedUserService,
        WorkspaceSlugService workspaceSlugService,
        WorkspaceSettingsService workspaceSettingsService,
        LeaguePointsRecalculator leaguePointsRecalculator,
        WorkspaceMembershipService workspaceMembershipService,
        GitLabWorkspaceInitializationService gitLabWorkspaceInitializationService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.workspaceSlugService = workspaceSlugService;
        this.workspaceSettingsService = workspaceSettingsService;
        this.leaguePointsRecalculator = leaguePointsRecalculator;
        this.workspaceMembershipService = workspaceMembershipService;
        this.gitLabWorkspaceInitializationService = gitLabWorkspaceInitializationService;
    }

    // ========================================================================
    // Workspace Lookup
    // ========================================================================

    public Optional<Workspace> getWorkspaceBySlug(String slug) {
        return workspaceRepository.findByWorkspaceSlug(slug);
    }

    @Transactional(readOnly = true)
    public Workspace getWorkspaceByRepositoryOwner(String nameWithOwner) {
        return workspaceRepository
            .findByRepositoriesToMonitor_NameWithOwner(nameWithOwner)
            .or(() -> resolveFallbackWorkspace("repository " + nameWithOwner))
            .orElseThrow(() -> new IllegalStateException("No workspace found for repository: " + nameWithOwner));
    }

    private Optional<Workspace> resolveFallbackWorkspace(String context) {
        List<Workspace> all = workspaceRepository.findAll();
        if (all.size() == 1) {
            log.info(
                "Resolved fallback workspace: workspaceId={}, context={}",
                all.getFirst().getId(),
                LoggingUtils.sanitizeForLog(context)
            );
            return Optional.of(all.getFirst());
        }
        log.warn(
            "Skipped workspace resolution: reason=ambiguousContext, context={}, workspaceCount={}",
            LoggingUtils.sanitizeForLog(context),
            all.size()
        );
        return Optional.empty();
    }

    // ========================================================================
    // Workspace Creation
    // ========================================================================

    @Transactional
    public Workspace createWorkspace(
        String rawSlug,
        String displayName,
        String accountLogin,
        AccountType accountType,
        Long ownerUserId
    ) {
        String slug = workspaceSlugService.normalize(rawSlug);
        workspaceSlugService.validate(slug);

        if (!workspaceSlugService.isAvailable(slug)) {
            throw new WorkspaceSlugConflictException(slug);
        }

        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug(slug);
        workspace.setDisplayName(displayName);
        workspace.setIsPubliclyViewable(DEFAULT_PUBLIC_VISIBILITY);
        workspace.setAccountLogin(accountLogin);
        workspace.setAccountType(accountType);
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);

        try {
            Workspace saved = workspaceRepository.save(workspace);
            createOwnerRole(saved, ownerUserId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Unique constraint violation on slug
            throw new WorkspaceSlugConflictException(slug, e);
        }
    }

    /**
     * Creates a workspace from a DTO, including optional git provider configuration.
     *
     * <p>For GitLab workspaces, the DTO should include {@code gitProviderMode = GITLAB_PAT}
     * along with a personal access token and optional server URL. The PAT is automatically
     * encrypted at rest via {@link de.tum.in.www1.hephaestus.core.security.EncryptedStringConverter}.
     */
    @Transactional
    public Workspace createWorkspace(CreateWorkspaceRequestDTO request) {
        // Always prefer the authenticated user to prevent privilege escalation. For dual-IdP
        // principals, pick the linked row matching the workspace's provider so subsequent
        // API calls run against the correct IdP identity; fall back to the primary row when
        // the target provider isn't linked. Fall back to the deprecated ownerUserId only
        // when no auth context exists (e.g. tests).
        GitProviderType targetProvider =
            request.gitProviderMode() == Workspace.GitProviderMode.GITLAB_PAT
                ? GitProviderType.GITLAB
                : GitProviderType.GITHUB;
        Long ownerUserId = authenticatedUserService
            .findLinkedUserForProvider(targetProvider)
            .map(User::getId)
            .orElse(request.ownerUserId());

        Workspace workspace = createWorkspace(
            request.workspaceSlug(),
            request.displayName(),
            request.accountLogin(),
            request.accountType(),
            ownerUserId
        );

        if (request.gitProviderMode() != null) {
            workspace.setGitProviderMode(request.gitProviderMode());
        }
        if (request.personalAccessToken() != null && !request.personalAccessToken().isBlank()) {
            workspace.setPersonalAccessToken(request.personalAccessToken());
        }
        if (request.serverUrl() != null && !request.serverUrl().isBlank()) {
            workspace.setServerUrl(request.serverUrl().trim());
        }

        // GitLab PAT workspaces monitor all repositories in the group by default
        if (request.gitProviderMode() == Workspace.GitProviderMode.GITLAB_PAT) {
            workspace.setRepositorySelection(RepositorySelection.ALL);
        }

        // Explicit save required: when called via createWorkspaceWithInitialization(),
        // self-invocation bypasses @Transactional proxy, so dirty-checking flush won't
        // happen automatically. The inner createWorkspace(5-args) saved the base entity,
        // but these additional fields (gitProviderMode, PAT, serverUrl) need a second save.
        return workspaceRepository.save(workspace);
    }

    /**
     * Creates a workspace and triggers async GitLab initialization if applicable.
     *
     * <p>This method is intentionally NOT {@code @Transactional} so that the inner
     * {@link #createWorkspace(CreateWorkspaceRequestDTO)} transaction commits before
     * the async initialization reads the workspace from the database.
     *
     * @param request the workspace creation request
     * @return the created workspace
     */
    public Workspace createWorkspaceWithInitialization(CreateWorkspaceRequestDTO request) {
        Workspace workspace = createWorkspace(request);

        // Trigger async repository discovery for GitLab PAT workspaces.
        // The @Transactional createWorkspace() has already committed at this point,
        // so the async thread will find the workspace in the database.
        if (workspace.getGitProviderMode() == Workspace.GitProviderMode.GITLAB_PAT) {
            gitLabWorkspaceInitializationService.initializeAsync(workspace.getId());
        }

        return workspace;
    }

    private void createOwnerRole(Workspace workspace, Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalStateException(
                "Cannot create workspace without an owner. " +
                    "The authenticated user must have a corresponding git provider User entity. " +
                    "workspaceSlug=" +
                    workspace.getWorkspaceSlug()
            );
        }
        workspaceMembershipService.createMembership(workspace, ownerUserId, WorkspaceMembership.WorkspaceRole.OWNER);
    }

    // ========================================================================
    // Workspace Account Login Management
    // ========================================================================

    @Transactional
    public Workspace updateAccountLogin(Long workspaceId, String accountLogin) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        if (!Objects.equals(workspace.getAccountLogin(), accountLogin)) {
            workspace.setAccountLogin(accountLogin);
            workspace = workspaceRepository.save(workspace);
        }

        return workspace;
    }

    // ========================================================================
    // League Points Recalculation
    // ========================================================================

    /**
     * Reset and recalculate league points for all users by replaying their
     * contributions from the first recorded activity until now.
     */
    @Transactional
    public void resetAndRecalculateLeagues(String slug) {
        Workspace workspace = requireWorkspace(slug);
        log.info(
            "Reset league points: workspaceId={}, workspaceSlug={}",
            workspace.getId(),
            workspace.getWorkspaceSlug()
        );
        resetAndRecalculateLeaguesInternal(workspace.getId());
    }

    public void resetAndRecalculateLeagues(WorkspaceContext workspaceContext) {
        Workspace workspace = requireWorkspace(requireSlug(workspaceContext));
        resetAndRecalculateLeaguesInternal(workspace.getId());
    }

    private void resetAndRecalculateLeaguesInternal(Long workspaceId) {
        log.debug("Recalculating league points: workspaceId={}", workspaceId);

        if (workspaceId == null) {
            log.warn("Skipped league recalculation: reason=workspaceIdIsNull");
            return;
        }

        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            log.warn("Skipped league recalculation: reason=workspaceNotFound, workspaceId={}", workspaceId);
            return;
        }

        leaguePointsRecalculator.recalculate(workspace);
    }

    // ========================================================================
    // Settings Delegation
    // ========================================================================

    public Workspace updateSchedule(String slug, Integer day, String time) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updateSchedule(workspace.getId(), day, time);
    }

    public Workspace updateSchedule(WorkspaceContext workspaceContext, Integer day, String time) {
        return updateSchedule(requireSlug(workspaceContext), day, time);
    }

    public Workspace updateNotifications(String slug, Boolean enabled, String team, String channelId) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updateNotifications(workspace.getId(), enabled, team, channelId);
    }

    public Workspace updateNotifications(
        WorkspaceContext workspaceContext,
        Boolean enabled,
        String team,
        String channelId
    ) {
        return updateNotifications(requireSlug(workspaceContext), enabled, team, channelId);
    }

    public Workspace updateToken(String slug, String personalAccessToken) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updateToken(workspace.getId(), personalAccessToken);
    }

    public Workspace updateToken(WorkspaceContext workspaceContext, String personalAccessToken) {
        return updateToken(requireSlug(workspaceContext), personalAccessToken);
    }

    public Workspace updateSlackCredentials(String slug, String slackToken, String slackSigningSecret) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updateSlackCredentials(workspace.getId(), slackToken, slackSigningSecret);
    }

    public Workspace updateSlackCredentials(
        WorkspaceContext workspaceContext,
        String slackToken,
        String slackSigningSecret
    ) {
        return updateSlackCredentials(requireSlug(workspaceContext), slackToken, slackSigningSecret);
    }

    public Workspace updatePublicVisibility(String slug, Boolean isPubliclyViewable) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updatePublicVisibility(workspace.getId(), isPubliclyViewable);
    }

    public Workspace updatePublicVisibility(WorkspaceContext workspaceContext, Boolean isPubliclyViewable) {
        return updatePublicVisibility(requireSlug(workspaceContext), isPubliclyViewable);
    }

    public Workspace updateFeatures(String slug, UpdateWorkspaceFeaturesRequestDTO request) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updateFeatures(workspace.getId(), request);
    }

    public Workspace updateFeatures(WorkspaceContext workspaceContext, UpdateWorkspaceFeaturesRequestDTO request) {
        return updateFeatures(requireSlug(workspaceContext), request);
    }

    // ========================================================================
    // Slug Renaming
    // ========================================================================

    @Transactional
    public Workspace renameSlug(WorkspaceContext workspaceContext, String newSlug) {
        Objects.requireNonNull(workspaceContext, "WorkspaceContext must not be null");

        Long workspaceId = workspaceContext.id();
        if (workspaceId == null) {
            throw new EntityNotFoundException("Workspace", "context");
        }

        return renameSlug(workspaceId, newSlug);
    }

    @Transactional
    public Workspace renameSlug(Long workspaceId, String newSlug) {
        workspaceSlugService.validate(newSlug);

        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));

        String currentSlug = workspace.getWorkspaceSlug();

        if (currentSlug.equals(newSlug)) {
            log.debug(
                "Skipped workspace rename: reason=slugUnchanged, workspaceId={}, slug={}",
                workspaceId,
                LoggingUtils.sanitizeForLog(newSlug)
            );
            return workspace;
        }

        if (workspaceRepository.existsByWorkspaceSlug(newSlug)) {
            throw new WorkspaceSlugConflictException(newSlug);
        }

        if (!workspaceSlugService.isAvailable(newSlug)) {
            throw new WorkspaceSlugConflictException(newSlug);
        }

        workspaceSlugService.recordRename(workspace, currentSlug, newSlug);

        workspace.setWorkspaceSlug(newSlug);
        Workspace saved = workspaceRepository.save(workspace);

        log.info(
            "Renamed workspace: workspaceId={}, oldSlug={}, newSlug={}",
            workspaceId,
            LoggingUtils.sanitizeForLog(currentSlug),
            LoggingUtils.sanitizeForLog(newSlug)
        );

        return saved;
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private Workspace requireWorkspace(String slug) {
        if (isBlank(slug)) {
            throw new IllegalArgumentException("Workspace slug must not be blank.");
        }
        return workspaceRepository
            .findByWorkspaceSlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));
    }

    private String requireSlug(WorkspaceContext workspaceContext) {
        Objects.requireNonNull(workspaceContext, "WorkspaceContext must not be null");
        String slug = workspaceContext.slug();
        if (isBlank(slug)) {
            throw new IllegalArgumentException("Workspace context slug must not be blank.");
        }
        return slug;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
