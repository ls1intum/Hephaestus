package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.common.PostgresStringUtils;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * Base class for GitHub entity processors with shared helper methods.
 * <p>
 * Provides common functionality for finding or creating related entities
 * (users, labels, milestones) that is shared between Issue and PullRequest
 * processors.
 * <p>
 * <b>Design rationale:</b> These operations are identical across processors,
 * extracting them eliminates duplication and ensures consistent behavior.
 */
public abstract class BaseGitHubProcessor {

    private static final Logger log = LoggerFactory.getLogger(BaseGitHubProcessor.class);

    protected final UserRepository userRepository;
    protected final LabelRepository labelRepository;
    protected final MilestoneRepository milestoneRepository;

    protected BaseGitHubProcessor(
        UserRepository userRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository
    ) {
        this.userRepository = userRepository;
        this.labelRepository = labelRepository;
        this.milestoneRepository = milestoneRepository;
    }

    /**
     * Find an existing user or create a new one from the DTO.
     */
    @Nullable
    protected User findOrCreateUser(GitHubUserDTO dto) {
        if (dto == null) {
            return null;
        }
        Long userId = dto.getDatabaseId();
        if (userId == null) {
            return null;
        }
        return userRepository
            .findById(userId)
            .map(existingUser -> {
                // Update email if available and not already set
                if (dto.email() != null && existingUser.getEmail() == null) {
                    existingUser.setEmail(dto.email());
                    return userRepository.save(existingUser);
                }
                return existingUser;
            })
            .orElseGet(() -> {
                User user = new User();
                user.setId(userId);
                user.setLogin(dto.login());
                user.setAvatarUrl(dto.avatarUrl());
                // Use login as fallback for name if null (name is @NonNull)
                user.setName(dto.name() != null ? dto.name() : dto.login());
                // Set email if available from DTO
                user.setEmail(dto.email());
                // Set htmlUrl if available
                if (dto.htmlUrl() != null) {
                    user.setHtmlUrl(dto.htmlUrl());
                } else {
                    user.setHtmlUrl("https://github.com/" + dto.login());
                }
                // Set type from DTO (BOT, USER, or ORGANIZATION)
                user.setType(dto.getEffectiveType());
                return userRepository.save(user);
            });
    }

    /**
     * Find an existing label or create a new one from the DTO.
     * <p>
     * CRITICAL: Always looks up by repository + name FIRST because this is the unique
     * constraint (uq_label_repository_name). This handles the case where GraphQL sync
     * created a label with a deterministic ID, then a webhook arrives with the actual
     * GitHub databaseId. Looking up by the webhook's ID would miss the existing entity.
     * <p>
     * Uses atomic insertIfAbsent to prevent race conditions when concurrent threads
     * try to create the same label simultaneously.
     * <p>
     * NEVER changes the ID of an existing (managed) entity - Hibernate will throw
     * "identifier of an instance was altered" exception.
     */
    @Nullable
    protected Label findOrCreateLabel(GitHubLabelDTO dto, Repository repository) {
        if (dto == null || dto.name() == null || dto.name().isBlank()) {
            return null;
        }

        // ALWAYS check by unique key (repository_id + name) FIRST - this is the constraint we enforce.
        Optional<Label> existingOpt = labelRepository.findByRepositoryIdAndName(repository.getId(), dto.name());

        // Fall back to ID lookup if name lookup didn't find it (handles label renames)
        if (existingOpt.isEmpty() && dto.id() != null) {
            existingOpt = labelRepository.findById(dto.id());
        }

        if (existingOpt.isPresent()) {
            return existingOpt.get();
        }

        // Use atomic insert to prevent race conditions.
        // If another thread inserted first, this returns 0 and we fetch the winner.
        Long labelId = dto.id() != null ? dto.id() : generateDeterministicLabelId(repository.getId(), dto.name());
        int inserted = labelRepository.insertIfAbsent(labelId, dto.name(), dto.color(), repository.getId());

        if (inserted == 0) {
            // Another thread inserted first - fetch the winner
            return labelRepository.findByRepositoryIdAndName(repository.getId(), dto.name()).orElse(null);
        }

        // We inserted - fetch the entity to return a managed instance
        return labelRepository.findById(labelId).orElse(null);
    }

    /**
     * Generate a deterministic negative ID for labels created from GraphQL data.
     * <p>
     * Uses bit shifting to combine repo ID and label name hash without collision.
     * The formula repositoryId * 31 + labelName.hashCode() can produce collisions.
     * Uses negative values to avoid collision with real GitHub label IDs which are
     * always positive.
     */
    private Long generateDeterministicLabelId(Long repositoryId, String labelName) {
        long combined = (repositoryId << 32) | (labelName.hashCode() & 0xFFFFFFFFL);
        return -combined;
    }

