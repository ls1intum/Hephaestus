package de.tum.in.www1.hephaestus.gitprovider.common.github;

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
                // Default type to USER
                user.setType(User.Type.USER);
                return userRepository.save(user);
            });
    }

    /**
     * Find an existing label or create a new one from the DTO.
     * <p>
     * Tries ID-based lookup first (webhooks provide ID), then falls back to
     * name-based lookup (GraphQL doesn't provide numeric ID). If creating a new
     * label without an ID, generates a deterministic negative ID to avoid
     * collision with real GitHub IDs.
     */
    @Nullable
    protected Label findOrCreateLabel(GitHubLabelDTO dto, Repository repository) {
        if (dto == null || dto.name() == null || dto.name().isBlank()) {
            return null;
        }

        // Try ID-based lookup first (webhooks provide ID), then name-based (GraphQL doesn't)
        Optional<Label> existingOpt;
        if (dto.id() != null) {
            existingOpt = labelRepository.findById(dto.id());
        } else {
            existingOpt = labelRepository.findByRepositoryIdAndName(repository.getId(), dto.name());
        }

        return existingOpt.orElseGet(() -> {
            Label label = new Label();
            // Use provided ID or generate deterministic one
            label.setId(dto.id() != null ? dto.id() : generateDeterministicLabelId(repository.getId(), dto.name()));
            label.setName(dto.name());
            label.setColor(dto.color());
            label.setRepository(repository);
            return labelRepository.save(label);
        });
    }

    /**
     * Generate a deterministic negative ID for labels created from GraphQL data.
     * <p>
     * Uses negative values to avoid collision with real GitHub label IDs which are
     * always positive.
     */
    private Long generateDeterministicLabelId(Long repositoryId, String labelName) {
        long hash = repositoryId * 31L + labelName.hashCode();
        return -Math.abs(hash);
    }

    /**
     * Find an existing milestone or create a new one from the DTO.
     */
    @Nullable
    protected Milestone findOrCreateMilestone(GitHubMilestoneDTO dto, Repository repository) {
        if (dto == null || dto.id() == null) {
            return null;
        }
        return milestoneRepository
            .findById(dto.id())
            .orElseGet(() -> {
                Milestone milestone = new Milestone();
                milestone.setId(dto.id());
                milestone.setNumber(dto.number());
                milestone.setTitle(dto.title());
                milestone.setDueOn(dto.dueOn());
                milestone.setRepository(repository);
                return milestoneRepository.save(milestone);
            });
    }

    /**
     * Sanitize string for PostgreSQL storage (removes null characters).
     */
    @Nullable
    protected String sanitize(@Nullable String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\u0000", "");
    }
}
