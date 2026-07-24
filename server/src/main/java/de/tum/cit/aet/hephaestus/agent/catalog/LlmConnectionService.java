package de.tum.cit.aet.hephaestus.agent.catalog;

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
    public LlmConnection create(CreateLlmConnectionRequestDTO request) {
        String slug = connectionSlug(request.slug(), request.displayName());
        if (StringUtils.hasText(request.slug()) && connectionRepository.findBySlug(slug).isPresent()) {
            throw new LlmConnectionSlugConflictException(slug);
        }
        egressPolicy.validate(request.baseUrl());

        LlmConnection connection = new LlmConnection();
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

        LlmConnection saved;
        try {
            saved = connectionRepository.save(connection);
        } catch (DataIntegrityViolationException e) {
            // The slug fast-path above is racy; the unique constraint backstops the loser of a concurrent
            // create. Report the same 409 rather than leaking a 500.
            throw new LlmConnectionSlugConflictException(slug);
        }
        llmConnectionAudit.connectionCreated(saved.getId(), saved.getSlug());
        return saved;
    }

    private String connectionSlug(String requested, String displayName) {
        String base = StringUtils.hasText(requested) ? requested : CatalogSlug.from(displayName);
        if (StringUtils.hasText(requested)) return base;
        String candidate = base;
        for (int i = 2; connectionRepository.findBySlug(candidate).isPresent(); i++) candidate = CatalogSlug.suffix(
            base,
            i
        );
        return candidate;
    }

    @Transactional
    public LlmConnection update(Long id, UpdateLlmConnectionRequestDTO request) {
        LlmConnection connection = connectionRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("LlmConnection", id));

        if (request.displayName() != null) {
            connection.setDisplayName(request.displayName());
        }
        // Clearing the key wins over a supplied value, mirroring AgentConfig's clearLlmApiKey semantics.
        if (Boolean.TRUE.equals(request.clearApiKey())) {
            connection.setApiKey(null);
        } else if (request.apiKey() != null) {
            connection.setApiKey(request.apiKey());
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
