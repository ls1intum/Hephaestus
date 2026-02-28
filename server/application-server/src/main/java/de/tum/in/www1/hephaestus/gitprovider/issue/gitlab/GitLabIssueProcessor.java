package de.tum.in.www1.hephaestus.gitprovider.issue.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.BaseGitLabProcessor;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.dto.GitLabIssueEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitLab issues.
 * <p>
 * Handles conversion of GitLab issue data (from webhooks and GraphQL sync) to Issue entities.
 * Follows the same patterns as {@link de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor}.
 * <p>
 * Confidential issues are skipped entirely (not stored in the database).
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabIssueProcessor extends BaseGitLabProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitLabIssueProcessor.class);

    private final IssueRepository issueRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitLabIssueProcessor(
        IssueRepository issueRepository,
        UserRepository userRepository,
        LabelRepository labelRepository,
        RepositoryRepository repositoryRepository,
        ScopeIdResolver scopeIdResolver,
        RepositoryScopeFilter repositoryScopeFilter,
        GitLabProperties gitLabProperties,
        ApplicationEventPublisher eventPublisher
    ) {
        super(
            userRepository,
            labelRepository,
            repositoryRepository,
            scopeIdResolver,
            repositoryScopeFilter,
            gitLabProperties
        );
        this.issueRepository = issueRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitLab issue webhook event.
     */
    @Transactional
    @Nullable
    public Issue process(GitLabIssueEventDTO event, ProcessingContext context) {
        if (event.isConfidential()) {
            log.debug("Skipped confidential issue: iid={}", event.objectAttributes().iid());
            return null;
        }

        var attrs = event.objectAttributes();
        Issue issue = upsertIssue(
            attrs.id(),
            attrs.iid(),
            attrs.title(),
            attrs.description(),
            attrs.state(),
            attrs.url(),
            attrs.createdAt(),
            attrs.updatedAt(),
            attrs.closedAt(),
            event.user(),
            context.repository()
        );

        if (issue == null) return null;

        // Update relationships
        boolean changed = updateLabels(event.labels(), issue.getLabels(), context.repository());
        changed |= updateAssignees(event.assignees(), issue.getAssignees(), context.providerId());
        if (changed) {
            issue = issueRepository.save(issue);
        }

        return issue;
    }

    /**
     * Label data extracted from GraphQL response for sync processing.
     */
    public record SyncLabelData(String globalId, String title, @Nullable String color) {}

    /**
     * Assignee data extracted from GraphQL response for sync processing.
     */
    public record SyncAssigneeData(
        String globalId,
        String username,
        @Nullable String name,
        @Nullable String avatarUrl,
        @Nullable String webUrl
    ) {}

    /**
     * All data needed to sync a single GitLab issue from GraphQL.
     */
    public record SyncIssueData(
        String globalId,
        String iid,
        String title,
        @Nullable String description,
        String state,
        boolean confidential,
        String webUrl,
        @Nullable String createdAt,
        @Nullable String updatedAt,
        @Nullable String closedAt,
        @Nullable String authorGlobalId,
        @Nullable String authorUsername,
        @Nullable String authorName,
        @Nullable String authorAvatarUrl,
        @Nullable String authorWebUrl,
        int commentsCount,
        @Nullable List<SyncLabelData> syncLabels,
        @Nullable List<SyncAssigneeData> syncAssignees
    ) {}

    /**
     * Process a GitLab issue from GraphQL sync.
     * <p>
     * Labels and assignees are resolved and persisted within this method's
     * transaction boundary to avoid detached-entity issues.
     */
    @Transactional
    @Nullable
    public Issue processFromSync(SyncIssueData data, Repository repository) {
        if (data.confidential()) {
            log.debug("Skipped confidential issue from sync: iid={}", data.iid());
            return null;
        }

        long nativeId;
        try {
            nativeId = GitLabSyncConstants.extractNumericId(data.globalId());
        } catch (IllegalArgumentException e) {
            log.warn("Skipped issue processing: reason=invalidGlobalId, gid={}", data.globalId());
            return null;
        }

        int issueNumber;
        try {
            issueNumber = Integer.parseInt(data.iid());
        } catch (NumberFormatException e) {
            log.warn("Skipped issue processing: reason=invalidIid, iid={}", data.iid());
            return null;
        }

        Long providerId = repository.getProvider().getId();

        // Check if existing
        Optional<Issue> existingOpt = issueRepository.findByRepositoryIdAndNumber(repository.getId(), issueNumber);
        boolean isNew = existingOpt.isEmpty();

        // Resolve author
        User author = findOrCreateUser(
            data.authorGlobalId(),
            data.authorUsername(),
            data.authorName(),
            data.authorAvatarUrl(),
            data.authorWebUrl(),
            providerId
        );

        // State mapping
        Issue.State issueState = convertState(data.state());

        Instant now = Instant.now();
        issueRepository.upsertCore(
            nativeId,
            providerId,
            issueNumber,
            sanitize(data.title()),
            sanitize(data.description()),
            issueState.name(),
            null, // stateReason — not available in GitLab
            data.webUrl(),
            false, // locked — not in query
            parseGitLabTimestamp(data.closedAt()),
            data.commentsCount(),
            now,
            parseGitLabTimestamp(data.createdAt()),
            parseGitLabTimestamp(data.updatedAt()),
            author != null ? author.getId() : null,
            repository.getId(),
            null, // milestoneId
            null, // issueTypeId
            null, // parentIssueId
            null, // subIssuesTotal
            null, // subIssuesCompleted
            null // subIssuesPercentCompleted
        );

        Issue issue = issueRepository.findByRepositoryIdAndNumber(repository.getId(), issueNumber).orElse(null);

        if (issue == null) {
            return null;
        }

        issue.setProvider(repository.getProvider());

        // Resolve and persist labels/assignees within this transaction
        boolean changed = updateSyncLabels(data.syncLabels(), issue.getLabels(), repository);
        changed |= updateSyncAssignees(data.syncAssignees(), issue.getAssignees(), providerId);
        if (changed) {
            issue = issueRepository.save(issue);
        }

        if (isNew) {
            ProcessingContext ctx = ProcessingContext.forSync(null, repository);
            eventPublisher.publishEvent(
                new DomainEvent.IssueCreated(EventPayload.IssueData.from(issue), EventContext.from(ctx))
            );
            log.debug("Created issue from sync: issueId={}, iid={}", nativeId, data.iid());
        }

        return issue;
    }

    /**
     * Process a closed event.
     */
    @Transactional
    @Nullable
    public Issue processClosed(GitLabIssueEventDTO event, ProcessingContext context) {
        Issue issue = process(event, context);
        if (issue != null) {
            eventPublisher.publishEvent(
                new DomainEvent.IssueClosed(EventPayload.IssueData.from(issue), "completed", EventContext.from(context))
            );
            log.debug("Closed issue: issueId={}", issue.getId());
        }
        return issue;
    }

    /**
     * Process a reopened event.
     */
    @Transactional
    @Nullable
    public Issue processReopened(GitLabIssueEventDTO event, ProcessingContext context) {
        Issue issue = process(event, context);
        if (issue != null) {
            eventPublisher.publishEvent(
                new DomainEvent.IssueReopened(EventPayload.IssueData.from(issue), EventContext.from(context))
            );
            log.debug("Reopened issue: issueId={}", issue.getId());
        }
        return issue;
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    @Nullable
    private Issue upsertIssue(
        Long rawId,
        Integer iid,
        String title,
        @Nullable String description,
        String state,
        @Nullable String htmlUrl,
        @Nullable String createdAt,
        @Nullable String updatedAt,
        @Nullable String closedAt,
        @Nullable de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser authorDto,
        Repository repository
    ) {
        if (rawId == null || iid == null) {
            log.warn("Skipped issue processing: reason=missingIdOrIid");
            return null;
        }

        long nativeId = rawId;
        int issueNumber = iid;
        Long providerId = repository.getProvider().getId();

        Optional<Issue> existingOpt = issueRepository.findByRepositoryIdAndNumber(repository.getId(), issueNumber);
        boolean isNew = existingOpt.isEmpty();

        User author = findOrCreateUser(authorDto, providerId);
        Issue.State issueState = convertState(state);

        Instant now = Instant.now();
        issueRepository.upsertCore(
            nativeId,
            providerId,
            issueNumber,
            sanitize(title),
            sanitize(description),
            issueState.name(),
            null, // stateReason
            htmlUrl,
            false, // locked
            parseGitLabTimestamp(closedAt),
            0, // commentsCount — not available in webhook
            now,
            parseGitLabTimestamp(createdAt),
            parseGitLabTimestamp(updatedAt),
            author != null ? author.getId() : null,
            repository.getId(),
            null,
            null,
            null,
            null,
            null,
            null
        );

        Issue issue = issueRepository.findByRepositoryIdAndNumber(repository.getId(), issueNumber).orElse(null);

        if (issue != null) {
            issue.setProvider(repository.getProvider());

            if (isNew) {
                eventPublisher.publishEvent(
                    new DomainEvent.IssueCreated(
                        EventPayload.IssueData.from(issue),
                        EventContext.from(ProcessingContext.forSync(null, repository))
                    )
                );
                log.debug("Created issue: nativeId={}, iid={}", nativeId, issueNumber);
            }
        }

        return issue;
    }

    /**
     * Maps GitLab issue state string to Issue.State enum.
     */
    private static Issue.State convertState(@Nullable String state) {
        if (state == null) {
            return Issue.State.OPEN;
        }
        return switch (state.toLowerCase()) {
            case "closed" -> Issue.State.CLOSED;
            default -> Issue.State.OPEN;
        };
    }

    /**
     * Updates labels from GraphQL sync data within the current transaction.
     */
    private boolean updateSyncLabels(
        @Nullable List<SyncLabelData> syncLabels,
        java.util.Collection<Label> currentLabels,
        Repository repository
    ) {
        if (syncLabels == null) {
            return false;
        }

        Set<Label> newLabels = new HashSet<>();
        for (SyncLabelData data : syncLabels) {
            Label label = findOrCreateLabel(data.title(), data.color(), repository);
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

    /**
     * Updates assignees from GraphQL sync data within the current transaction.
     */
    private boolean updateSyncAssignees(
        @Nullable List<SyncAssigneeData> syncAssignees,
        Set<User> currentAssignees,
        Long providerId
    ) {
        if (syncAssignees == null) {
            return false;
        }

        Set<User> newAssignees = new HashSet<>();
        for (SyncAssigneeData data : syncAssignees) {
            User user = findOrCreateUser(
                data.globalId(),
                data.username(),
                data.name(),
                data.avatarUrl(),
                data.webUrl(),
                providerId
            );
            if (user != null) {
                newAssignees.add(user);
            }
        }

        if (!currentAssignees.equals(newAssignees)) {
            currentAssignees.clear();
            currentAssignees.addAll(newAssignees);
            return true;
        }
        return false;
    }
}
