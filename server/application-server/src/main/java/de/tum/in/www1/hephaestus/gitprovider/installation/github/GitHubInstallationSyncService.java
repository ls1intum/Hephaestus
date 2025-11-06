package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.installation.Installation;
import de.tum.in.www1.hephaestus.gitprovider.installation.Installation.LifecycleState;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepository;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepositoryLink;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepositoryLinkRepository;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTarget;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTargetRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHEventPayloadInstallationTarget;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class GitHubInstallationSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationSyncService.class);

    private final InstallationRepository installationRepository;
    private final InstallationRepositoryLinkRepository linkRepository;
    private final InstallationTargetRepository targetRepository;
    private final GitHubInstallationConverter installationConverter;
    private final GitHubInstallationTargetConverter installationTargetConverter;
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubUserConverter userConverter;
    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;

    public GitHubInstallationSyncService(
        InstallationRepository installationRepository,
        InstallationRepositoryLinkRepository linkRepository,
        InstallationTargetRepository targetRepository,
        GitHubInstallationConverter installationConverter,
        GitHubInstallationTargetConverter installationTargetConverter,
        GitHubRepositorySyncService repositorySyncService,
        GitHubUserConverter userConverter,
        UserRepository userRepository,
        @Lazy WorkspaceService workspaceService
    ) {
        this.installationRepository = installationRepository;
        this.linkRepository = linkRepository;
        this.targetRepository = targetRepository;
        this.installationConverter = installationConverter;
        this.installationTargetConverter = installationTargetConverter;
        this.repositorySyncService = repositorySyncService;
        this.userConverter = userConverter;
        this.userRepository = userRepository;
        this.workspaceService = workspaceService;
    }

    @Transactional
    public Installation synchronizeInstallationSnapshot(GHAppInstallation ghInstallation) {
        if (ghInstallation == null) {
            logger.warn("Skipping installation snapshot because payload is missing installation data.");
            return null;
        }

        var installation = upsertInstallation(ghInstallation);
        installation.setLastWebhookReceivedAt(Instant.now());

        var account = safeAccount(ghInstallation);
        var target = upsertTarget(account);
        installation.setTarget(target);
        installation.setTargetGithubId(ghInstallation.getTargetId());
        installation.setTargetType(target != null ? target.getType() : installation.getTargetType());

        upsertSuspendedBy(installation, safeSuspendedBy(ghInstallation));

        var updatedAt = safeUpdatedAt(ghInstallation);
        var lifecycleAction = ghInstallation.getSuspendedAt() != null ? "suspend" : "unsuspend";
        updateLifecycleState(installation, lifecycleAction, updatedAt, ghInstallation.getSuspendedAt());

        return installationRepository.save(installation);
    }

    @Transactional
    public Installation handleInstallationEvent(GHEventPayload.Installation payload) {
        var ghInstallation = payload.getInstallation();
        if (ghInstallation == null) {
            logger.warn("Installation payload without installation data: action={}", payload.getAction());
            return null;
        }

        var installation = upsertInstallation(ghInstallation);
        installation.setLastWebhookReceivedAt(Instant.now());

        var target = upsertTarget(ghInstallation.getAccount());
        installation.setTarget(target);
        installation.setTargetGithubId(ghInstallation.getTargetId());
        installation.setTargetType(target != null ? target.getType() : installation.getTargetType());

        upsertSuspendedBy(installation, ghInstallation.getSuspendedBy());
        var updatedAt = safeUpdatedAt(ghInstallation);
        updateLifecycleState(installation, payload.getAction(), updatedAt, ghInstallation.getSuspendedAt());

        var persistedInstallation = installationRepository.save(installation);

        synchronizeRepositoriesSnapshot(
            persistedInstallation,
            extractRawRepositories(payload),
            updatedAt,
            payload.getAction()
        );

        var reconciled = installationRepository.save(persistedInstallation);
        workspaceService.reconcileRepositoriesForInstallation(reconciled);
        return reconciled;
    }

    @Transactional
    public Installation handleInstallationRepositoriesEvent(GHEventPayload.InstallationRepositories payload) {
        var ghInstallation = payload.getInstallation();
        if (ghInstallation == null) {
            logger.warn("installation_repositories payload missing installation");
            return null;
        }

        var installation = upsertInstallation(ghInstallation);
        installation.setLastRepositoriesSyncAt(Instant.now());

        var payloadSelection = payload.getRepositorySelection();
        String installationSelectionSymbol = null;
        var ghSelection = ghInstallation.getRepositorySelection();
        installationSelectionSymbol = ghSelection != null ? ghSelection.name() : null;

        logger.info(
            "installation_repositories selection signals: payload={}, installation={}, resolvedBefore={}",
            payloadSelection,
            installationSelectionSymbol,
            installation.getRepositorySelection()
        );

        var selection = Installation.RepositorySelection.fromSymbol(payloadSelection);
        if (selection != Installation.RepositorySelection.UNKNOWN) {
            installation.setRepositorySelection(selection);
        }

        var updatedAt = safeUpdatedAt(ghInstallation);

        var persistedInstallation = installationRepository.save(installation);

        if (payload.getRepositoriesAdded() != null) {
            payload.getRepositoriesAdded().forEach(repo -> linkRepository(persistedInstallation, repo));
        }

        if (payload.getRepositoriesRemoved() != null) {
            payload
                .getRepositoriesRemoved()
                .forEach(repo -> unlinkRepository(persistedInstallation, repo.getId(), updatedAt));
        }

        var reconciled = installationRepository.save(persistedInstallation);
        workspaceService.reconcileRepositoriesForInstallation(reconciled);
        return reconciled;
    }

    @Transactional
    public InstallationTarget handleInstallationTargetEvent(GHEventPayloadInstallationTarget payload) {
        var account = payload.getAccount();
        if (account == null) {
            logger.warn("installation_target payload missing account data");
            return null;
        }

        var persistedTarget = targetRepository
            .findById(account.getId())
            .map(existing -> installationTargetConverter.updateFromTargetPayload(account, existing))
            .orElseGet(() -> installationTargetConverter.updateFromTargetPayload(account, new InstallationTarget()));
        persistedTarget.setLastSyncedAt(Instant.now());
        if (payload.getChanges() != null && payload.getChanges().getLogin() != null) {
            persistedTarget.setLastRenamedFrom(payload.getChanges().getLogin().getFrom());
            persistedTarget.setLastRenamedAt(Instant.now());
        }
        final var finalTarget = targetRepository.save(persistedTarget);

        var installationRef = payload.getInstallationRef();
        if (installationRef != null) {
            installationRepository
                .findById(installationRef.getId())
                .ifPresent(installation -> {
                    installation.setTarget(finalTarget);
                    installation.setTargetGithubId(finalTarget.getId());
                    installation.setTargetType(finalTarget.getType());
                    installationRepository.save(installation);
                });
        }

        return finalTarget;
    }

    private Installation upsertInstallation(GHAppInstallation ghInstallation) {
        return installationRepository
            .findById(ghInstallation.getId())
            .map(existing -> installationConverter.update(ghInstallation, existing))
            .orElseGet(() -> installationConverter.convert(ghInstallation));
    }

    private InstallationTarget upsertTarget(GHUser account) {
        if (account == null) {
            return null;
        }
        return targetRepository
            .findById(account.getId())
            .map(existing -> targetRepository.save(installationTargetConverter.update(account, existing)))
            .orElseGet(() -> targetRepository.save(installationTargetConverter.convert(account)));
    }

    private void upsertSuspendedBy(Installation installation, GHUser suspendedBy) {
        if (suspendedBy == null) {
            installation.setSuspendedBy(null);
            return;
        }
        var user = userRepository
            .findById(suspendedBy.getId())
            .map(existing -> userConverter.update(suspendedBy, existing))
            .orElseGet(() -> userConverter.convert(suspendedBy));
        installation.setSuspendedBy(userRepository.save(user));
    }

    private void updateLifecycleState(
        Installation installation,
        String action,
        Instant updatedAt,
        Instant suspendedAt
    ) {
        var referenceTime = Optional.ofNullable(updatedAt).orElseGet(Instant::now);
        if ("deleted".equalsIgnoreCase(action)) {
            installation.setLifecycleState(LifecycleState.DELETED);
            installation.setDeletedAt(referenceTime);
            markRepositoriesInactive(installation, null, referenceTime);
        } else if ("suspend".equalsIgnoreCase(action)) {
            installation.setLifecycleState(LifecycleState.SUSPENDED);
            installation.setSuspendedAt(suspendedAt != null ? suspendedAt : referenceTime);
            installation.setDeletedAt(null);
        } else if ("unsuspend".equalsIgnoreCase(action)) {
            installation.setLifecycleState(LifecycleState.ACTIVE);
            installation.setSuspendedAt(null);
            installation.setSuspendedBy(null);
            installation.setDeletedAt(null);
        } else {
            installation.setLifecycleState(LifecycleState.ACTIVE);
            installation.setDeletedAt(null);
        }

        if ("new_permissions_accepted".equalsIgnoreCase(action)) {
            installation.setLastPermissionsAcceptedAt(referenceTime);
        }
    }

    private void synchronizeRepositoriesSnapshot(
        Installation installation,
        Optional<List<GHEventPayload.Installation.Repository>> rawRepositories,
        Instant updatedAt,
        String action
    ) {
        boolean deleting = "deleted".equalsIgnoreCase(action);
        if (rawRepositories.isEmpty()) {
            if (deleting) {
                markRepositoriesInactive(installation, null, Optional.ofNullable(updatedAt).orElseGet(Instant::now));
            }
            return;
        }

        var referenceTime = Optional.ofNullable(updatedAt).orElseGet(Instant::now);
        var seen = new HashSet<Long>();

        rawRepositories
            .orElseGet(List::of)
            .forEach(repo -> {
                var repository = repositorySyncService.upsertFromInstallationPayload(
                    repo.getId(),
                    repo.getFullName(),
                    repo.getName(),
                    repo.isPrivate()
                );
                seen.add(repository.getId());
                ensureLink(installation, repository, referenceTime, !deleting);
            });

        if (!deleting) {
            markRepositoriesInactive(installation, seen, referenceTime);
        } else {
            markRepositoriesInactive(installation, null, referenceTime);
        }
    }

    private void linkRepository(Installation installation, GHRepository repositoryPayload) {
        if (repositoryPayload.getFullName() == null) {
            logger.warn(
                "Skipping repository without full name in installation_repositories.added for installation {}",
                installation.getId()
            );
            return;
        }
        Repository repository = repositorySyncService.upsertFromInstallationPayload(
            repositoryPayload.getId(),
            repositoryPayload.getFullName(),
            repositoryPayload.getName(),
            repositoryPayload.isPrivate()
        );
        ensureLink(installation, repository, Instant.now(), true);
    }

    private void unlinkRepository(Installation installation, long repositoryId, Instant updatedAt) {
        linkRepository
            .findByIdInstallationIdAndIdRepositoryId(installation.getId(), repositoryId)
            .ifPresent(link -> {
                link.setActive(false);
                link.setRemovedAt(Optional.ofNullable(updatedAt).orElseGet(Instant::now));
                link.setLastSyncedAt(Instant.now());
            });
    }

    private void ensureLink(Installation installation, Repository repository, Instant referenceTime, boolean active) {
        var link = linkRepository
            .findByIdInstallationIdAndIdRepositoryId(installation.getId(), repository.getId())
            .orElseGet(() -> {
                var newLink = new InstallationRepositoryLink();
                newLink.setId(new InstallationRepositoryLink.Id(installation.getId(), repository.getId()));
                newLink.setInstallation(installation);
                newLink.setRepository(repository);
                installation.getRepositoryLinks().add(newLink);
                return newLink;
            });
        link.setInstallation(installation);
        link.setRepository(repository);
        link.setActive(active);
        if (active) {
            link.setRemovedAt(null);
            link.setLinkedAt(referenceTime);
        } else {
            link.setRemovedAt(referenceTime);
        }
        link.setLastSyncedAt(Instant.now());
    }

    private void markRepositoriesInactive(Installation installation, Set<Long> keepIds, Instant referenceTime) {
        installation
            .getRepositoryLinks()
            .forEach(link -> {
                if (keepIds == null || !keepIds.contains(link.getRepository().getId())) {
                    link.setActive(false);
                    link.setRemovedAt(referenceTime);
                    link.setLastSyncedAt(Instant.now());
                }
            });
    }

    private Optional<List<GHEventPayload.Installation.Repository>> extractRawRepositories(
        GHEventPayload.Installation payload
    ) {
        try {
            var repositories = payload.getRawRepositories();
            if (repositories == null) {
                return Optional.empty();
            }
            return Optional.of(repositories);
        } catch (NullPointerException ex) {
            return Optional.empty();
        }
    }

    private Instant safeUpdatedAt(GHAppInstallation installation) {
        try {
            return installation.getUpdatedAt();
        } catch (IOException e) {
            logger.error("Failed to read updated_at for installation {}: {}", installation.getId(), e.getMessage());
            return null;
        }
    }

    private GHUser safeAccount(GHAppInstallation installation) {
        try {
            return installation.getAccount();
        } catch (Exception e) {
            logger.warn("Failed to read account for installation {}: {}", installation.getId(), e.getMessage());
            return null;
        }
    }

    private GHUser safeSuspendedBy(GHAppInstallation installation) {
        try {
            return installation.getSuspendedBy();
        } catch (Exception e) {
            logger.warn(
                "Failed to read suspended_by for installation {}: {}",
                installation.getId(),
                e.getMessage()
            );
            return null;
        }
    }
}
