package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubPushMessageHandler extends GitHubMessageHandler<GHEventPayload.Push> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPushMessageHandler.class);

    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubPushCommitService pushCommitService;
    private final GitHubCommitSyncService commitSyncService;
    private final WorkspaceRepository workspaceRepository;

    public GitHubPushMessageHandler(
        GitHubRepositorySyncService repositorySyncService,
        GitHubPushCommitService pushCommitService,
        GitHubCommitSyncService commitSyncService,
        WorkspaceRepository workspaceRepository
    ) {
        super(GHEventPayload.Push.class);
        this.repositorySyncService = repositorySyncService;
        this.pushCommitService = pushCommitService;
        this.commitSyncService = commitSyncService;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    protected void handleEvent(GHEventPayload.Push eventPayload) {
        var repository = eventPayload.getRepository();
        if (repository != null) {
            repositorySyncService.processRepository(repository);
            logger.info(
                "Received push event for repo={} ref={} commits={}",
                repository.getFullName(),
                eventPayload.getRef(),
                eventPayload.getCommits().size()
            );
        } else {
            logger.warn("Push event without repository context");
        }

        pushCommitService.ingestPush(eventPayload);

        var hydratedRepository = resolveHydratedRepository(repository);
        if (hydratedRepository != null) {
            var commitShas = collectCommitShas(eventPayload);
            if (!commitShas.isEmpty()) {
                commitSyncService.syncCommits(hydratedRepository, commitShas);
            }
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PUSH;
    }

    private Set<String> collectCommitShas(GHEventPayload.Push payload) {
        var commits = payload.getCommits();
        if (commits == null || commits.isEmpty()) {
            return Set.of();
        }
        return commits
            .stream()
            .map(GHEventPayload.Push.PushCommit::getSha)
            .filter(sha -> sha != null && !sha.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private GHRepository resolveHydratedRepository(GHRepository payloadRepository) {
        if (payloadRepository == null) {
            return null;
        }
        var nameWithOwner = payloadRepository.getFullName();
        if (nameWithOwner == null || nameWithOwner.isBlank()) {
            logger.warn("Push payload repository missing full name; skipping commit enrichment");
            return null;
        }
        Workspace workspace = resolveWorkspace(nameWithOwner);
        if (workspace == null) {
            return null;
        }
        try {
            var hydrated = repositorySyncService.syncRepository(workspace.getId(), nameWithOwner);
            if (hydrated.isEmpty()) {
                logger.warn("Unable to hydrate repository {} for workspace {}; skipping commit enrichment", nameWithOwner, workspace.getId());
            }
            return hydrated.orElse(null);
        } catch (Exception e) {
            logger.warn("Failed to hydrate repository {} for enrichment: {}", nameWithOwner, e.getMessage());
            return null;
        }
    }

    private Workspace resolveWorkspace(String nameWithOwner) {
        return workspaceRepository
            .findByRepositoriesToMonitor_NameWithOwner(nameWithOwner)
            .orElseGet(() -> {
                logger.warn("No workspace found for repository {}; skipping commit enrichment", nameWithOwner);
                return null;
            });
    }
}
