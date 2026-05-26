package de.tum.cit.aet.hephaestus.integration.connection;

import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates Connection lookups + state transitions.
 *
 * <p>Transitions are guarded by {@link IntegrationState#canTransitionTo}; same-state
 * calls are idempotent no-ops. Audit-row INSERT carries {@code correlation_id} with a
 * uniqueness constraint, so webhook redelivery never causes state flap.
 *
 * <p>Runtime helpers ({@code findActiveProviderKind}, {@code findActiveGitHubAppConfig},
 * {@code findActiveGitLabConfig}, {@code findSlackNotificationConfig},
 * {@code findActiveBearerToken}, {@code updateConfig}) are the only path callers should
 * use to read or mutate per-Connection state. The legacy {@code Workspace} columns
 * ({@code installation_id}, {@code personal_access_token}, …) are gone after the Stage 1
 * cutover; this service is their authoritative replacement.
 */
@Service
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

    private final ConnectionRepository connectionRepository;
    private final ConnectionAuditRepository auditRepository;
    private final CredentialBundleConverter credentialConverter;

    public ConnectionService(ConnectionRepository connectionRepository,
                             ConnectionAuditRepository auditRepository,
                             CredentialBundleConverter credentialConverter) {
        this.connectionRepository = connectionRepository;
        this.auditRepository = auditRepository;
        this.credentialConverter = credentialConverter;
    }

    @Transactional(readOnly = true)
    public Optional<Connection> findActive(long workspaceId, IntegrationKind kind) {
        return connectionRepository.findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
            workspaceId, kind, IntegrationState.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Connection requireActive(long workspaceId, IntegrationKind kind) {
        return findActive(workspaceId, kind).orElseThrow(() ->
            new NoSuchElementException("No ACTIVE Connection for workspace=" + workspaceId + " kind=" + kind));
    }

    @Transactional(readOnly = true)
    public Optional<Connection> findByRef(IntegrationRef ref) {
        if (ref.instanceKey() == null) return Optional.empty();
        return connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            ref.workspaceId(), ref.kind(), ref.instanceKey());
    }

    /**
     * Reverse lookup: workspace id from a GitHub App {@code installationId}.
     *
     * <p>Replaces the legacy {@code WorkspaceRepository.findByInstallationId} after
     * the {@code installation_id} column is dropped — the durable identity lives in
     * the GitHub Connection's {@code instance_key} now. Returns the FIRST matching
     * binding; cross-workspace collision is rejected at
     * {@code GithubInstallationBindingService}, so at most one row exists.
     *
     * <p>{@link Optional#empty()} when no GitHub Connection carries this installation
     * id (uninstalled, never bound, or backfill incomplete). Callers MUST treat empty
     * the same way they treated the old {@code Optional<Workspace>} — no implicit
     * create.
     */
    @Transactional(readOnly = true)
    public Optional<Long> findWorkspaceIdByGitHubInstallationId(long installationId) {
        return connectionRepository
            .findByKindAndInstanceKey(IntegrationKind.GITHUB, Long.toString(installationId))
            .stream()
            .findFirst()
            .map(c -> c.getWorkspace().getId());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Runtime helpers — replace the legacy Workspace getInstallationId() /
    // getPersonalAccessToken() / getGitlabGroupId() / … readers. All return
    // Optional and never throw on the "no Connection yet" case — callers must
    // handle the empty path explicitly (the same shape the legacy nullable
    // columns gave them, no behavioural drift on freshly-provisioned workspaces).
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Returns the kind of the workspace's SCM Connection.
     *
     * <p>Prefers {@link IntegrationKind#GITHUB} if both somehow exist — the legacy
     * invariant was "one workspace, one SCM provider". A workspace that ends up with
     * both an ACTIVE GitHub and an ACTIVE GitLab Connection is misconfigured; we pick
     * GitHub because that matches the historical GitHub-first bootstrap path and gives
     * deterministic behaviour rather than a coin-flip on row insertion order.
     */
    @Transactional(readOnly = true)
    public Optional<IntegrationKind> findActiveProviderKind(long workspaceId) {
        if (findActive(workspaceId, IntegrationKind.GITHUB).isPresent()) {
            return Optional.of(IntegrationKind.GITHUB);
        }
        if (findActive(workspaceId, IntegrationKind.GITLAB).isPresent()) {
            return Optional.of(IntegrationKind.GITLAB);
        }
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
    public Optional<ConnectionConfig.SlackConfig> findSlackNotificationConfig(long workspaceId) {
        return findActive(workspaceId, IntegrationKind.SLACK)
            .map(Connection::getConfig)
            .filter(c -> c instanceof ConnectionConfig.SlackConfig)
            .map(c -> (ConnectionConfig.SlackConfig) c);
    }

    /**
     * Decrypts the stored {@link BearerToken} for the workspace's ACTIVE Connection of
     * the given kind, if any. The credential blob is decoded against the per-row AAD
     * (see {@link Connection#credentials}) so tampering or cross-row substitution
     * surfaces as {@link de.tum.cit.aet.hephaestus.core.security.EncryptionException}
     * rather than a silent "no auth available".
     */
    @Transactional(readOnly = true)
    public Optional<BearerToken> findActiveBearerToken(long workspaceId, IntegrationKind kind) {
        return findActive(workspaceId, kind)
            .flatMap(c -> c.credentials(credentialConverter))
            .filter(b -> b instanceof BearerToken)
            .map(b -> (BearerToken) b);
    }

    /**
     * Atomically mutate the {@link ConnectionConfig} on the workspace's ACTIVE
     * Connection of the given kind.
     *
     * <p>This is the only sanctioned write path for config-only changes (e.g. stamping
     * {@code gitlab_webhook_id} on an existing GitLab connection after webhook
     * registration succeeds). Returns the updated Connection, or empty if no ACTIVE
     * row exists — callers must handle the "no connection bound yet" path; we will
     * NOT auto-create on update.
     *
     * <p>Mutator gets the current config and must return the replacement variant of
     * the same sealed subtype. The repository call uses Hibernate's version column to
     * prevent lost updates under concurrent writes.
     */
    @Transactional
    public Optional<Connection> updateConfig(long workspaceId, IntegrationKind kind,
                                             UnaryOperator<ConnectionConfig> mutator) {
        return findActive(workspaceId, kind).map(c -> {
            ConnectionConfig next = mutator.apply(c.getConfig());
            if (next == null) {
                throw new IllegalArgumentException(
                    "Mutator returned null for connection " + c.getId() + " — config must be non-null"
                );
            }
            if (!next.getClass().equals(c.getConfig().getClass())) {
                throw new IllegalArgumentException(
                    "Mutator changed config variant on connection " + c.getId() + ": "
                        + c.getConfig().getClass().getSimpleName() + " → " + next.getClass().getSimpleName()
                );
            }
            c.setConfig(next);
            return connectionRepository.save(c);
        });
    }

    /**
     * Replace the credential blob on the workspace's ACTIVE Connection of the given
     * kind. Used by GitLab PAT rotation (old token revoked by GitLab → new token must
     * be persisted immediately). Returns the updated Connection or empty if no ACTIVE
     * row exists.
     */
    @Transactional
    public Optional<Connection> rotateBearerToken(long workspaceId, IntegrationKind kind, BearerToken bundle) {
        return findActive(workspaceId, kind).map(c -> {
            c.setCredentials(bundle, credentialConverter);
            return connectionRepository.save(c);
        });
    }

    /**
     * Upsert the workspace's GitHub App binding so it reflects {@code installationId}.
     *
     * <p>Two-step rebind:
     * <ol>
     *   <li>If a non-UNINSTALLED GitHub Connection exists for this workspace with a
     *       different {@code instance_key} (PAT row, or a stale App row pointing at a
     *       different installation), transition it to UNINSTALLED — that path clears
     *       its credential blob inside the same transaction (per
     *       {@link IntegrationState#UNINSTALLED} contract).</li>
     *   <li>Either re-use an existing {@code (workspace, GITHUB, installationId)} row
     *       (re-bind) or create a new one. The transition to {@code ACTIVE} is wrapped
     *       in {@link #transition} so the audit row + idempotency contract are honoured.</li>
     * </ol>
     *
     * <p>Used by {@code GithubLifecycleListener.createOrUpdateFromInstallation} on the
     * PAT→App promotion path. {@code GithubInstallationBindingService.bind} owns the
     * higher-trust "OAuth-driven bind" entry — it adds the installer-identity check
     * and the cross-workspace collision guard. This helper is the lifecycle-driven
     * counterpart: webhook says "install on org X" and we have to make the binding
     * truthful regardless of what the PAT-mode workspace looked like before.
     *
     * @param workspace      the target workspace
     * @param installationId the GitHub App installation id (becomes the {@code instance_key})
     * @param accountLogin   GitHub org/user login stored in {@link ConnectionConfig.GitHubAppConfig}
     * @param correlationId  audit correlation key; webhook redelivery with the same id is silenced
     * @return the ACTIVE GitHub App connection
     */
    @Transactional
    public Connection upsertGitHubAppConnection(Workspace workspace,
                                                long installationId,
                                                @Nullable String accountLogin,
                                                String correlationId) {
        String instanceKey = Long.toString(installationId);

        // Step 1: retire any non-matching SCM-side row on this workspace. The legacy
        // PAT workspace carries instance_key='pat'; a previously-bound App installation
        // could carry a different number. Either way, retire it before introducing the
        // new instance_key to keep the invariant "one ACTIVE GitHub Connection per
        // workspace" intact.
        for (Connection existing : connectionRepository.findByWorkspaceIdAndState(
            workspace.getId(), IntegrationState.ACTIVE)) {
            if (existing.getKind() != IntegrationKind.GITHUB) {
                continue;
            }
            if (instanceKey.equals(existing.getInstanceKey())) {
                continue;
            }
            transition(existing, new TransitionRequest(
                IntegrationState.UNINSTALLED,
                "REPLACED_BY_INSTALL",
                "SYSTEM",
                "github-install-" + installationId,
                correlationId + "-retire-" + existing.getId(),
                "Replaced by GitHub App installation " + installationId
            ));
        }

        // Step 2: upsert the (workspace, GITHUB, installationId) row.
        Connection connection = connectionRepository
            .findByWorkspaceIdAndKindAndInstanceKey(workspace.getId(), IntegrationKind.GITHUB, instanceKey)
            .orElseGet(() -> {
                ConnectionConfig.GitHubAppConfig config = new ConnectionConfig.GitHubAppConfig(
                    installationId, accountLogin, /* serverUrl */ null, Set.of()
                );
                Connection fresh = new Connection(workspace, IntegrationKind.GITHUB, instanceKey, config);
                fresh.setDisplayName(accountLogin);
                return connectionRepository.save(fresh);
            });

        // Refresh stored accountLogin/orgLogin if the webhook carries a non-blank one
        // (handles the rare-but-real case where the org renamed since first bind).
        if (accountLogin != null && !accountLogin.isBlank()
            && connection.getConfig() instanceof ConnectionConfig.GitHubAppConfig current
            && !accountLogin.equals(current.orgLogin())) {
            connection.setConfig(new ConnectionConfig.GitHubAppConfig(
                installationId, accountLogin, current.serverUrl(), current.enabledStreams()
            ));
            connection.setDisplayName(accountLogin);
            connection = connectionRepository.save(connection);
        }

        if (connection.getState() != IntegrationState.ACTIVE) {
            connection = transition(connection, new TransitionRequest(
                IntegrationState.ACTIVE,
                "INSTALL_BIND",
                "GITHUB_WEBHOOK",
                "github-install-" + installationId,
                correlationId,
                "Linked workspace to GitHub App installation " + installationId
            ));
        }

        return connection;
    }

    /**
     * Transition the Connection to {@code req.next()}. Idempotent: same-state is a no-op
     * (no audit row); invalid transitions throw; webhook redelivery is silenced via
     * the {@code uq_connection_audit_idempotency} constraint.
     *
     * <p>Side effect: transitioning to {@link IntegrationState#UNINSTALLED} clears the
     * credential ciphertext + algorithm tag inside the same transaction. The
     * {@code IntegrationState.UNINSTALLED} javadoc contract ("credentials cleared") is
     * enforced here — not in the entity, not in a downstream listener — so the purge is
     * atomic with the state change.
     */
    @Transactional
    public Connection transition(Connection connection, TransitionRequest req) {
        IntegrationState current = connection.getState();
        if (current == req.next()) {
            log.debug("Connection {} already in state {}, no-op", connection.getId(), req.next());
            return connection;
        }
        if (!current.canTransitionTo(req.next())) {
            throw new IllegalStateException(
                "Illegal transition for connection " + connection.getId() + ": " + current + " → " + req.next()
            );
        }
        ConnectionAudit audit = new ConnectionAudit(
            connection, req.eventType(), current, req.next(),
            req.actorKind(), req.actorRef(), req.correlationId(), req.detail()
        );
        try {
            auditRepository.save(audit);
        } catch (DataIntegrityViolationException e) {
            log.info("Idempotent {} for connection={} corr={} (already recorded)", req.eventType(),
                connection.getId(), req.correlationId());
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

    /** Parameter object for {@link #transition} — collapses 7 params to one record. */
    public record TransitionRequest(
        IntegrationState next,
        String eventType,
        String actorKind,
        @Nullable String actorRef,
        @Nullable String correlationId,
        @Nullable String detail
    ) {
    }
}
