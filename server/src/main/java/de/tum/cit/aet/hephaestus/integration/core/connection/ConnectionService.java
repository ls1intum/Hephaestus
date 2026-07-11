package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connection lookups + state transitions. Transitions are guarded by
 * {@link IntegrationState#canTransitionTo}; same-state calls are no-ops. The audit
 * row's correlation_id uniqueness silences webhook redelivery.
 */
@Service
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

    private final ConnectionRepository connectionRepository;
    private final ConnectionAuditRepository auditRepository;
    private final CredentialBundleConverter credentialConverter;

    public ConnectionService(
        ConnectionRepository connectionRepository,
        ConnectionAuditRepository auditRepository,
        CredentialBundleConverter credentialConverter
    ) {
        this.connectionRepository = connectionRepository;
        this.auditRepository = auditRepository;
        this.credentialConverter = credentialConverter;
    }

    @Transactional(readOnly = true)
    public Optional<Connection> findActive(long workspaceId, IntegrationKind kind) {
        return connectionRepository.findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
            workspaceId,
            kind,
            IntegrationState.ACTIVE
        );
    }

    /**
     * Kind of the workspace's SCM Connection. The invariant is "one workspace, one
     * SCM provider" — if both ACTIVE rows exist that's corrupt data and we fail loud.
     */
    @Transactional(readOnly = true)
    public Optional<IntegrationKind> findActiveProviderKind(long workspaceId) {
        boolean github = findActive(workspaceId, IntegrationKind.GITHUB).isPresent();
        boolean gitlab = findActive(workspaceId, IntegrationKind.GITLAB).isPresent();
        if (github && gitlab) {
            throw new IllegalStateException(
                "Workspace " +
                    workspaceId +
                    " has ACTIVE Connections for both GITHUB and GITLAB; " +
                    "out-of-band fix required"
            );
        }
        if (github) return Optional.of(IntegrationKind.GITHUB);
        if (gitlab) return Optional.of(IntegrationKind.GITLAB);
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ConnectionConfig.GitHubAppConfig> findActiveGitHubAppConfig(long workspaceId) {
        return findActive(workspaceId, IntegrationKind.GITHUB)
            .map(Connection::getConfig)
            .filter(c -> c instanceof ConnectionConfig.GitHubAppConfig)
            .map(c -> (ConnectionConfig.GitHubAppConfig) c);
    }

    @Transactional(readOnly = true)
    public Optional<ConnectionConfig.GitHubPatConfig> findActiveGitHubPatConfig(long workspaceId) {
        return findActive(workspaceId, IntegrationKind.GITHUB)
            .map(Connection::getConfig)
            .filter(c -> c instanceof ConnectionConfig.GitHubPatConfig)
            .map(c -> (ConnectionConfig.GitHubPatConfig) c);
    }

    @Transactional(readOnly = true)
    public Optional<ConnectionConfig.GitLabConfig> findActiveGitLabConfig(long workspaceId) {
        return findActive(workspaceId, IntegrationKind.GITLAB)
            .map(Connection::getConfig)
            .filter(c -> c instanceof ConnectionConfig.GitLabConfig)
            .map(c -> (ConnectionConfig.GitLabConfig) c);
    }

    @Transactional(readOnly = true)
    public Optional<ConnectionConfig.SlackConfig> findSlackConfig(long workspaceId) {
        return findActive(workspaceId, IntegrationKind.SLACK)
            .map(Connection::getConfig)
            .filter(c -> c instanceof ConnectionConfig.SlackConfig)
            .map(c -> (ConnectionConfig.SlackConfig) c);
    }

    /**
     * Decrypts the stored {@link BearerToken} for the workspace's ACTIVE Connection,
     * if any. Tampering/cross-row substitution surfaces as {@code EncryptionException}.
     */
    @Transactional(readOnly = true)
    public Optional<BearerToken> findActiveBearerToken(long workspaceId, IntegrationKind kind) {
        return findActive(workspaceId, kind)
            .flatMap(c -> c.credentials(credentialConverter))
            .flatMap(b -> b instanceof BearerToken bt ? Optional.of(bt) : Optional.empty());
    }

    /**
     * Mutate the {@link ConnectionConfig} on the workspace's ACTIVE Connection.
     * Empty when no ACTIVE row exists — callers MUST NOT auto-create. The mutator
     * must return the same sealed-subtype variant; cross-variant returns throw.
     */
    @Transactional
    public Optional<Connection> updateConfig(
        long workspaceId,
        IntegrationKind kind,
        UnaryOperator<ConnectionConfig> mutator
    ) {
        return findActive(workspaceId, kind).map(c -> {
            ConnectionConfig next = mutator.apply(c.getConfig());
            if (next == null) {
                throw new IllegalArgumentException(
                    "Mutator returned null for connection " + c.getId() + " — config must be non-null"
                );
            }
            if (!next.getClass().equals(c.getConfig().getClass())) {
                throw new IllegalArgumentException(
                    "Mutator changed config variant on connection " +
                        c.getId() +
                        ": " +
                        c.getConfig().getClass().getSimpleName() +
                        " → " +
                        next.getClass().getSimpleName()
                );
            }
            c.setConfig(next);
            return connectionRepository.save(c);
        });
    }

    /**
     * Replace the credential blob on the workspace's ACTIVE Connection. Empty when
     * no ACTIVE row exists.
     */
    @Transactional
    public Optional<Connection> rotateBearerToken(long workspaceId, IntegrationKind kind, BearerToken bundle) {
        return findActive(workspaceId, kind).map(c -> {
            c.setCredentials(bundle, credentialConverter);
            return connectionRepository.save(c);
        });
    }

    /**
     * Upsert the workspace's GitHub App binding to {@code installationId}. Retires
     * any non-matching SCM-side row first (clearing its credentials via the
     * {@link IntegrationState#UNINSTALLED} transition), then activates the
     * {@code (workspace, GITHUB, installationId)} row. Idempotent on
     * {@code correlationId}.
     */
    @Transactional
    public Connection upsertGitHubAppConnection(
        Workspace workspace,
        long installationId,
        @Nullable String accountLogin,
        String correlationId
    ) {
        String instanceKey = Long.toString(installationId);

        // Retire any non-matching SCM-side row before introducing the new instance_key
        // (invariant: one ACTIVE GitHub Connection per workspace).
        for (Connection existing : connectionRepository.findByWorkspaceIdAndState(
            workspace.getId(),
            IntegrationState.ACTIVE
        )) {
            if (existing.getKind() != IntegrationKind.GITHUB) {
                continue;
            }
            if (instanceKey.equals(existing.getInstanceKey())) {
                continue;
            }
            transition(
                existing,
                new TransitionRequest(
                    IntegrationState.UNINSTALLED,
                    "REPLACED_BY_INSTALL",
                    "SYSTEM",
                    "github-install-" + installationId,
                    correlationId + "-retire-" + existing.getId(),
                    "Replaced by GitHub App installation " + installationId
                )
            );
        }

        Connection connection = connectionRepository
            .findByWorkspaceIdAndKindAndInstanceKey(workspace.getId(), IntegrationKind.GITHUB, instanceKey)
            .orElseGet(() -> {
                ConnectionConfig.GitHubAppConfig config = new ConnectionConfig.GitHubAppConfig(
                    installationId,
                    accountLogin,
                    /* serverUrl */ null,
                    Set.of()
                );
                Connection fresh = new Connection(workspace, IntegrationKind.GITHUB, instanceKey, config);
                fresh.setDisplayName(accountLogin);
                return connectionRepository.save(fresh);
            });

        // Refresh accountLogin/orgLogin if renamed since first bind.
        if (
            accountLogin != null &&
            !accountLogin.isBlank() &&
            connection.getConfig() instanceof ConnectionConfig.GitHubAppConfig current &&
            !accountLogin.equals(current.orgLogin())
        ) {
            connection.setConfig(
                new ConnectionConfig.GitHubAppConfig(
                    installationId,
                    accountLogin,
                    current.serverUrl(),
                    current.enabledStreams()
                )
            );
            connection.setDisplayName(accountLogin);
            connection = connectionRepository.save(connection);
        }

        if (connection.getState() != IntegrationState.ACTIVE) {
            connection = transition(
                connection,
                new TransitionRequest(
                    IntegrationState.ACTIVE,
                    "INSTALL_BIND",
                    "GITHUB_WEBHOOK",
                    "github-install-" + installationId,
                    correlationId,
                    "Linked workspace to GitHub App installation " + installationId
                )
            );
        }

        return connection;
    }

    /**
     * Upsert + activate the workspace's PAT Connection row. Idempotent on
     * {@code (workspace, kind, instanceKey)}.
     */
    @Transactional
    public void provisionPatConnection(
        Workspace workspace,
        IntegrationKind kind,
        String instanceKey,
        ConnectionConfig config,
        String token,
        String correlationId
    ) {
        Connection connection = connectionRepository
            .findByWorkspaceIdAndKindAndInstanceKey(workspace.getId(), kind, instanceKey)
            .orElseGet(() -> {
                Connection fresh = new Connection(workspace, kind, instanceKey, config);
                fresh.setDisplayName(workspace.getAccountLogin());
                return connectionRepository.save(fresh);
            });

        if (connection.getState() != IntegrationState.ACTIVE) {
            connection = transition(
                connection,
                new TransitionRequest(
                    IntegrationState.ACTIVE,
                    "PAT_PROVISIONED",
                    "SYSTEM",
                    "scm-connection-provisioner",
                    correlationId,
                    "Provisioned PAT connection on workspace creation"
                )
            );
        }

        if (token != null && !token.isBlank()) {
            rotateBearerToken(workspace.getId(), kind, new BearerToken(token, null));
        }
    }

    /**
     * Transition the Connection to {@code req.next()}. Idempotent on same-state and on
     * duplicate {@code correlationId}; invalid transitions throw. Transitioning to
     * {@link IntegrationState#UNINSTALLED} clears credentials atomically.
     */
    @Transactional
    public Connection transition(Connection connection, TransitionRequest req) {
        IntegrationState current = connection.getState();
        if (current == req.next()) {
            log.debug("Connection {} already in state {}, no-op", connection.getId(), req.next());
            return connection;
        }
        if (!current.canTransitionTo(req.next()) && !isSlackOAuthReconnect(connection, current, req)) {
            throw new IllegalStateException(
                "Illegal transition for connection " + connection.getId() + ": " + current + " → " + req.next()
            );
        }
        ConnectionAudit audit = new ConnectionAudit(
            connection,
            req.eventType(),
            current,
            req.next(),
            req.actorKind(),
            req.actorRef(),
            req.correlationId(),
            req.detail()
        );
        try {
            auditRepository.save(audit);
        } catch (DataIntegrityViolationException e) {
            log.info(
                "Idempotent {} for connection={} corr={} (already recorded)",
                req.eventType(),
                connection.getId(),
                req.correlationId()
            );
            return connection;
        }
        connection.setState(req.next());
        connection.setStateReason(req.detail());
        if (req.next() == IntegrationState.UNINSTALLED && connection.getCredentialsEncrypted() != null) {
            connection.setCredentialsEncrypted(null);
            connection.setCredentialsAlg(null);
            log.info("Purged credentials on UNINSTALLED transition for connection={}", connection.getId());
        }
        return connectionRepository.save(connection);
    }

    private static boolean isSlackOAuthReconnect(
        Connection connection,
        IntegrationState current,
        TransitionRequest request
    ) {
        return (
            connection.getKind() == IntegrationKind.SLACK &&
            current == IntegrationState.UNINSTALLED &&
            request.next() == IntegrationState.ACTIVE &&
            "OAUTH_COMPLETE".equals(request.eventType())
        );
    }

    /** Parameter object for {@link #transition} — collapses 6 params to one record. */
    public record TransitionRequest(
        IntegrationState next,
        String eventType,
        String actorKind,
        @Nullable String actorRef,
        @Nullable String correlationId,
        @Nullable String detail
    ) {}
}
