package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.events.ConnectionLifecycleEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final ApplicationEventPublisher eventPublisher;
    private final SyncJobService syncJobService;

    /**
     * Runs the pre-transition revoke/erase callback in its own {@code REQUIRES_NEW} transaction so a
     * failing erase cannot mark the lifecycle transaction rollback-only — see {@link #runRevokeIsolated}.
     */
    private final TransactionTemplate revokeTransactionTemplate;

    public ConnectionService(
        ConnectionRepository connectionRepository,
        ConnectionAuditRepository auditRepository,
        CredentialBundleConverter credentialConverter,
        ApplicationEventPublisher eventPublisher,
        SyncJobService syncJobService,
        PlatformTransactionManager transactionManager
    ) {
        this.connectionRepository = connectionRepository;
        this.auditRepository = auditRepository;
        this.credentialConverter = credentialConverter;
        this.eventPublisher = eventPublisher;
        this.syncJobService = syncJobService;
        this.revokeTransactionTemplate = new TransactionTemplate(transactionManager);
        this.revokeTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
     * Ids of every workspace with an ACTIVE Connection of the given kind — the fan-out a periodic sync
     * scheduler iterates so a freshly connected workspace is picked up even before it has mirrored any data.
     */
    @Transactional(readOnly = true)
    public List<Long> findWorkspaceIdsWithActiveConnection(IntegrationKind kind) {
        return connectionRepository.findWorkspaceIdsByKindAndState(kind, IntegrationState.ACTIVE);
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
    public Optional<ConnectionConfig.SlackConfig> findSlackNotificationConfig(long workspaceId) {
        return findActive(workspaceId, IntegrationKind.SLACK)
            .map(Connection::getConfig)
            .filter(c -> c instanceof ConnectionConfig.SlackConfig)
            .map(c -> (ConnectionConfig.SlackConfig) c);
    }

    @Transactional(readOnly = true)
    public Optional<ConnectionConfig.OutlineConfig> findActiveOutlineConfig(long workspaceId) {
        return findActive(workspaceId, IntegrationKind.OUTLINE)
            .map(Connection::getConfig)
            .filter(c -> c instanceof ConnectionConfig.OutlineConfig)
            .map(c -> (ConnectionConfig.OutlineConfig) c);
    }

    /**
     * Resolves the ACTIVE Outline Connection that registered the given change-notification
     * subscription id, returning its workspace and stored signing secret. The subscription id
     * arrives in an inbound webhook body as an <em>untrusted routing key</em> — it only selects
     * which stored secret to verify against, so a forged id simply matches nothing. Empty when no
     * ACTIVE Outline connection carries that subscription (or it has no stored secret).
     *
     * <p>Reached from {@code OutlineWebhookSecretSource#getSecret} BEFORE the HMAC comparison, on a
     * route the auth rate limiter exempts — so it must be a single indexed probe, never a scan over
     * the connected fleet. {@link ConnectionRepository#findOutlineSubscriptionsBySubscriptionId}
     * supplies exactly that.
     *
     * <p>Fail-closed on ambiguity: a subscription id is globally unique at Outline, so more than one
     * ACTIVE match means corrupt data. We reject the delivery rather than pick a row — guessing would
     * let a hostile/duplicated row decide which workspace's secret verifies a delivery.
     */
    @Transactional(readOnly = true)
    public Optional<OutlineSubscription> findOutlineSubscription(@Nullable String subscriptionId) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return Optional.empty();
        }
        List<ConnectionRepository.OutlineSubscriptionProjection> matches =
            connectionRepository.findOutlineSubscriptionsBySubscriptionId(subscriptionId);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            // Never log the (attacker-supplied) subscription id itself.
            log.error(
                "Outline webhook subscription id matches {} ACTIVE connections — rejecting delivery; out-of-band fix required",
                matches.size()
            );
            return Optional.empty();
        }
        ConnectionRepository.OutlineSubscriptionProjection match = matches.getFirst();
        String secret = match.getSigningSecret();
        if (secret == null || secret.isBlank() || match.getWorkspaceId() == null) {
            return Optional.empty();
        }
        return Optional.of(new OutlineSubscription(match.getWorkspaceId(), secret));
    }

    /** The workspace a change-notification subscription belongs to and its signing secret. */
    public record OutlineSubscription(long workspaceId, String signingSecret) {}

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
     * The Connection by id within its workspace, regardless of state. Deactivation-time cleanup
     * (e.g. tearing down a vendor webhook) runs after the row left ACTIVE, so {@link #findActive}
     * can no longer resolve it.
     */
    @Transactional(readOnly = true)
    public Optional<Connection> findInWorkspace(long workspaceId, long connectionId) {
        return connectionRepository.findByIdAndWorkspaceId(connectionId, workspaceId);
    }

    /**
     * Decrypts the stored {@link BearerToken} of one Connection regardless of state. Empty once
     * credentials were purged (the UNINSTALLED transition) — SUSPENDED rows still resolve.
     */
    @Transactional(readOnly = true)
    public Optional<BearerToken> findBearerToken(long workspaceId, long connectionId) {
        return findInWorkspace(workspaceId, connectionId)
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

        // Invariant: one ACTIVE GitHub Connection per workspace.
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
     *
     * <p>Authoritative: a sync job in flight does NOT block this. Every caller here is the system or
     * the vendor stating a fact — workspace PURGE erasing credentials, Slack telling us the app was
     * uninstalled, a GitHub install replacing its predecessor. None of them can be retried by a human
     * and none may be preempted by a background reconcile, so they win over the job and let it fail on
     * its own. Only the interactive admin door ({@link #disconnect}) fences, because only it has
     * someone to hand a 409 to.
     */
    @Transactional
    public Connection transition(Connection connection, TransitionRequest req) {
        return applyTransition(connection, req, null);
    }

    /**
     * Admin-initiated disconnect: the one door that refuses to uninstall out from under a running sync.
     *
     * <p>The fence is here rather than in {@link #applyTransition} because it trades availability for
     * tidiness — acceptable when an admin clicked a button and can be told "retry", never acceptable
     * for erasure or a vendor-driven uninstall.
     *
     * <p>It cannot wedge. Under the connection's lifecycle lock we first reap leases the hourly sweep
     * hasn't reached (so a job stranded by a pod crash frees the connection in minutes, not an hour —
     * the same inline reap "Sync now" already does), then request the surviving job's cancellation and
     * report it as a 409 naming that job id. The cancel request is durable even though we throw
     * ({@code noRollbackFor}), so this is a "retry in a moment", not a dead end: the runner aborts and
     * the admin's next click succeeds.
     *
     * <p>{@code revoke} may kill vendor access or erase provider data, so it runs only after the fence
     * and the state-machine check have passed, while the row lock is still held.
     *
     * <p>{@code revoke} is best effort: it runs in its own transaction and any {@code RuntimeException}
     * it raises is logged and absorbed here, so the local {@code UNINSTALLED} transition still commits
     * and the admin can always clear a stale row. Callers must NOT re-implement the swallow inside the
     * callback — doing so converts the failure into an {@code UnexpectedRollbackException} at the nested
     * commit instead of suppressing it (see {@link #runRevokeIsolated}).
     *
     * <p>The connection's lifecycle row lock is held for the whole disconnect, vendor round-trips
     * included: that fence is what makes the sync-job check atomic with the state write. The vendor
     * calls are individually kept out of a transaction ({@code Propagation.NOT_SUPPORTED}) so they do
     * not also pin a DB transaction.
     *
     * @throws ConnectionBusyException 409 — a sync job still holds the connection; its cancellation has
     *                                 been requested, so retrying shortly will succeed
     */
    @Transactional(noRollbackFor = ConnectionBusyException.class)
    public Connection disconnect(Connection connection, TransitionRequest req, Runnable revoke) {
        if (req.next() != IntegrationState.UNINSTALLED) {
            throw new IllegalArgumentException("Disconnect must transition to UNINSTALLED");
        }
        return applyTransition(connection, req, revoke, /* fenceOnActiveSyncJob */ true);
    }

    private Connection applyTransition(
        Connection connection,
        TransitionRequest req,
        @Nullable Runnable beforeLocalTransition
    ) {
        return applyTransition(connection, req, beforeLocalTransition, /* fenceOnActiveSyncJob */ false);
    }

    private Connection applyTransition(
        Connection connection,
        TransitionRequest req,
        @Nullable Runnable beforeLocalTransition,
        boolean fenceOnActiveSyncJob
    ) {
        if (connection.getId() != null) {
            long connectionId = connection.getId();
            long workspaceId = connection.getWorkspace().getId();
            connectionRepository.acquireLifecycleLock(connectionId, workspaceId);
            connection = connectionRepository
                .findByIdAndWorkspaceId(connectionId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Connection", connectionId));
            if (fenceOnActiveSyncJob) {
                // Inside the lock, so no job can slip in between this check and the state write.
                syncJobService
                    .requestCancelForTeardown(connectionId)
                    .ifPresent(activeJobId -> {
                        throw new ConnectionBusyException(connectionId, activeJobId);
                    });
            }
        }
        IntegrationState current = connection.getState();
        if (current == req.next()) {
            log.debug("Connection {} already in state {}, no-op", connection.getId(), req.next());
            return connection;
        }
        if (!current.canTransitionTo(req.next()) && !isGuardedReconnect(connection, current, req)) {
            throw new IllegalStateException(
                "Illegal transition for connection " + connection.getId() + ": " + current + " → " + req.next()
            );
        }
        if (beforeLocalTransition != null) {
            runRevokeIsolated(connection, beforeLocalTransition);
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
        Connection saved = connectionRepository.save(connection);
        publishLifecycleEvent(saved, current, req.next());
        return saved;
    }

    /**
     * Runs the vendor revoke / provider-data erase in its own {@code REQUIRES_NEW} transaction and
     * absorbs its failure, so "best effort, proceed locally" is actually reachable.
     *
     * <p><b>Why a nested transaction and not a plain try/catch.</b> The erasers
     * ({@code ScmWorkspaceContentEraser}, {@code SlackWorkspaceContentEraser}, the Outline
     * {@code deleteByWorkspaceId} sweep) are {@code @Transactional} with default {@code REQUIRED}
     * propagation, so joining the lifecycle transaction would let a {@code DataAccessException} from any
     * of them — an FK surprise, a statement timeout on a large mirror delete — mark the shared
     * transaction rollback-only, and the commit would then fail with {@code UnexpectedRollbackException}
     * even though the transition and audit row were written. Running the callback on its own transaction
     * confines that poisoning to the callback; catching OUTSIDE the template also absorbs the
     * {@code UnexpectedRollbackException} raised at the nested commit when a callback swallowed the
     * failure internally.
     *
     * <p><b>Why this cannot deadlock against our own row lock.</b> We hold {@code SELECT … FOR UPDATE}
     * on the {@code connection} row on the outer connection, and the nested transaction runs on a
     * second pooled connection — so it must never wait on that row. It does not: no revoke path writes
     * the {@code connection} row (both {@code OutlineWebhookRegistrar#deregister} and
     * {@code GitLabWebhookService#deregisterActiveWebhook} refuse to rewrite config there), and the FK
     * children they DO write are only ever deleted — {@code outline_document},
     * {@code outline_collection} and {@code outline_document_event} carry a {@code connection_id}, but
     * PostgreSQL runs no parent-side referential check on a child DELETE, so no {@code FOR KEY SHARE} is
     * taken on the locked row. Plain reads of {@code connection} from the nested transaction (e.g.
     * {@code findActiveBearerToken}) are MVCC snapshots and never block on {@code FOR UPDATE}. Adding a
     * write of {@code connection} — or an INSERT into any table keyed on it — to a revoke path would
     * break this and self-deadlock.
     *
     * <p><b>Accepted consequence.</b> Erase and transition are not atomic. A revoke that erases
     * successfully still commits even if the transition afterwards fails (duplicate-correlation
     * short-circuit, illegal transition), leaving erased data behind an ACTIVE connection. Every erase
     * path is idempotent and the next disconnect re-runs it, so this is recoverable — and it is the
     * direction to fail in, since the reverse strands the admin with a 500 and an ACTIVE connection.
     */
    private void runRevokeIsolated(Connection connection, Runnable revoke) {
        try {
            revokeTransactionTemplate.executeWithoutResult(status -> revoke.run());
        } catch (RuntimeException e) {
            log.warn(
                "Vendor revoke/erase failed for connection={} kind={}: {} — proceeding with the local transition",
                connection.getId(),
                connection.getKind(),
                e.toString()
            );
        }
    }

    /**
     * Signal a genuine ACTIVE-boundary crossing to vendor adapters (AFTER_COMMIT listeners).
     * Fires inside the transition transaction so a rollback also drops the event; the
     * same-state and duplicate-correlation early returns above keep replays silent.
     */
    private void publishLifecycleEvent(Connection connection, IntegrationState previous, IntegrationState next) {
        long workspaceId = connection.getWorkspace().getId();
        if (next == IntegrationState.ACTIVE) {
            eventPublisher.publishEvent(
                new ConnectionLifecycleEvent.Activated(connection.getId(), workspaceId, connection.getKind())
            );
        } else if (previous == IntegrationState.ACTIVE) {
            eventPublisher.publishEvent(
                new ConnectionLifecycleEvent.Deactivated(connection.getId(), workspaceId, connection.getKind())
            );
        }
    }

    /**
     * Guarded revival of a terminal UNINSTALLED row — the kind-specific reconnect flows the
     * {@link IntegrationState} javadoc reserves. Two doors only: a completed Slack OAuth round-trip,
     * and an admin-driven inline re-connect ({@code INITIATE}) whose strategy just re-validated
     * fresh credentials against the vendor. Both preserve the vendor natural key
     * {@code (workspace, kind, instance_key)} instead of colliding with the unique constraint.
     */
    private static boolean isGuardedReconnect(
        Connection connection,
        IntegrationState current,
        TransitionRequest request
    ) {
        if (current != IntegrationState.UNINSTALLED || request.next() != IntegrationState.ACTIVE) {
            return false;
        }
        if (connection.getKind() == IntegrationKind.SLACK && "OAUTH_COMPLETE".equals(request.eventType())) {
            return true;
        }
        return "INITIATE".equals(request.eventType());
    }

    /** Parameter object for {@link #transition}. */
    public record TransitionRequest(
        IntegrationState next,
        String eventType,
        String actorKind,
        @Nullable String actorRef,
        @Nullable String correlationId,
        @Nullable String detail
    ) {}
}
