package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.PostgresStringUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookLabel;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * Base class for GitLab entity processors with shared helper methods.
 * <p>
 * Provides common functionality for finding or creating related entities
 * (users, labels) that is shared across GitLab Issue, MR, and Note processors.
 * <p>
 * All GitLab entity IDs are negated via {@link GitLabSyncConstants#toEntityId(long)}
 * before storage to prevent collision with GitHub IDs.
 */
public abstract class BaseGitLabProcessor {

    private static final Logger log = LoggerFactory.getLogger(BaseGitLabProcessor.class);

    /**
     * Formatter for GitLab webhook timestamps: {@code "yyyy-MM-dd HH:mm:ss Z"}.
     * <p>
     * GitLab webhooks use a non-ISO timestamp format. Example: {@code "2026-01-31 19:03:35 +0100"}.
     * The GraphQL API uses standard ISO-8601. This formatter handles the webhook format
     * while {@link OffsetDateTime#parse} handles the ISO-8601 format.
     */
    private static final DateTimeFormatter GITLAB_WEBHOOK_TIMESTAMP = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart()
        .appendPattern(" ")
        .appendOffset("+HHmm", "+0000")
        .optionalEnd()
        .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
        .toFormatter();

    protected final UserRepository userRepository;
    protected final LabelRepository labelRepository;
    protected final RepositoryRepository repositoryRepository;
    protected final ScopeIdResolver scopeIdResolver;
    protected final RepositoryScopeFilter repositoryScopeFilter;
    protected final GitLabProperties gitLabProperties;

    protected BaseGitLabProcessor(
        UserRepository userRepository,
        LabelRepository labelRepository,
        RepositoryRepository repositoryRepository,
        ScopeIdResolver scopeIdResolver,
        RepositoryScopeFilter repositoryScopeFilter,
        GitLabProperties gitLabProperties
    ) {
        this.userRepository = userRepository;
        this.labelRepository = labelRepository;
        this.repositoryRepository = repositoryRepository;
        this.scopeIdResolver = scopeIdResolver;
        this.repositoryScopeFilter = repositoryScopeFilter;
        this.gitLabProperties = gitLabProperties;
    }

    // ========================================================================
    // User Resolution
    // ========================================================================

    /**
     * Finds or creates a user from webhook data.
     * <p>
     * Uses negated IDs and sets provider to GITLAB.
     * Constructs HTML URL from the GitLab server URL and username.
     */
    @Nullable
    protected User findOrCreateUser(@Nullable GitLabWebhookUser dto) {
        if (dto == null || dto.id() == null || dto.username() == null) {
            return null;
        }

        long entityId = GitLabSyncConstants.toEntityId(dto.id());
        String login = dto.username();
        String name = dto.name() != null ? dto.name() : login;
        String avatarUrl = dto.avatarUrl() != null ? dto.avatarUrl() : "";
        String htmlUrl = gitLabProperties.defaultServerUrl() + "/" + login;

        userRepository.upsertUser(
            entityId,
            login,
            name,
            avatarUrl,
            htmlUrl,
            User.Type.USER.name(),
            dto.email(),
            null, // createdAt — not in webhook
            null, // updatedAt — not in webhook
            GitProviderType.GITLAB.name()
        );

        return userRepository.findById(entityId).orElse(null);
    }

    /**
     * Finds or creates a user from GraphQL data (id, username, name, avatarUrl, webUrl).
     * <p>
     * Public because sync services need to resolve users from GraphQL response data.
     */
    @Nullable
    public User findOrCreateUser(
        String globalId,
        String username,
        @Nullable String name,
        @Nullable String avatarUrl,
        @Nullable String webUrl
    ) {
        if (globalId == null || username == null) {
            return null;
        }

        long entityId;
        try {
            entityId = GitLabSyncConstants.extractEntityId(globalId);
        } catch (IllegalArgumentException e) {
            log.warn("Skipped user resolution: reason=invalidGlobalId, gid={}", globalId);
            return null;
        }

        String resolvedName = name != null ? name : username;
        String resolvedAvatarUrl = avatarUrl != null ? avatarUrl : "";
        String resolvedHtmlUrl = webUrl != null ? webUrl : (gitLabProperties.defaultServerUrl() + "/" + username);

        userRepository.upsertUser(
            entityId,
            username,
            resolvedName,
            resolvedAvatarUrl,
            resolvedHtmlUrl,
            User.Type.USER.name(),
            null,
            null,
            null,
            GitProviderType.GITLAB.name()
        );

        return userRepository.findById(entityId).orElse(null);
    }

    // ========================================================================
    // Label Resolution
    // ========================================================================

    /**
     * Finds or creates a label from webhook data.
     */
    @Nullable
    protected Label findOrCreateLabel(@Nullable GitLabWebhookLabel dto, Repository repository) {
        if (dto == null || dto.title() == null || dto.title().isBlank()) {
            return null;
        }

        Optional<Label> existing = labelRepository.findByRepositoryIdAndName(repository.getId(), dto.title());
        if (existing.isPresent()) {
            return existing.get();
        }

        Long labelId =
            dto.id() != null
                ? GitLabSyncConstants.toEntityId(dto.id())
                : generateDeterministicLabelId(repository.getId(), dto.title());

        int inserted = labelRepository.insertIfAbsent(labelId, dto.title(), dto.color(), repository.getId());
        if (inserted == 0) {
            return labelRepository.findByRepositoryIdAndName(repository.getId(), dto.title()).orElse(null);
        }
        return labelRepository.findById(labelId).orElse(null);
    }

    /**
     * Finds or creates a label from GraphQL data (globalId, title, color).
     * <p>
     * Public because sync services need to resolve labels from GraphQL response data.
     * <p>
     * Uses deterministic composite IDs based on (repositoryId, labelName) rather than
     * GitLab global IDs. GitLab group-level labels share the same global ID across all
     * projects, but labels are stored per-repository in the database. Using the GitLab
     * global ID would cause primary key collisions when the same label appears in
     * multiple projects.
     */
    @Nullable
    public Label findOrCreateLabel(
        @Nullable String globalId,
        @Nullable String title,
        @Nullable String color,
        Repository repository
    ) {
        if (title == null || title.isBlank()) {
            return null;
        }

        Optional<Label> existing = labelRepository.findByRepositoryIdAndName(repository.getId(), title);
        if (existing.isPresent()) {
            return existing.get();
        }

        Long labelId = generateDeterministicLabelId(repository.getId(), title);

        int inserted = labelRepository.insertIfAbsent(labelId, title, color, repository.getId());
        if (inserted == 0) {
            return labelRepository.findByRepositoryIdAndName(repository.getId(), title).orElse(null);
        }
        return labelRepository.findById(labelId).orElse(null);
    }

    private Long generateDeterministicLabelId(Long repositoryId, String labelName) {
        long combined = (repositoryId << 32) | (labelName.hashCode() & 0xFFFFFFFFL);
        return -combined;
    }

    // ========================================================================
    // Relationship Updates
    // ========================================================================

    /**
     * Updates assignees collection from webhook user list.
     */
    protected boolean updateAssignees(@Nullable List<GitLabWebhookUser> assigneeDtos, Set<User> currentAssignees) {
        if (assigneeDtos == null) {
            return false;
        }

        Set<User> newAssignees = new HashSet<>();
        for (GitLabWebhookUser dto : assigneeDtos) {
            User assignee = findOrCreateUser(dto);
            if (assignee != null) {
                newAssignees.add(assignee);
            }
        }

        if (!currentAssignees.equals(newAssignees)) {
            currentAssignees.clear();
            currentAssignees.addAll(newAssignees);
            return true;
        }
        return false;
    }

    /**
     * Updates labels collection from webhook label list.
     */
    protected boolean updateLabels(
        @Nullable List<GitLabWebhookLabel> labelDtos,
        Collection<Label> currentLabels,
        Repository repository
    ) {
        if (labelDtos == null) {
            return false;
        }

        Set<Label> newLabels = new HashSet<>();
        for (GitLabWebhookLabel dto : labelDtos) {
            Label label = findOrCreateLabel(dto, repository);
            if (label != null) {
                newLabels.add(label);
            }
        }

        if (!new HashSet<>(currentLabels).equals(newLabels)) {
            currentLabels.clear();
            currentLabels.addAll(newLabels);
            return true;
        }
        return false;
    }

    // ========================================================================
    // Timestamp Parsing
    // ========================================================================

    /**
     * Parses a GitLab timestamp string to an Instant.
     * <p>
     * Handles both webhook format ({@code "2026-01-31 19:03:35 +0100"}) and
     * GraphQL ISO-8601 format ({@code "2026-01-31T19:03:35Z"}).
     */
    @Nullable
    protected static Instant parseGitLabTimestamp(@Nullable String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            // Try ISO-8601 first (GraphQL format)
            return OffsetDateTime.parse(timestamp).toInstant();
        } catch (DateTimeParseException e1) {
            try {
                // Try webhook format
                return OffsetDateTime.parse(timestamp, GITLAB_WEBHOOK_TIMESTAMP).toInstant();
            } catch (DateTimeParseException e2) {
                log.warn("Could not parse GitLab timestamp: value={}", timestamp);
                return null;
            }
        }
    }

    // ========================================================================
    // Context Resolution
    // ========================================================================

    /**
     * Resolves a ProcessingContext from a project's pathWithNamespace for webhook events.
     */
    @Nullable
    protected ProcessingContext resolveContext(@Nullable String pathWithNamespace, @Nullable String action) {
        if (pathWithNamespace == null || pathWithNamespace.isBlank()) {
            return null;
        }

        if (!repositoryScopeFilter.isRepositoryAllowed(pathWithNamespace)) {
            log.debug("Skipped event: reason=repositoryFiltered, repoName={}", pathWithNamespace);
            return null;
        }

        Repository repository = repositoryRepository
            .findByNameWithOwnerWithOrganization(pathWithNamespace)
            .orElse(null);
        if (repository == null) {
            log.debug("Skipped event: reason=repositoryNotFound, repoName={}", pathWithNamespace);
            return null;
        }

        Long scopeId = resolveScopeId(repository);
        return ProcessingContext.forWebhook(scopeId, repository, action);
    }

    private Long resolveScopeId(Repository repository) {
        if (repository.getOrganization() != null) {
            String orgLogin = repository.getOrganization().getLogin();
            Long scopeId = scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null);
            if (scopeId != null) {
                return scopeId;
            }
        }
        return scopeIdResolver.findScopeIdByRepositoryName(repository.getNameWithOwner()).orElse(null);
    }

    // ========================================================================
    // Sanitization
    // ========================================================================

    @Nullable
    protected String sanitize(@Nullable String input) {
        return PostgresStringUtils.sanitize(input);
    }
}
