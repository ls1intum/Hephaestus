package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.commit.github.dto.GitHubCommitDTO;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub Commits.
 * <p>
 * This service handles the conversion of GitHubCommitDTO to Commit entities.
 * <p>
 * <b>Key Differences from Other Processors:</b>
 * <ul>
 * <li>Uses SHA (String) as primary key instead of Long database ID</li>
 * <li>Author/committer may be null if the git email doesn't map to a GitHub user</li>
 * <li>Commits are repository-scoped and identified by (sha, repository_id)</li>
 * </ul>
 */
@Service
public class GitHubCommitProcessor extends BaseGitHubProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubCommitProcessor.class);

    private final CommitRepository commitRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubCommitProcessor(
        UserRepository userRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        CommitRepository commitRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, labelRepository, milestoneRepository);
        this.commitRepository = commitRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub commit DTO and persist it as a Commit entity.
     *
     * @param dto     the commit DTO
     * @param context the processing context with repository information
     * @return the created or updated Commit entity, or null if processing failed
     */
    @Transactional
    public Commit process(GitHubCommitDTO dto, ProcessingContext context) {
        String sha = dto.sha();
        if (sha == null || sha.isBlank()) {
            log.warn("Skipped commit processing: reason=missingSha");
            return null;
        }

        Repository repository = context.repository();

        // Resolve GitHub users (may be null if git email doesn't map to GitHub user)
        User author = dto.author() != null ? findOrCreateUser(dto.author()) : null;
        User committer = dto.committer() != null ? findOrCreateUser(dto.committer()) : null;

        // Prepare commit data - sanitize to handle PostgreSQL null bytes
        String message = sanitize(
            dto.messageHeadline() != null ? dto.messageHeadline() : extractHeadline(dto.message())
        );
        String messageBody = sanitize(dto.messageBody());
        Instant lastSyncAt = Instant.now();

        // Atomic upsert - handles race conditions
        commitRepository.upsertCore(
            sha,
            repository.getId(),
            message,
            messageBody,
            dto.htmlUrl(),
            dto.authoredAt(),
            dto.committedAt(),
            dto.additions(),
            dto.deletions(),
            dto.changedFiles() != null ? dto.changedFiles() : 0,
            lastSyncAt,
            author != null ? author.getId() : null,
            committer != null ? committer.getId() : null
        );

        // Fetch the managed entity after upsert
        Commit commit = commitRepository
            .findByShaAndRepositoryId(sha, repository.getId())
            .orElseThrow(() -> new IllegalStateException("Commit not found after upsert: sha=" + sha));

        log.debug("Upserted commit: sha={}, repo={}", sha.substring(0, 7), repository.getNameWithOwner());

        // Publish domain event
        EventContext eventContext = EventContext.from(context);
        EventPayload.CommitData commitData = EventPayload.CommitData.from(commit);
        eventPublisher.publishEvent(new DomainEvent.CommitCreated(commitData, eventContext));

        return commit;
    }

    /**
     * Extract the first line of a commit message as the headline.
     */
    private String extractHeadline(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        int newlineIndex = message.indexOf('\n');
        if (newlineIndex > 0) {
            return message.substring(0, newlineIndex).trim();
        }
        return message.trim();
    }
}
