package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommit;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.commit.github.dto.GitHubCommitDTO;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified processor for GitHub commits.
 * <p>
 * This service handles the conversion of GitHubCommitDTO to GitCommit entities,
 * persists them, and manages related entities.
 * It's used by both the GraphQL sync service and webhook handlers.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern (SHA is the primary key)</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubCommitProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubCommitProcessor.class);

    private final GitCommitRepository commitRepository;
    private final UserRepository userRepository;

    public GitHubCommitProcessor(GitCommitRepository commitRepository, UserRepository userRepository) {
        this.commitRepository = commitRepository;
        this.userRepository = userRepository;
    }

    /**
     * Process a GitHub commit DTO and persist it as a GitCommit entity.
     *
     * @param dto the commit DTO
     * @param context the processing context
     * @param refName the branch/tag reference (e.g., "refs/heads/main")
     * @param pushedAt the timestamp when the commit was pushed
     * @return the persisted GitCommit entity
     */
    @Transactional
    public GitCommit process(
        GitHubCommitDTO dto,
        ProcessingContext context,
        @Nullable String refName,
        @Nullable Instant pushedAt
    ) {
        String sha = dto.getSha();
        if (sha == null || sha.isEmpty()) {
            logger.warn("Commit DTO missing SHA, skipping");
            return null;
        }

        Repository repository = context.repository();
        Optional<GitCommit> existingOpt = commitRepository.findById(sha);

        GitCommit commit;
        boolean isNew = existingOpt.isEmpty();

        if (isNew) {
            commit = createCommit(dto, repository, refName, pushedAt);
            commit = commitRepository.save(commit);
            logger.debug("Created commit {} in {}", sha.substring(0, 7), sanitizeForLog(repository.getNameWithOwner()));
        } else {
            commit = existingOpt.get();
            Set<String> changedFields = updateCommit(dto, commit, refName, pushedAt);
            commit = commitRepository.save(commit);

            if (!changedFields.isEmpty()) {
                logger.debug(
                    "Updated commit {} in {} - changed: {}",
                    sha.substring(0, 7),
                    sanitizeForLog(repository.getNameWithOwner()),
                    changedFields
                );
            }
        }

        commit.setLastSyncAt(Instant.now());
        return commitRepository.save(commit);
    }

    // ==================== Entity Creation ====================

    private GitCommit createCommit(
        GitHubCommitDTO dto,
        Repository repository,
        @Nullable String refName,
        @Nullable Instant pushedAt
    ) {
        GitCommit commit = new GitCommit();
        commit.setSha(dto.getSha());
        commit.setAbbreviatedSha(dto.abbreviatedSha() != null ? dto.abbreviatedSha() : dto.getSha().substring(0, 7));
        commit.setMessage(sanitize(dto.message()));
        commit.setMessageHeadline(dto.messageHeadline());
        commit.setHtmlUrl(dto.htmlUrl());
        commit.setAuthoredAt(dto.authoredAt());
        commit.setCommittedAt(dto.committedAt());
        commit.setPushedAt(pushedAt);
        commit.setAuthorName(dto.authorName());
        commit.setAuthorEmail(dto.authorEmail());
        commit.setCommitterName(dto.committerName());
        commit.setCommitterEmail(dto.committerEmail());
        commit.setAdditions(dto.additions());
        commit.setDeletions(dto.deletions());
        commit.setChangedFiles(dto.changedFiles());
        commit.setRefName(refName);
        commit.setMergeCommit(dto.isMergeCommit());
        commit.setDistinct(dto.distinct());
        commit.setCreatedAt(Instant.now());
        commit.setUpdatedAt(Instant.now());
        commit.setRepository(repository);

        // Link to GitHub users if available
        if (dto.author() != null) {
            User author = findOrCreateUser(dto.author());
            commit.setAuthor(author);
        }
        if (dto.committer() != null) {
            User committer = findOrCreateUser(dto.committer());
            commit.setCommitter(committer);
        }

        return commit;
    }

    // ==================== Entity Update ====================

    private Set<String> updateCommit(
        GitHubCommitDTO dto,
        GitCommit commit,
        @Nullable String refName,
        @Nullable Instant pushedAt
    ) {
        Set<String> changedFields = new HashSet<>();

        // Message might be truncated in webhooks, update if we have a longer version
        if (
            dto.message() != null &&
            (commit.getMessage() == null || dto.message().length() > commit.getMessage().length())
        ) {
            commit.setMessage(sanitize(dto.message()));
            changedFields.add("message");
        }

        // Statistics might not be available initially (webhook doesn't include them)
        if (dto.additions() != null && !Objects.equals(commit.getAdditions(), dto.additions())) {
            commit.setAdditions(dto.additions());
            changedFields.add("additions");
        }
        if (dto.deletions() != null && !Objects.equals(commit.getDeletions(), dto.deletions())) {
            commit.setDeletions(dto.deletions());
            changedFields.add("deletions");
        }
        if (dto.changedFiles() != null && !Objects.equals(commit.getChangedFiles(), dto.changedFiles())) {
            commit.setChangedFiles(dto.changedFiles());
            changedFields.add("changedFiles");
        }

        // Update ref name if provided and different
        if (refName != null && !Objects.equals(commit.getRefName(), refName)) {
            commit.setRefName(refName);
            changedFields.add("refName");
        }

        // Update pushed at if provided and not set
        if (pushedAt != null && commit.getPushedAt() == null) {
            commit.setPushedAt(pushedAt);
            changedFields.add("pushedAt");
        }

        // Link users if they weren't linked before
        if (dto.author() != null && commit.getAuthor() == null) {
            User author = findOrCreateUser(dto.author());
            if (author != null) {
                commit.setAuthor(author);
                changedFields.add("author");
            }
        }
        if (dto.committer() != null && commit.getCommitter() == null) {
            User committer = findOrCreateUser(dto.committer());
            if (committer != null) {
                commit.setCommitter(committer);
                changedFields.add("committer");
            }
        }

        commit.setUpdatedAt(Instant.now());
        return changedFields;
    }

    // ==================== Helper Methods ====================

    @Nullable
    private User findOrCreateUser(GitHubUserDTO dto) {
        if (dto == null) {
            return null;
        }
        Long userId = dto.getDatabaseId();
        if (userId == null) {
            return null;
        }
        return userRepository
            .findById(userId)
            .orElseGet(() -> {
                User user = new User();
                user.setId(userId);
                user.setLogin(dto.login());
                user.setAvatarUrl(dto.avatarUrl());
                user.setName(dto.name() != null ? dto.name() : dto.login());
                return userRepository.save(user);
            });
    }

    @Nullable
    private String sanitize(@Nullable String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\u0000", "");
    }
}
