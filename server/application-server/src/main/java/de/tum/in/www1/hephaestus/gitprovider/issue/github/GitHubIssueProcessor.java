package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueTypeDTO;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.github.GitHubIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified processor for GitHub issues.
 * <p>
 * This service handles the conversion of GitHubIssueDTO to Issue entities,
 * persists them, and publishes appropriate domain events. It's used by both
 * the GraphQL sync service and webhook handlers.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Domain events published for reactive feature development</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubIssueProcessor extends BaseGitHubProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueProcessor.class);

    private final IssueRepository issueRepository;
    private final OrganizationRepository organizationRepository;
    private final GitHubIssueTypeSyncService issueTypeSyncService;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubIssueProcessor(
        IssueRepository issueRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        UserRepository userRepository,
        OrganizationRepository organizationRepository,
        GitHubIssueTypeSyncService issueTypeSyncService,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, labelRepository, milestoneRepository);
        this.issueRepository = issueRepository;
        this.organizationRepository = organizationRepository;
        this.issueTypeSyncService = issueTypeSyncService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub issue DTO and persist it as an Issue entity.
     * Publishes appropriate domain events based on what changed.
     */
    @Transactional
    public Issue process(GitHubIssueDTO dto, ProcessingContext context) {
        // Use getDatabaseId() which falls back to id for webhook payloads
        Long dbId = dto.getDatabaseId();
        if (dbId == null) {
            logger.warn("Issue DTO missing databaseId, skipping");
            return null;
        }

        Repository repository = context.repository();
        Optional<Issue> existingOpt = issueRepository.findById(dbId);

        Issue issue;
        boolean isNew = existingOpt.isEmpty();

        if (isNew) {
            issue = createIssue(dto, repository);
            issue = issueRepository.save(issue);
            eventPublisher.publishEvent(
                new DomainEvent.IssueCreated(EventPayload.IssueData.from(issue), EventContext.from(context))
            );
            logger.debug("Created issue #{} in {}", dto.number(), context.repository().getNameWithOwner());
        } else {
            issue = existingOpt.get();
            Set<String> changedFields = updateIssue(dto, issue, repository);
            issue = issueRepository.save(issue);

            if (!changedFields.isEmpty()) {
                eventPublisher.publishEvent(
                    new DomainEvent.IssueUpdated(
                        EventPayload.IssueData.from(issue),
                        changedFields,
                        EventContext.from(context)
                    )
                );
                logger.debug(
                    "Updated issue #{} in {} - changed: {}",
                    dto.number(),
                    context.repository().getNameWithOwner(),
                    changedFields
                );
            }
        }

        return issue;
    }

    /**
     * Process a typed event (issue type assigned).
     */
    @Transactional
    public Issue processTyped(
        GitHubIssueDTO issueDto,
        GitHubIssueTypeDTO typeDto,
        String orgLogin,
        ProcessingContext context
    ) {
        Issue issue = process(issueDto, context);

        if (typeDto != null) {
            IssueType issueType = findOrCreateIssueType(typeDto, orgLogin);
            if (issueType != null) {
                issue.setIssueType(issueType);
                issue = issueRepository.save(issue);
                eventPublisher.publishEvent(
                    new DomainEvent.IssueTyped(
                        EventPayload.IssueData.from(issue),
                        EventPayload.IssueTypeData.from(issueType),
                        EventContext.from(context)
                    )
                );
                logger.info("Issue #{} typed as '{}'", issue.getNumber(), issueType.getName());
            }
        }

        return issue;
    }

    /**
     * Process an untyped event (issue type removed).
     */
    @Transactional
    public Issue processUntyped(GitHubIssueDTO issueDto, ProcessingContext context) {
        Issue issue = process(issueDto, context);

        IssueType previousType = issue.getIssueType();
        if (previousType != null) {
            issue.setIssueType(null);
            issue = issueRepository.save(issue);
            eventPublisher.publishEvent(
                new DomainEvent.IssueUntyped(
                    EventPayload.IssueData.from(issue),
                    EventPayload.IssueTypeData.from(previousType),
                    EventContext.from(context)
                )
            );
            logger.info("Issue #{} untyped (was '{}')", issue.getNumber(), previousType.getName());
        }

        return issue;
    }

    /**
     * Process a closed event.
     */
    @Transactional
    public Issue processClosed(GitHubIssueDTO issueDto, ProcessingContext context) {
        Issue issue = process(issueDto, context);
        String stateReason = issueDto.stateReason() != null ? issueDto.stateReason() : "completed";
        eventPublisher.publishEvent(
            new DomainEvent.IssueClosed(EventPayload.IssueData.from(issue), stateReason, EventContext.from(context))
        );
        logger.debug("Issue #{} closed: {}", issue.getNumber(), stateReason);
        return issue;
    }

    /**
     * Process a reopened event.
     */
    @Transactional
    public Issue processReopened(GitHubIssueDTO issueDto, ProcessingContext context) {
        Issue issue = process(issueDto, context);
        eventPublisher.publishEvent(
            new DomainEvent.IssueReopened(EventPayload.IssueData.from(issue), EventContext.from(context))
        );
        logger.debug("Issue #{} reopened", issue.getNumber());
        return issue;
    }

    /**
     * Process a labeled event.
     */
    @Transactional
    public Issue processLabeled(GitHubIssueDTO issueDto, GitHubLabelDTO labelDto, ProcessingContext context) {
        Issue issue = process(issueDto, context);
        Label label = findOrCreateLabel(labelDto, context.repository());
        if (label != null) {
            eventPublisher.publishEvent(
                new DomainEvent.IssueLabeled(
                    EventPayload.IssueData.from(issue),
                    EventPayload.LabelData.from(label),
                    EventContext.from(context)
                )
            );
            logger.debug("Issue #{} labeled: {}", issue.getNumber(), label.getName());
        }
        return issue;
    }

    /**
     * Process an unlabeled event.
     */
    @Transactional
    public Issue processUnlabeled(GitHubIssueDTO issueDto, GitHubLabelDTO labelDto, ProcessingContext context) {
        Issue issue = process(issueDto, context);
        Label label = findOrCreateLabel(labelDto, context.repository());
        if (label != null) {
            eventPublisher.publishEvent(
                new DomainEvent.IssueUnlabeled(
                    EventPayload.IssueData.from(issue),
                    EventPayload.LabelData.from(label),
                    EventContext.from(context)
                )
            );
            logger.debug("Issue #{} unlabeled: {}", issue.getNumber(), label.getName());
        }
        return issue;
    }

    /**
     * Process a deleted event.
     * Publishes IssueDeleted domain event.
     */
    @Transactional
    public void processDeleted(GitHubIssueDTO issueDto, ProcessingContext context) {
        Long dbId = issueDto.getDatabaseId();
        if (dbId != null) {
            issueRepository.deleteById(dbId);
            eventPublisher.publishEvent(new DomainEvent.IssueDeleted(dbId, EventContext.from(context)));
            logger.info("Deleted issue with id {}", dbId);
        }
    }

    // ==================== Entity Creation ====================

    private Issue createIssue(GitHubIssueDTO dto, Repository repository) {
        Issue issue = new Issue();
        issue.setId(dto.getDatabaseId());
        issue.setNumber(dto.number());
        issue.setTitle(sanitize(dto.title()));
        issue.setBody(sanitize(dto.body()));
        issue.setState(convertState(dto.state()));
        issue.setStateReason(convertStateReason(dto.stateReason()));
        issue.setHtmlUrl(dto.htmlUrl());
        issue.setCommentsCount(dto.commentsCount());
        issue.setCreatedAt(dto.createdAt());
        issue.setUpdatedAt(dto.updatedAt());
        issue.setClosedAt(dto.closedAt());
        issue.setRepository(repository);

        // Author
        if (dto.author() != null) {
            User author = findOrCreateUser(dto.author());
            issue.setAuthor(author);
        }

        // Assignees
        if (dto.assignees() != null) {
            for (GitHubUserDTO assigneeDto : dto.assignees()) {
                User assignee = findOrCreateUser(assigneeDto);
                if (assignee != null) {
                    issue.getAssignees().add(assignee);
                }
            }
        }

        // Labels
        if (dto.labels() != null) {
            for (GitHubLabelDTO labelDto : dto.labels()) {
                Label label = findOrCreateLabel(labelDto, repository);
                if (label != null) {
                    issue.getLabels().add(label);
                }
            }
        }

        // Milestone
        if (dto.milestone() != null) {
            Milestone milestone = findOrCreateMilestone(dto.milestone(), repository);
            issue.setMilestone(milestone);
        }

        // Issue type
        if (dto.issueType() != null && repository.getOrganization() != null) {
            IssueType issueType = findOrCreateIssueType(dto.issueType(), repository.getOrganization().getLogin());
            issue.setIssueType(issueType);
        }

        return issue;
    }

    // ==================== Entity Update ====================

    private Set<String> updateIssue(GitHubIssueDTO dto, Issue issue, Repository repository) {
        Set<String> changedFields = new HashSet<>();

        // Title
        if (!Objects.equals(issue.getTitle(), dto.title())) {
            issue.setTitle(sanitize(dto.title()));
            changedFields.add("title");
        }

        // Body
        if (!Objects.equals(issue.getBody(), dto.body())) {
            issue.setBody(sanitize(dto.body()));
            changedFields.add("body");
        }

        // State
        Issue.State newState = convertState(dto.state());
        if (issue.getState() != newState) {
            issue.setState(newState);
            changedFields.add("state");
        }

        // State reason
        Issue.StateReason newStateReason = convertStateReason(dto.stateReason());
        if (!Objects.equals(issue.getStateReason(), newStateReason)) {
            issue.setStateReason(newStateReason);
            changedFields.add("stateReason");
        }

        // Comments count
        Integer dtoCommentsCount = dto.commentsCount();
        int newCommentsCount = dtoCommentsCount != null ? dtoCommentsCount : 0;
        if (issue.getCommentsCount() != newCommentsCount) {
            issue.setCommentsCount(newCommentsCount);
            changedFields.add("commentsCount");
        }

        // Timestamps
        if (!Objects.equals(issue.getUpdatedAt(), dto.updatedAt())) {
            issue.setUpdatedAt(dto.updatedAt());
        }
        if (!Objects.equals(issue.getClosedAt(), dto.closedAt())) {
            issue.setClosedAt(dto.closedAt());
            changedFields.add("closedAt");
        }

        // Milestone
        if (dto.milestone() != null) {
            Milestone milestone = findOrCreateMilestone(dto.milestone(), repository);
            if (!Objects.equals(issue.getMilestone(), milestone)) {
                issue.setMilestone(milestone);
                changedFields.add("milestone");
            }
        } else if (issue.getMilestone() != null) {
            issue.setMilestone(null);
            changedFields.add("milestone");
        }

        // Issue type
        if (dto.issueType() != null && repository.getOrganization() != null) {
            IssueType issueType = findOrCreateIssueType(dto.issueType(), repository.getOrganization().getLogin());
            if (!Objects.equals(issue.getIssueType(), issueType)) {
                issue.setIssueType(issueType);
                changedFields.add("issueType");
            }
        }

        return changedFields;
    }

    /**
     * Converts a GitHub API state string to Issue.State enum.
     */
    private Issue.State convertState(String state) {
        if (state == null) {
            return Issue.State.OPEN;
        }
        return switch (state.toUpperCase()) {
            case "CLOSED", "MERGED" -> Issue.State.CLOSED;
            default -> Issue.State.OPEN;
        };
    }

    /**
     * Converts a GitHub API state reason string to Issue.StateReason enum.
     * Returns UNKNOWN for unrecognized values to preserve data integrity.
     */
    @Nullable
    private Issue.StateReason convertStateReason(String stateReason) {
        if (stateReason == null) {
            return null;
        }
        return switch (stateReason.toUpperCase()) {
            case "COMPLETED" -> Issue.StateReason.COMPLETED;
            case "REOPENED" -> Issue.StateReason.REOPENED;
            case "NOT_PLANNED" -> Issue.StateReason.NOT_PLANNED;
            default -> Issue.StateReason.UNKNOWN;
        };
    }

    @Nullable
    private IssueType findOrCreateIssueType(GitHubIssueTypeDTO dto, String orgLogin) {
        if (dto == null || dto.nodeId() == null) {
            return null;
        }

        return issueTypeSyncService
            .findByNodeId(dto.nodeId())
            .orElseGet(() -> {
                var orgOpt = organizationRepository.findByLoginIgnoreCase(orgLogin);
                if (orgOpt.isEmpty()) {
                    logger.warn("Cannot create issue type '{}' - org '{}' not found", dto.name(), orgLogin);
                    return null;
                }
                return issueTypeSyncService.findOrCreateFromWebhook(
                    dto.nodeId(),
                    dto.name(),
                    dto.description(),
                    dto.color(),
                    dto.isEnabled() != null ? dto.isEnabled() : true,
                    orgOpt.get()
                );
            });
    }
}
