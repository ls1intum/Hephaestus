package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaboratorRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.HttpException;
import org.kohsuke.github.PagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubRepositoryCollaboratorSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubRepositoryCollaboratorSyncService.class);

    private final RepositoryCollaboratorRepository collaboratorRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final GitHubUserConverter userConverter;

    public GitHubRepositoryCollaboratorSyncService(
        RepositoryCollaboratorRepository collaboratorRepository,
        RepositoryRepository repositoryRepository,
        UserRepository userRepository,
        GitHubUserConverter userConverter
    ) {
        this.collaboratorRepository = collaboratorRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.userConverter = userConverter;
    }

    @Transactional
    public void syncCollaborators(GHRepository ghRepository) {
        if (ghRepository == null) {
            return;
        }

        long repositoryId = ghRepository.getId();
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            logger.debug("Skipping collaborator sync for repositoryId={} because it is unknown locally", repositoryId);
            return;
        }

        Set<Long> seenUserIds = new HashSet<>();

        PagedIterable<GHUser> collaborators = ghRepository.listCollaborators().withPageSize(100);

        try {
            for (GHUser ghUser : collaborators) {
                if (ghUser == null) {
                    continue;
                }
                User user = upsertUser(ghUser);
                GHPermissionType permissionType;
                try {
                    permissionType = ghRepository.getPermission(ghUser);
                } catch (IOException permissionError) {
                    logger.warn(
                        "Failed to resolve permission for repositoryId={} userId={}: {}",
                        repositoryId,
                        ghUser.getId(),
                        permissionError.getMessage()
                    );
                    continue;
                }

                RepositoryCollaborator.Permission permission = mapPermission(permissionType);
                if (permission == RepositoryCollaborator.Permission.UNKNOWN) {
                    logger.debug(
                        "Hub4j reported permission={} for repositoryId={} userId={} – stored as UNKNOWN",
                        permissionType,
                        repositoryId,
                        user.getId()
                    );
                }

                // Use atomic upsert to avoid race conditions with concurrent syncs
                // Note: upsert() clears the persistence context (clearAutomatically=true)
                collaboratorRepository.upsert(repositoryId, user.getId(), permission.name());
                seenUserIds.add(user.getId());
            }
        } catch (GHException ghException) {
            handleCollaboratorListingFailure(repositoryId, ghException);
            return;
        }

        // Re-fetch existing collaborators AFTER all upserts complete
        // This is necessary because upsert() uses clearAutomatically=true which evicts entities
        List<RepositoryCollaborator> currentCollaborators = collaboratorRepository.findByRepository_Id(repositoryId);

        // Remove collaborators that are no longer in the GitHub list
        int removedCount = 0;
        for (RepositoryCollaborator existing : currentCollaborators) {
            if (!seenUserIds.contains(existing.getUser().getId())) {
                collaboratorRepository.delete(existing);
                removedCount++;
            }
        }

        logger.info(
            "Repository collaborators synced: repositoryId={} total={} removed={}",
            repositoryId,
            seenUserIds.size(),
            removedCount
        );
    }

    private User upsertUser(GHUser ghUser) {
        User user = userRepository.findById(ghUser.getId()).orElseGet(User::new);
        user.setId(ghUser.getId());
        userConverter.update(ghUser, user);
        return userRepository.save(user);
    }

    private RepositoryCollaborator.Permission mapPermission(GHPermissionType permissionType) {
        if (permissionType == null) {
            return RepositoryCollaborator.Permission.UNKNOWN;
        }
        return switch (permissionType) {
            case ADMIN -> RepositoryCollaborator.Permission.ADMIN;
            case WRITE -> RepositoryCollaborator.Permission.WRITE;
            case READ -> RepositoryCollaborator.Permission.READ;
            default -> RepositoryCollaborator.Permission.UNKNOWN;
        };
    }

    private void handleCollaboratorListingFailure(long repositoryId, GHException exception) {
        HttpException httpException = unwrapHttpException(exception);
        if (httpException != null) {
            int responseCode = httpException.getResponseCode();
            if (responseCode == 401) {
                logger.warn(
                    "Skipping collaborator sync for repositoryId={} due to invalid/expired credentials (HTTP 401).",
                    repositoryId
                );
                return;
            }
            if (responseCode == 403) {
                logger.warn(
                    "Skipping collaborator sync for repositoryId={} due to insufficient GitHub App permissions.",
                    repositoryId
                );
                return;
            }
            if (responseCode == 404) {
                logger.warn("Repository {} no longer exists while syncing collaborators.", repositoryId);
                return;
            }
            logger.warn(
                "Failed to list collaborators for repositoryId={} (HTTP {}): {}",
                repositoryId,
                responseCode,
                httpException.getMessage()
            );
            return;
        }

        logger.warn("Failed to list collaborators for repositoryId={}: {}", repositoryId, exception.getMessage());
    }

    private HttpException unwrapHttpException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpException httpException) {
                return httpException;
            }
            current = current.getCause();
        }
        return null;
    }
}
