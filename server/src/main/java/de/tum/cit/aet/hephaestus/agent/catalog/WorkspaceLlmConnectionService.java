package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.agent.catalog.ApiProtocolDefaults.AuthDefaults;
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
        CreateWorkspaceLlmConnectionRequest request
    ) {
        requireByoEnabled();
        Long workspaceId = workspaceContext.id();
        if (connectionRepository.findByWorkspaceIdAndSlug(workspaceId, request.slug()).isPresent()) {
            throw new LlmConnectionSlugConflictException(request.slug());
        }
        egressPolicy.validate(request.baseUrl());

        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));

        AuthDefaults defaults = ApiProtocolDefaults.forProtocol(request.apiProtocol());

        WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
        connection.setWorkspace(workspace);
        connection.setSlug(request.slug());
        connection.setDisplayName(request.displayName());
        connection.setBaseUrl(request.baseUrl().trim());
        connection.setApiProtocol(request.apiProtocol());
        connection.setAuthHeaderName(
            StringUtils.hasText(request.authHeaderName()) ? request.authHeaderName() : defaults.headerName()
        );
        connection.setAuthValuePrefix(
            request.authValuePrefix() != null ? request.authValuePrefix() : defaults.valuePrefix()
        );
        if (StringUtils.hasText(request.apiKey())) {
            connection.setApiKey(request.apiKey());
        }
        if (request.azureApiVersion() != null) {
            connection.setAzureApiVersion(
                request.azureApiVersion().isBlank() ? null : request.azureApiVersion().trim()
            );
        }
        if (request.enabled() != null) {
            connection.setEnabled(request.enabled());
        }

        try {
            return connectionRepository.save(connection);
        } catch (DataIntegrityViolationException e) {
            // The slug fast-path above is racy; the unique constraint backstops the loser of a concurrent
            // create. Report the same 409 rather than leaking a 500.
            throw new LlmConnectionSlugConflictException(request.slug());
        }
    }

    @Transactional
    public WorkspaceLlmConnection update(
        WorkspaceContext workspaceContext,
        Long id,
        UpdateWorkspaceLlmConnectionRequest request
    ) {
        requireByoEnabled();
        WorkspaceLlmConnection connection = get(workspaceContext, id);

        if (request.displayName() != null) {
            connection.setDisplayName(request.displayName());
        }
        if (request.baseUrl() != null) {
            String newBaseUrl = request.baseUrl().trim();
            egressPolicy.validate(newBaseUrl);
            connection.setBaseUrl(newBaseUrl);
        }
        if (request.apiProtocol() != null) {
            connection.setApiProtocol(request.apiProtocol());
        }
        if (request.authHeaderName() != null) {
            connection.setAuthHeaderName(request.authHeaderName());
        }
        if (request.authValuePrefix() != null) {
            connection.setAuthValuePrefix(request.authValuePrefix());
        }
        // Clearing the key wins over a supplied value, mirroring the instance connection's semantics.
        if (Boolean.TRUE.equals(request.clearApiKey())) {
            connection.setApiKey(null);
        } else if (request.apiKey() != null) {
            connection.setApiKey(request.apiKey());
        }
        if (request.azureApiVersion() != null) {
            connection.setAzureApiVersion(
                request.azureApiVersion().isBlank() ? null : request.azureApiVersion().trim()
            );
        }
        if (request.enabled() != null) {
            connection.setEnabled(request.enabled());
        }

        return connectionRepository.save(connection);
    }

    @Transactional
    public void delete(WorkspaceContext workspaceContext, Long id) {
        requireByoEnabled();
        WorkspaceLlmConnection connection = get(workspaceContext, id);
        if (modelRepository.existsByConnectionIdAndWorkspaceId(id, workspaceContext.id())) {
            throw new LlmConnectionInUseException(id);
        }
        connectionRepository.delete(connection);
    }

    /** "Test connection": workspace-framed (reachable + model count only, never the raw model list). */
    @Transactional(readOnly = true)
    public WorkspaceLlmProbeResult probe(WorkspaceContext workspaceContext, Long id) {
        requireByoEnabled();
        WorkspaceLlmConnection connection = get(workspaceContext, id);
        egressPolicy.validate(connection.getBaseUrl());
        LlmProbeResult raw = probeService.probeCredential(
            connection.getBaseUrl(),
            connection.getAuthHeaderName(),
            connection.getAuthValuePrefix(),
            connection.getApiKey()
        );
        return WorkspaceLlmProbeResult.from(raw);
    }

    private void requireByoEnabled() {
        if (!instanceLlmSettingsService.get().isAllowWorkspaceConnections()) {
            throw new AccessForbiddenException("Connecting your own AI provider is disabled on this server.");
        }
    }
}
