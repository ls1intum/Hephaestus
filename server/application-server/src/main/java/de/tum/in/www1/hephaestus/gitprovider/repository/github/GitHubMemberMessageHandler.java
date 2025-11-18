package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaboratorRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import java.util.Locale;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHMemberChanges;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubMemberMessageHandler extends GitHubMessageHandler<GHEventPayload.Member> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMemberMessageHandler.class);

    private final RepositoryRepository repositoryRepository;
    private final RepositoryCollaboratorRepository collaboratorRepository;
    private final GitHubRepositoryConverter repositoryConverter;
    private final UserRepository userRepository;
    private final GitHubUserConverter userConverter;

    public GitHubMemberMessageHandler(
        RepositoryRepository repositoryRepository,
        RepositoryCollaboratorRepository collaboratorRepository,
        GitHubRepositoryConverter repositoryConverter,
        UserRepository userRepository,
        GitHubUserConverter userConverter
    ) {
        super(GHEventPayload.Member.class);
        this.repositoryRepository = repositoryRepository;
        this.collaboratorRepository = collaboratorRepository;
        this.repositoryConverter = repositoryConverter;
        this.userRepository = userRepository;
        this.userConverter = userConverter;
    }

    // Intentionally no @Override: javac cannot match the bridge method generated for GHEventPayload.Member.
    protected void handleEvent(GHEventPayload.Member payload) {
        String action = payload.getAction() == null ? "" : payload.getAction().toLowerCase(Locale.ROOT);
        GHRepository ghRepository = payload.getRepository();
        GHUser ghUser = payload.getMember();

        if (ghRepository == null || ghUser == null) {
            logger.warn("member event missing repository or user (action={})", action);
            return;
        }

        Repository repository = upsertRepository(ghRepository);
        User user = upsertUser(ghUser);

        RepositoryCollaborator.Id id = new RepositoryCollaborator.Id(repository.getId(), user.getId());
        RepositoryCollaborator existing = collaboratorRepository.findById(id).orElse(null);

        if ("removed".equals(action)) {
            if (existing != null) {
                collaboratorRepository.delete(existing);
                logger.info("Removed collaborator userId={} from repositoryId={}", user.getId(), repository.getId());
            }
            return;
        }

        RepositoryCollaborator.Permission permission = resolvePermission(payload.getChanges(), existing);
        RepositoryCollaborator collaborator = existing;

        if (collaborator == null) {
            collaborator = new RepositoryCollaborator(repository, user, permission);
        } else {
            collaborator.updatePermission(permission);
        }

        collaboratorRepository.save(collaborator);
        logger.info(
            "Upserted collaborator userId={} repositoryId={} permission={} (action={})",
            user.getId(),
            repository.getId(),
            collaborator.getPermission(),
            action
        );
    }

    private Repository upsertRepository(GHRepository ghRepository) {
        Repository repository = repositoryRepository.findById(ghRepository.getId()).orElseGet(Repository::new);
        repository.setId(ghRepository.getId());
        repository = repositoryConverter.update(ghRepository, repository);
        return repositoryRepository.save(repository);
    }

    private User upsertUser(GHUser ghUser) {
        User user = userRepository.findById(ghUser.getId()).orElseGet(User::new);
        user.setId(ghUser.getId());
        user = userConverter.update(ghUser, user);
        return userRepository.save(user);
    }

    private RepositoryCollaborator.Permission resolvePermission(
        GHMemberChanges changes,
        RepositoryCollaborator existing
    ) {
        if (changes != null && changes.getPermission() != null) {
            var to = RepositoryCollaborator.Permission.fromGitHubValue(changes.getPermission().getTo());
            if (to != RepositoryCollaborator.Permission.UNKNOWN) {
                return to;
            }
            var from = RepositoryCollaborator.Permission.fromGitHubValue(changes.getPermission().getFrom());
            if (from != RepositoryCollaborator.Permission.UNKNOWN) {
                return from;
            }
        }
        if (changes != null && changes.getRoleName() != null) {
            var roleName = RepositoryCollaborator.Permission.fromGitHubValue(changes.getRoleName().getTo());
            if (roleName != RepositoryCollaborator.Permission.UNKNOWN) {
                return roleName;
            }
        }
        return existing != null ? existing.getPermission() : RepositoryCollaborator.Permission.UNKNOWN;
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.MEMBER;
    }
}
