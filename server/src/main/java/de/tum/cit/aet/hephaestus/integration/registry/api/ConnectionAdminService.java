package de.tum.cit.aet.hephaestus.integration.registry.api;

import de.tum.cit.aet.hephaestus.integration.manifest.IntegrationManifestRegistry;
import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionAudit;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionAuditRepository;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService.TransitionRequest;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application-layer facade for the Connection registry's admin operations. Owns all
 * direct repository access so the {@link ConnectionController} stays a thin HTTP
 * adapter — required by the {@code ArchitectureTest.controllersDoNotAccessRepositories}
 * rule and the {@code CodeQualityTest.controllersAreThin} 5-param ceiling.
 *
 * <p>This is intentionally a separate type from {@link ConnectionService}: that one
 * owns the state-machine + audit invariants (called from many places — credential
 * providers, lifecycle webhooks, purge contributor). This service owns admin-only
 * orchestration (list, lookup, audit projection, inline-credentials Connection
 * creation) and is only called from the REST surface.
 */
@Service
public class ConnectionAdminService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionAdminService.class);

    private final ConnectionRepository connectionRepository;
    private final ConnectionAuditRepository auditRepository;
    private final ConnectionService connectionService;
    private final WorkspaceRepository workspaceRepository;
    private final IntegrationManifestRegistry manifests;

    public ConnectionAdminService(ConnectionRepository connectionRepository,
                                  ConnectionAuditRepository auditRepository,
                                  ConnectionService connectionService,
                                  WorkspaceRepository workspaceRepository,
                                  IntegrationManifestRegistry manifests) {
        this.connectionRepository = connectionRepository;
        this.auditRepository = auditRepository;
        this.connectionService = connectionService;
        this.workspaceRepository = workspaceRepository;
        this.manifests = manifests;
    }

    @Transactional(readOnly = true)
    public List<Connection> listForWorkspace(long workspaceId) {
        return connectionRepository.findByWorkspaceId(workspaceId);
    }

    /**
     * Resolve a Connection by id AND verify it belongs to the path workspaceId.
     * Throws {@link NoSuchElementException} on either branch — we intentionally do NOT
     * distinguish "no such id" from "id exists in another workspace" because the latter
     * would leak workspace boundaries to a probing attacker.
     */
    @Transactional(readOnly = true)
    public Connection findInWorkspaceOrThrow(long workspaceId, long id) {
        Connection connection = connectionRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Connection not found: id=" + id));
        Long actualWorkspaceId = connection.getWorkspace() == null ? null : connection.getWorkspace().getId();
        if (!Objects.equals(actualWorkspaceId, workspaceId)) {
            log.info("Cross-workspace read attempt: connection={} actualWorkspace={} requestedWorkspace={}",
                id, actualWorkspaceId, workspaceId);
            throw new NoSuchElementException("Connection not found in workspace " + workspaceId + ": id=" + id);
        }
        return connection;
    }

    @Transactional(readOnly = true)
    public List<ConnectionAudit> auditForConnection(long connectionId, int limit) {
        return auditRepository.findByConnectionIdOrderByOccurredAtDesc(connectionId)
            .stream().limit(limit).toList();
    }

    public IntegrationManifestRegistry manifests() {
        return manifests;
    }

    /**
     * Persist a Connection from an inline-credential flow and transition it ACTIVE.
     * {@code credentials} are recorded via a placeholder marker today — replaced by
     * the AES-GCM converter in the credentials slice (see {@code GitlabCredentialProvider}
     * TODO chain).
     */
    @Transactional
    public Connection createInlineConnection(long workspaceId,
                                             IntegrationKind kind,
                                             @Nullable String instanceKey,
                                             CredentialBundle credentials,
                                             Map<String, String> userInput,
                                             String actorRef) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace not found: id=" + workspaceId));

        ConnectionConfig config = buildConfigForInlineKind(kind, userInput, instanceKey);
        Connection connection = new Connection(workspace, kind, instanceKey, config);
        connection = connectionRepository.save(connection);

        persistCredentialsPlaceholder(connection, credentials);

        String correlationId = "initiate-" + connection.getId() + "-" + UUID.randomUUID();
        connection = connectionService.transition(connection, new TransitionRequest(
            IntegrationState.ACTIVE,
            "INITIATE",
            "ADMIN",
            actorRef,
            correlationId,
            "Inline credentials accepted"
        ));
        log.info("Initiated inline Connection id={} workspace={} kind={}",
            connection.getId(), workspaceId, kind);
        return connection;
    }

    /**
     * Stash the credential bundle alongside the Connection. The real flow encrypts via
     * AES-GCM — see TODO in {@link de.tum.cit.aet.hephaestus.integration.gitlab.credentials.GitlabCredentialProvider}.
     * The placeholder byte sequence is non-secret and bounded length; downstream
     * credential providers detect it and return empty rather than crashing.
     */
    private void persistCredentialsPlaceholder(Connection connection, CredentialBundle bundle) {
        if (bundle == null) {
            return;
        }
        connection.setCredentialsEncrypted(new byte[] { 0x00 });
        connection.setCredentialsAlg("PLACEHOLDER-AES-GCM");
        log.warn("Persisting credential PLACEHOLDER for connection={} kind={} (bundle type={}). "
                + "Replace with AES-GCM ciphertext once the credential converter ships.",
            connection.getId(), connection.getKind(), bundle.getClass().getSimpleName());
    }

    /**
     * Per-kind config construction for inline-flow Connections. Only kinds with an
     * {@code AcceptInline} initiation are handled here — others should never reach
     * this branch because their strategies return {@code RedirectToVendor}.
     */
    private ConnectionConfig buildConfigForInlineKind(IntegrationKind kind,
                                                      Map<String, String> userInput,
                                                      @Nullable String instanceKey) {
        Set<String> enabledStreams = new HashSet<>();
        return switch (kind) {
            case GITLAB -> {
                String serverUrl = userInput.getOrDefault("server_url", "https://gitlab.com");
                Long groupId = parseGroupId(instanceKey);
                yield new ConnectionConfig.GitLabConfig(
                    serverUrl,
                    groupId,
                    /* gitlabWebhookId */ null,
                    ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                    enabledStreams
                );
            }
            case GITHUB -> new ConnectionConfig.GitHubPatConfig(
                /* orgLogin */ null,
                userInput.getOrDefault("server_url", null),
                enabledStreams
            );
            case SLACK -> new ConnectionConfig.SlackConfig(
                instanceKey,
                /* teamName */ null,
                /* notificationChannelId */ null,
                /* teamLabel */ null,
                enabledStreams
            );
            case OUTLINE -> new ConnectionConfig.OutlineConfig(
                userInput.getOrDefault("server_url", "https://app.getoutline.com"),
                instanceKey,
                enabledStreams
            );
        };
    }

    private static @Nullable Long parseGroupId(@Nullable String instanceKey) {
        if (instanceKey == null || instanceKey.isBlank()) return null;
        try {
            return Long.parseLong(instanceKey.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
