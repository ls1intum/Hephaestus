package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.agent.catalog.ApiProtocolDefaults.AuthDefaults;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.LlmConnectionAudit;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * CRUD for instance-owned LLM connections (#1368). GLOBAL, {@code app_admin}-owned, so this service is
 * {@link WorkspaceAgnostic}; access is gated by {@code hasAuthority('app_admin')} on
 * {@link LlmConnectionAdminController}. Every persisted base URL is vetted by {@link EgressPolicy}.
 *
 * <p>Audited on {@code auth_event} (not {@code config_audit_event}) via the {@link LlmConnectionAudit} SPI
 * port: this catalog is GLOBAL, and {@code config_audit_event.workspace_id} is NOT NULL, so a
 * workspace-less change cannot land there. The port's sole implementation is
 * {@code @ConditionalOnServerRole}, so this service — nothing outside the admin controller consumes it
 * — is gated the same way, matching {@code AccountAdminController}'s pattern for the same ledger.
 */
@Service
@RequiredArgsConstructor
@WorkspaceAgnostic("Instance LLM connection catalog is global (app_admin-owned), not tenant-scoped")
@ConditionalOnServerRole
public class LlmConnectionService {

    private final LlmConnectionRepository connectionRepository;
    private final LlmModelRepository modelRepository;
    private final EgressPolicy egressPolicy;
    private final LlmConnectionAudit llmConnectionAudit;

    @Transactional(readOnly = true)
    public List<LlmConnection> list() {
        return connectionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public LlmConnection get(Long id) {
        return connectionRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("LlmConnection", id));
    }

    @Transactional
    public LlmConnection create(CreateLlmConnectionRequest request) {
        if (connectionRepository.findBySlug(request.slug()).isPresent()) {
            throw new LlmConnectionSlugConflictException(request.slug());
        }
        egressPolicy.validate(request.baseUrl());

        AuthDefaults defaults = ApiProtocolDefaults.forProtocol(request.apiProtocol());

        LlmConnection connection = new LlmConnection();
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

        LlmConnection saved;
        try {
            saved = connectionRepository.save(connection);
        } catch (DataIntegrityViolationException e) {
            // The slug fast-path above is racy; the unique constraint backstops the loser of a concurrent
            // create. Report the same 409 rather than leaking a 500.
            throw new LlmConnectionSlugConflictException(request.slug());
        }
        llmConnectionAudit.connectionCreated(saved.getId(), saved.getSlug());
        return saved;
    }

    @Transactional
    public LlmConnection update(Long id, UpdateLlmConnectionRequest request) {
        LlmConnection connection = connectionRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("LlmConnection", id));

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
        // Clearing the key wins over a supplied value, mirroring AgentConfig's clearLlmApiKey semantics.
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

        LlmConnection saved = connectionRepository.save(connection);
        llmConnectionAudit.connectionUpdated(saved.getId(), saved.getSlug());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        LlmConnection connection = connectionRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("LlmConnection", id));
        if (modelRepository.existsByConnectionId(id)) {
            throw new LlmConnectionInUseException(id);
        }
        connectionRepository.delete(connection);
        llmConnectionAudit.connectionDeleted(connection.getId(), connection.getSlug());
    }
}