    /**
     * Find an existing milestone or create a new one from the DTO.
     * <p>
     * CRITICAL: Always looks up by repository + number FIRST because this is the unique
     * constraint (uk_milestone_number_repository). This handles the case where GraphQL sync
     * created a milestone with a deterministic ID, then a webhook arrives with the actual
     * GitHub databaseId. Looking up by the webhook's ID would miss the existing entity.
     * <p>
     * Uses atomic insertIfAbsent to prevent race conditions when concurrent threads
     * try to create the same milestone simultaneously.
     * <p>
     * For GraphQL responses that don't include databaseId (id is null), we generate a
     * deterministic negative ID to avoid collision with real GitHub IDs.
     * <p>
     * NEVER changes the ID of an existing (managed) entity - Hibernate will throw
     * "identifier of an instance was altered" exception.
     */
    @Nullable
    protected Milestone findOrCreateMilestone(GitHubMilestoneDTO dto, Repository repository) {
        if (dto == null || dto.number() <= 0) {
            return null;
        }

        // ALWAYS check by unique key (repository_id + number) FIRST - this is the constraint we enforce.
        Optional<Milestone> existingOpt = milestoneRepository.findByNumberAndRepositoryId(
            dto.number(),
            repository.getId()
        );

        // Fall back to ID lookup if number lookup didn't find it (handles edge cases)
        if (existingOpt.isEmpty() && dto.id() != null) {
            existingOpt = milestoneRepository.findById(dto.id());
        }

        if (existingOpt.isPresent()) {
            return existingOpt.get();
        }

        // Use atomic insert to prevent race conditions.
        // If another thread inserted first, this returns 0 and we fetch the winner.
        Long milestoneId = dto.id() != null
            ? dto.id()
            : generateDeterministicMilestoneId(repository.getId(), dto.number());

        String title = dto.title() != null ? dto.title() : "Milestone " + dto.number();
        String htmlUrl = dto.htmlUrl() != null ? dto.htmlUrl() : "";
        String state = dto.state() != null ? dto.state().toUpperCase() : "OPEN";
        int openIssuesCount = dto.openIssuesCount() != null ? dto.openIssuesCount() : 0;
        int closedIssuesCount = dto.closedIssuesCount() != null ? dto.closedIssuesCount() : 0;

        int inserted = milestoneRepository.insertIfAbsent(
            milestoneId,
            dto.number(),
            title,
            dto.description(),
            state,
            htmlUrl,
            dto.dueOn(),
            openIssuesCount,
            closedIssuesCount,
            repository.getId(),
            dto.createdAt(),
            dto.updatedAt()
        );

        if (inserted == 0) {
            // Another thread inserted first - fetch the winner
            return milestoneRepository.findByNumberAndRepositoryId(dto.number(), repository.getId()).orElse(null);
        }

        // We inserted - fetch the entity to return a managed instance
        return milestoneRepository.findById(milestoneId).orElse(null);
    }

    /**
     * Generate a deterministic negative ID for milestones created from GraphQL data.
     * <p>
     * Uses bit shifting to combine repo ID and milestone number without collision.
     * Uses negative values to avoid collision with real GitHub milestone IDs which are
     * always positive.
     */
    private Long generateDeterministicMilestoneId(Long repositoryId, int milestoneNumber) {
        long combined = (repositoryId << 32) | (milestoneNumber & 0xFFFFFFFFL);
        return -combined;
    }

    /**
     * Converts a GitHub API milestone state string to Milestone.State enum.
     */
    private Milestone.State parseMilestoneState(String state) {
        if (state == null) {
            log.warn(
                "Milestone state is null, defaulting to OPEN. " +
                    "This may indicate missing data in webhook or GraphQL response."
            );
            return Milestone.State.OPEN;
        }
        return switch (state.toUpperCase()) {
            case "OPEN" -> Milestone.State.OPEN;
            case "CLOSED" -> Milestone.State.CLOSED;
            default -> {
                log.warn("Unknown milestone state '{}', defaulting to OPEN", state);
                yield Milestone.State.OPEN;
            }
        };
    }

    /**
     * Sanitize string for PostgreSQL storage (removes null characters).
     * Delegates to {@link PostgresStringUtils#sanitize(String)}.
     */
    @Nullable
    protected String sanitize(@Nullable String input) {
        return PostgresStringUtils.sanitize(input);
    }
}
