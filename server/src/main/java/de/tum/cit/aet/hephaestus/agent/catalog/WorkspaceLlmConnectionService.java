package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * CRUD (plus "test connection") for a workspace's own "bring your own" LLM connection (#1368):
 * tenant-scoped, workspace-admin-owned, self-funded. Every mutation is gated on the instance-wide
 * {@code allow_workspace_connections} switch — an instance admin can turn this feature off entirely.
 * Every persisted base URL is vetted by {@link EgressPolicy}, same rule as the instance catalog.
 *
 * <p>Audited on {@code config_audit_event} (unlike the instance catalog, which audits on
 * {@code auth_event} — this resource has a workspace id, so it fits the workspace-scoped ledger).
 */
@Service
@RequiredArgsConstructor
public class WorkspaceLlmConnectionService {

    private final WorkspaceLlmConnectionRepository connectionRepository;
    private final WorkspaceLlmModelRepository modelRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EgressPolicy egressPolicy;
    private final InstanceLlmSettingsService instanceLlmSettingsService;
    private final LlmConnectionProbeService probeService;
    private final ConfigAuditPort configAudit;

    @Transactional(readOnly = true)
    public List<WorkspaceLlmConnection> list(WorkspaceContext workspaceContext) {
        return connectionRepository.findByWorkspaceId(workspaceContext.id());
    }

    @Transactional(readOnly = true)
    public WorkspaceLlmConnection get(WorkspaceContext workspaceContext, Long id) {
        return connectionRepository
            .findByIdAndWorkspaceId(id, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("WorkspaceLlmConnection", id));
    }

    @Transactional
    public WorkspaceLlmConnection create(
        WorkspaceContext workspaceContext,
        CreateWorkspaceLlmConnectionRequestDTO request
    ) {
        requireByoEnabled();
        Long workspaceId = workspaceContext.id();
        String slug = connectionSlug(workspaceId, request.slug(), request.displayName());
        if (
            StringUtils.hasText(request.slug()) &&
            connectionRepository.findByWorkspaceIdAndSlug(workspaceId, slug).isPresent()
        ) {
            throw new LlmConnectionSlugConflictException(slug);
        }
        egressPolicy.validate(request.baseUrl());

        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));

        WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
        connection.setWorkspace(workspace);
        connection.setSlug(slug);
        connection.setDisplayName(request.displayName());
        connection.setBaseUrl(request.baseUrl().trim());
        connection.setApiProtocol(request.apiProtocol());
        connection.setAuthMode(request.authMode() != null ? request.authMode() : LlmAuthMode.BEARER);
        if (StringUtils.hasText(request.apiKey())) {
            connection.setApiKey(request.apiKey());
        }
        if (request.enabled() != null) {
            connection.setEnabled(request.enabled());
        }

        WorkspaceLlmConnection saved;
        try {
            saved = connectionRepository.save(connection);
        } catch (DataIntegrityViolationException e) {
            // The slug fast-path above is racy; the unique constraint backstops the loser of a concurrent
            // create. Report the same 409 rather than leaking a 500.
            throw new LlmConnectionSlugConflictException(slug);
        }
        configAudit.record(
            ConfigAuditEntry.created(
                ConfigAuditEntityType.WORKSPACE_LLM_CONNECTION,
                saved.getId(),
                workspaceId,
                WorkspaceLlmConnectionSnapshot.of(saved)
            )
        );
        return saved;
    }

    private String connectionSlug(Long workspaceId, String requested, String displayName) {
        String base = StringUtils.hasText(requested) ? requested : CatalogSlug.from(displayName);
        if (StringUtils.hasText(requested)) return base;
        String candidate = base;
        for (
            int i = 2;
            connectionRepository.findByWorkspaceIdAndSlug(workspaceId, candidate).isPresent();
            i++
        ) candidate = CatalogSlug.suffix(base, i);
        return candidate;
    }

    @Transactional
    public WorkspaceLlmConnection update(
        WorkspaceContext workspaceContext,
        Long id,
        UpdateWorkspaceLlmConnectionRequestDTO request
    ) {
        WorkspaceLlmConnection connection = get(workspaceContext, id);
        WorkspaceLlmConnectionSnapshot before = WorkspaceLlmConnectionSnapshot.of(connection);

        if (request.displayName() != null) {
            connection.setDisplayName(request.displayName());
        }
        // Clearing the key wins over a supplied value, mirroring the instance connection's semantics.
        if (Boolean.TRUE.equals(request.clearApiKey())) {
            connection.setApiKey(null);
        } else if (request.apiKey() != null) {
            connection.setApiKey(request.apiKey());
        }
        if (request.enabled() != null) {
            connection.setEnabled(request.enabled());
        }

        WorkspaceLlmConnection saved = connectionRepository.save(connection);
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.WORKSPACE_LLM_CONNECTION,
                saved.getId(),
                workspaceContext.id(),
                before,
                WorkspaceLlmConnectionSnapshot.of(saved)
            )
        );
        return saved;
    }

    @Transactional
    public void delete(WorkspaceContext workspaceContext, Long id) {
        WorkspaceLlmConnection connection = get(workspaceContext, id);
        if (modelRepository.existsByConnectionIdAndWorkspaceId(id, workspaceContext.id())) {
            throw new LlmConnectionInUseException(id);
        }
        WorkspaceLlmConnectionSnapshot before = WorkspaceLlmConnectionSnapshot.of(connection);
        connectionRepository.delete(connection);
        configAudit.record(
            ConfigAuditEntry.deleted(ConfigAuditEntityType.WORKSPACE_LLM_CONNECTION, id, workspaceContext.id(), before)
        );
    }

    /** "Test connection": workspace-framed (reachable + model count only, never the raw model list). */
    @Transactional(readOnly = true)
    public WorkspaceLlmProbeResultDTO probe(WorkspaceContext workspaceContext, Long id) {
        WorkspaceLlmConnection connection = get(workspaceContext, id);
        egressPolicy.validate(connection.getBaseUrl());
        LlmProbeResultDTO raw = probeService.probeCredential(
            connection.getBaseUrl(),
            connection.getAuthMode(),
            connection.getApiKey()
        );
        return WorkspaceLlmProbeResultDTO.from(raw);
    }

    private void requireByoEnabled() {
        if (!instanceLlmSettingsService.get().isAllowWorkspaceConnections()) {
            throw new AccessForbiddenException("Connecting your own AI provider is disabled on this server.");
        }
    }
}
