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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

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

        List<RepositoryCollaborator> existingCollaborators = collaboratorRepository.findByRepository_Id(repositoryId);
        Map<Long, RepositoryCollaborator> existingByUserId = new HashMap<>(existingCollaborators.size());
        existingCollaborators.forEach(collaborator -> existingByUserId.put(collaborator.getUser().getId(), collaborator)
        );

        Set<Long> seenUserIds = new HashSet<>();

        for (GHUser ghUser : ghRepository.listCollaborators()) {
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

            RepositoryCollaborator collaborator = existingByUserId.remove(user.getId());
            if (collaborator == null) {
                collaborator = new RepositoryCollaborator(repository, user, permission);
            } else {
                collaborator.updatePermission(permission);
            }
            collaboratorRepository.save(collaborator);
            seenUserIds.add(user.getId());
        }

        int removedCount = 0;
        for (RepositoryCollaborator stale : existingByUserId.values()) {
            collaboratorRepository.delete(stale);
            removedCount++;
        }

        logger.info(
            "Repository collaborators synced: repositoryId={} total={} removed={}",
            repositoryId,
            seenUserIds.size(),
            removedCount
        );
    }

    private User upsertUser(GHUser ghUser) {
        long userId = ghUser.getId();
        User user = userRepository.findById(userId).orElseGet(User::new);
        user.setId(userId);
        userConverter.update(ghUser, user);
        try {
            // Flush to surface constraint violations immediately and minimize retry window
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Another thread inserted the user concurrently; load the existing row
            return userRepository.findById(userId).orElseThrow(() -> ex);
        }
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
}
