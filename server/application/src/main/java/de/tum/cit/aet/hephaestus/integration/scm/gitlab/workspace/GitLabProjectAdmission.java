package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import de.tum.cit.aet.hephaestus.core.security.ScmOrigin;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig.GitLabConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** GitLab reads retain their connection and monitor lifetime until their short persistence transaction. */
@Component
@RequiredArgsConstructor
public class GitLabProjectAdmission {
    public record ConnectionRead(
            long connectionId,
            long workspaceId,
            long providerId,
            String groupPath,
            Map<String, Set<Long>> originalMonitors) {}

    public record ProjectRead(ConnectionRead connection, long repositoryId, long nativeId, long monitorId) {}

    private final ConnectionRepository connections;
    private final ConnectionService connectionService;
    private final RepositoryRepository repositories;
    private final RepositoryToMonitorRepository monitors;
    private final WorkspaceRepository workspaces;
    private final TransactionTemplate transactions;

    @Transactional(readOnly = true)
    public Optional<ConnectionRead> captureConnection(long workspaceId, long providerId) {
        return captureConnection(workspaceId, providerId, true);
    }

    private Optional<ConnectionRead> captureConnection(long workspaceId, long providerId, boolean discovery) {
        Map<String, Set<Long>> original = new HashMap<>();
        if (discovery) {
            for (RepositoryToMonitor monitor : monitors.findByWorkspaceId(workspaceId)) {
                Long nativeId = monitor.getNativeId();
                if (nativeId == null) {
                    nativeId = repositories
                            .findByNameWithOwnerAndProviderId(monitor.getNameWithOwner(), providerId)
                            .map(Repository::getNativeId)
                            .orElse(null);
                }
                String key = nativeId != null
                        ? "id:" + nativeId
                        : "path:" + monitor.getNameWithOwner().toLowerCase(Locale.ROOT);
                original.computeIfAbsent(key, ignored -> new HashSet<>()).add(monitor.getId());
            }
        }
        return connections
                .findActive(workspaceId, IntegrationKind.GITLAB)
                .filter(connection -> connection.getConfig() instanceof GitLabConfig)
                .flatMap(connection -> workspaces
                        .findMonitorPolicy(workspaceId)
                        .map(policy -> new ConnectionRead(
                                Objects.requireNonNull(connection.getId()),
                                workspaceId,
                                providerId,
                                policy.getAccountLogin(),
                                original.entrySet().stream()
                                        .collect(Collectors.toUnmodifiableMap(
                                                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue()))))));
    }

    @Transactional(readOnly = true)
    public Optional<ProjectRead> capture(long workspaceId, Repository supplied) {
        return captureProject(workspaceId, supplied);
    }

    private Optional<ProjectRead> captureProject(long workspaceId, Repository supplied) {
        return repositories.findById(supplied.getId()).flatMap(repository -> {
            if (repository.getProvider().getType() != IdentityProviderType.GITLAB || repository.getNativeId() == null)
                return Optional.empty();
            long providerId = Objects.requireNonNull(repository.getProvider().getId());
            Optional<ConnectionRead> connection = captureConnection(workspaceId, providerId, false);
            if (connection.isEmpty()
                    || !allowed(
                            connection.get(),
                            repository.getNativeId(),
                            repository.getNameWithOwner(),
                            repository.getProvider().getServerUrl(),
                            false,
                            false)) return Optional.empty();
            return monitors.findByWorkspaceIdAndSourceRepositoryId(workspaceId, repository.getId()).stream()
                    .min(Comparator.comparing(RepositoryToMonitor::getId))
                    .map(monitor -> new ProjectRead(
                            connection.get(), repository.getId(), repository.getNativeId(), monitor.getId()));
        });
    }

    @Transactional(readOnly = true)
    public Optional<ProjectRead> capture(ConnectionRead discovery, Repository repository) {
        Set<Long> expected = originalMonitors(discovery, repository.getNativeId(), repository.getNameWithOwner());
        return captureProject(discovery.workspaceId(), repository)
                .filter(read -> read.connection().connectionId() == discovery.connectionId())
                .filter(read -> expected.isEmpty() || expected.contains(read.monitorId()));
    }

    /** Called inside the transaction which will perform the first project/user/work write. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean hold(ProjectRead read) {
        return holdCurrent(read);
    }

    private boolean holdCurrent(ProjectRead read) {
        ConnectionRead connection = read.connection();
        connections.acquireLifecycleLock(connection.connectionId(), connection.workspaceId());
        return repositories
                .findById(read.repositoryId())
                .filter(repository -> Objects.equals(repository.getNativeId(), read.nativeId())
                        && Objects.equals(repository.getProvider().getId(), connection.providerId())
                        && allowed(
                                connection,
                                repository.getNativeId(),
                                repository.getNameWithOwner(),
                                repository.getProvider().getServerUrl(),
                                true,
                                false)
                        && monitors.findSourceRepositoryIdsOfMonitor(read.monitorId())
                                .contains(read.repositoryId()))
                .isPresent();
    }

    public <T> Optional<T> write(ProjectRead read, Supplier<@Nullable T> write) {
        return Optional.ofNullable(transactions.execute(status -> holdCurrent(read) ? write.get() : null));
    }

    public boolean current(ProjectRead read) {
        return write(read, () -> true).orElse(false);
    }

    public boolean runWrite(ProjectRead read, Runnable write) {
        return write(read, () -> {
                    write.run();
                    return true;
                })
                .orElse(false);
    }

    /** Discovery may create a fresh monitor, but must check its original connection and the current exclusion first. */
    public <T> Optional<T> discover(
            ConnectionRead read, long nativeId, String path, String serverUrl, Supplier<@Nullable T> write) {
        return Optional.ofNullable(transactions.execute(status -> {
            connections.acquireLifecycleLock(read.connectionId(), read.workspaceId());
            Set<Long> original = originalMonitors(read, nativeId, path);
            if (!original.isEmpty() && original.stream().noneMatch(monitors::existsById)) return null;
            return allowed(read, nativeId, path, serverUrl, true, true) ? write.get() : null;
        }));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean allowsReference(ProjectRead read, Repository repository) {
        return Objects.equals(
                        repository.getProvider().getId(), read.connection().providerId())
                && connections
                        .findConfigInState(
                                read.connection().connectionId(),
                                read.connection().workspaceId(),
                                IntegrationState.ACTIVE)
                        .filter(GitLabConfig.class::isInstance)
                        .map(GitLabConfig.class::cast)
                        .filter(config -> !config.projectExclusions()
                                .excludes(repository.getNativeId(), repository.getNameWithOwner()))
                        .isPresent();
    }

    private boolean allowed(
            ConnectionRead read, long nativeId, String path, String serverUrl, boolean locked, boolean canonical) {
        Optional<GitLabConfig> config = locked
                ? connections
                        .findConfigInState(read.connectionId(), read.workspaceId(), IntegrationState.ACTIVE)
                        .filter(GitLabConfig.class::isInstance)
                        .map(GitLabConfig.class::cast)
                : connections
                        .findByIdAndWorkspaceId(read.connectionId(), read.workspaceId())
                        .filter(connection -> connection.getState() == IntegrationState.ACTIVE)
                        .map(Connection::getConfig)
                        .filter(GitLabConfig.class::isInstance)
                        .map(GitLabConfig.class::cast);
        GitLabConfig current = config.orElse(null);
        if (current == null
                || !ScmOrigin.of(current.serverUrl()).equals(ScmOrigin.of(serverUrl))
                || workspaces
                        .findMonitorPolicy(read.workspaceId())
                        .filter(policy -> policy.getAccountLogin().equals(read.groupPath()))
                        .filter(policy -> contains(policy.getAccountLogin(), path))
                        .isEmpty()) return false;
        if (canonical && current.projectExclusions().excludes(null, path)) {
            current = connectionService
                    .updateGitLabProjectExclusions(
                            read.workspaceId(), exclusions -> exclusions.resolvePath(nativeId, path))
                    .map(connection -> (GitLabConfig) connection.getConfig())
                    .orElse(current);
        }
        return !current.projectExclusions().excludes(nativeId, path);
    }

    private static Set<Long> originalMonitors(ConnectionRead read, long nativeId, String path) {
        Set<Long> ids = new HashSet<>(read.originalMonitors().getOrDefault("id:" + nativeId, Set.of()));
        ids.addAll(read.originalMonitors().getOrDefault("path:" + path.toLowerCase(Locale.ROOT), Set.of()));
        return ids;
    }

    private static boolean contains(String group, String path) {
        String root = group.toLowerCase(Locale.ROOT);
        String candidate = path.toLowerCase(Locale.ROOT);
        return candidate.startsWith(root + "/");
    }
}
