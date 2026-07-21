package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for models on a workspace's own "bring your own" LLM connection (#1368), plus the
 * available-models projection every workspace admin uses to pick a Task's model: the union of instance
 * catalog models visible to this workspace and the workspace's own BYO models.
 *
 * <p>Every mutation is gated on {@code allow_workspace_connections}, same as
 * {@link WorkspaceLlmConnectionService}. Pricing reuses {@link LlmPriceValidation} — same PRICED/FREE/
 * UNPRICED rule as the instance catalog, just applied inline instead of through a temporal history.
 * Audited on {@code config_audit_event} — see {@link WorkspaceLlmConnectionService} for why this
 * differs from the instance catalog's {@code auth_event} ledger.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceLlmModelService {

    private final WorkspaceLlmModelRepository modelRepository;
    private final WorkspaceLlmConnectionRepository connectionRepository;
    private final LlmModelRepository instanceModelRepository;
    private final LlmModelPriceRepository instancePriceRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final InstanceLlmSettingsService instanceLlmSettingsService;
    private final ConfigAuditPort configAudit;

    @Transactional(readOnly = true)
    public List<WorkspaceLlmModel> list(WorkspaceContext workspaceContext) {
        // Eager-fetches connection — the controller converts every row straight to
        // WorkspaceLlmModelDTO, same reasoning as get()'s javadoc comment.
        return modelRepository.findByWorkspaceIdWithConnection(workspaceContext.id());
    }

    @Transactional(readOnly = true)
    public WorkspaceLlmModel get(WorkspaceContext workspaceContext, Long id) {
        // Eager-fetches connection: every caller (the controller's GET, and update()/delete() below,
        // which reuse this lookup) either converts straight to WorkspaceLlmModelDTO — which reads
        // connection.displayName — after this transaction closes, or needs the connection materialized
        // for its own checks. A plain findByIdAndWorkspaceId would return a lazy connection proxy that
        // throws LazyInitializationException once OSIV is off.
        return modelRepository
            .findByIdAndWorkspaceIdWithConnection(id, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("WorkspaceLlmModel", id));
    }

    @Transactional
    public WorkspaceLlmModel create(
        WorkspaceContext workspaceContext,
        Long connectionId,
        CreateWorkspaceLlmModelRequestDTO request
    ) {
        requireByoEnabled();
        Long workspaceId = workspaceContext.id();
        WorkspaceLlmConnection connection = connectionRepository
            .findByIdAndWorkspaceId(connectionId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("WorkspaceLlmConnection", connectionId));
        if (modelRepository.findByWorkspaceIdAndSlug(workspaceId, request.slug()).isPresent()) {
            throw new LlmModelSlugConflictException(connectionId, request.slug());
        }

        PricingMode pricingMode = request.pricingMode() != null ? request.pricingMode() : PricingMode.UNPRICED;
        LlmPriceValidation.validate(
            pricingMode,
            request.per1mInputUsd(),
            request.per1mOutputUsd(),
            request.per1mCacheReadUsd(),
            request.per1mCacheWriteUsd(),
            request.per1mReasoningUsd(),
            request.priceNote()
        );

        WorkspaceLlmModel model = new WorkspaceLlmModel();
        model.setWorkspace(connection.getWorkspace());
        model.setConnection(connection);
        model.setSlug(request.slug());
        model.setDisplayName(request.displayName());
        model.setUpstreamModelId(request.upstreamModelId());
        model.setApiProtocolOverride(blankToNull(request.apiProtocolOverride()));
        if (request.modality() != null) {
            model.setModality(request.modality());
        }
        model.setContextWindow(request.contextWindow());
        model.setMaxOutputTokens(request.maxOutputTokens());
        if (request.supportsReasoning() != null) {
            model.setSupportsReasoning(request.supportsReasoning());
        }
        model.setCacheControlFormat(blankToNull(request.cacheControlFormat()));
        if (request.enabled() != null) {
            model.setEnabled(request.enabled());
        }
        applyPrice(
            model,
            pricingMode,
            request.per1mInputUsd(),
            request.per1mOutputUsd(),
            request.per1mCacheReadUsd(),
            request.per1mCacheWriteUsd(),
            request.per1mReasoningUsd(),
            request.priceNote()
        );

        WorkspaceLlmModel saved;
        try {
            saved = modelRepository.save(model);
        } catch (DataIntegrityViolationException e) {
            // The slug fast-path above is racy; the unique constraint backstops the loser of a concurrent
            // create. Report the same 409 rather than leaking a 500.
            throw new LlmModelSlugConflictException(connectionId, request.slug());
        }
        configAudit.record(
            ConfigAuditEntry.created(
                ConfigAuditEntityType.WORKSPACE_LLM_MODEL,
                saved.getId(),
                workspaceId,
                WorkspaceLlmModelSnapshot.of(saved)
            )
        );
        return saved;
    }

    @Transactional
    public WorkspaceLlmModel update(
        WorkspaceContext workspaceContext,
        Long id,
        UpdateWorkspaceLlmModelRequestDTO request
    ) {
        requireByoEnabled();
        WorkspaceLlmModel model = get(workspaceContext, id);
        WorkspaceLlmModelSnapshot before = WorkspaceLlmModelSnapshot.of(model);

        if (request.displayName() != null) {
            model.setDisplayName(request.displayName());
        }
        if (request.upstreamModelId() != null) {
            model.setUpstreamModelId(request.upstreamModelId());
        }
        if (request.apiProtocolOverride() != null) {
            model.setApiProtocolOverride(blankToNull(request.apiProtocolOverride()));
        }
        if (request.modality() != null) {
            model.setModality(request.modality());
        }
        if (request.contextWindow() != null) {
            model.setContextWindow(request.contextWindow());
        }
        if (request.maxOutputTokens() != null) {
            model.setMaxOutputTokens(request.maxOutputTokens());
        }
        if (request.supportsReasoning() != null) {
            model.setSupportsReasoning(request.supportsReasoning());
        }
        if (request.cacheControlFormat() != null) {
            model.setCacheControlFormat(blankToNull(request.cacheControlFormat()));
        }
        if (request.enabled() != null) {
            model.setEnabled(request.enabled());
        }
        // Pricing is all-or-nothing: only touched when pricingMode is given (see class docs).
        if (request.pricingMode() != null) {
            LlmPriceValidation.validate(
                request.pricingMode(),
                request.per1mInputUsd(),
                request.per1mOutputUsd(),
                request.per1mCacheReadUsd(),
                request.per1mCacheWriteUsd(),
                request.per1mReasoningUsd(),
                request.priceNote()
            );
            applyPrice(
                model,
                request.pricingMode(),
                request.per1mInputUsd(),
                request.per1mOutputUsd(),
                request.per1mCacheReadUsd(),
                request.per1mCacheWriteUsd(),
                request.per1mReasoningUsd(),
                request.priceNote()
            );
        }

        WorkspaceLlmModel saved = modelRepository.save(model);
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.WORKSPACE_LLM_MODEL,
                saved.getId(),
                workspaceContext.id(),
                before,
                WorkspaceLlmModelSnapshot.of(saved)
            )
        );
        return saved;
    }

    @Transactional
    public void delete(WorkspaceContext workspaceContext, Long id) {
        requireByoEnabled();
        WorkspaceLlmModel model = get(workspaceContext, id);
        if (agentConfigRepository.existsByWorkspaceModelIdAndWorkspaceId(id, workspaceContext.id())) {
            throw new LlmModelInUseException(id);
        }
        WorkspaceLlmModelSnapshot before = WorkspaceLlmModelSnapshot.of(model);
        modelRepository.delete(model);
        configAudit.record(
            ConfigAuditEntry.deleted(ConfigAuditEntityType.WORKSPACE_LLM_MODEL, id, workspaceContext.id(), before)
        );
    }

    /**
     * The union projection every workspace admin picks a Task's model from: instance models visible to
     * this workspace, plus this workspace's own usable BYO models. Read-only, no BYO gate — viewing is
     * always allowed even if {@code allow_workspace_connections} was later turned off (existing bindings
     * must remain visible/explicable).
     */
    @Transactional(readOnly = true)
    public List<AvailableLlmModelDTO> availableModels(WorkspaceContext workspaceContext) {
        List<LlmModel> instanceModels = instanceModelRepository.findVisibleEnabledModels(workspaceContext.id());
        List<Long> instanceModelIds = instanceModels.stream().map(LlmModel::getId).toList();
        Map<Long, LlmModelPrice> currentPrices = instanceModelIds.isEmpty()
            ? Map.of()
            : instancePriceRepository
                  .findByModelIdInAndEffectiveToIsNull(instanceModelIds)
                  .stream()
                  .collect(Collectors.toMap(price -> price.getModel().getId(), price -> price));

        List<WorkspaceLlmModel> workspaceModels = modelRepository.findEnabledWithEnabledConnection(
            workspaceContext.id()
        );

        List<AvailableLlmModelDTO> result = new ArrayList<>(instanceModels.size() + workspaceModels.size());
        instanceModels.forEach(model ->
            result.add(AvailableLlmModelDTO.fromInstance(model, currentPrices.get(model.getId())))
        );
        workspaceModels.forEach(model -> result.add(AvailableLlmModelDTO.fromWorkspace(model)));
        return result;
    }

    private void requireByoEnabled() {
        if (!instanceLlmSettingsService.get().isAllowWorkspaceConnections()) {
            throw new AccessForbiddenException("Connecting your own AI provider is disabled on this server.");
        }
    }

    private static void applyPrice(
        WorkspaceLlmModel model,
        PricingMode pricingMode,
        BigDecimal per1mInputUsd,
        BigDecimal per1mOutputUsd,
        BigDecimal per1mCacheReadUsd,
        BigDecimal per1mCacheWriteUsd,
        BigDecimal per1mReasoningUsd,
        String priceNote
    ) {
        model.setPricingMode(pricingMode);
        model.setPer1mInputUsd(per1mInputUsd);
        model.setPer1mOutputUsd(per1mOutputUsd);
        model.setPer1mCacheReadUsd(per1mCacheReadUsd);
        model.setPer1mCacheWriteUsd(per1mCacheWriteUsd);
        model.setPer1mReasoningUsd(per1mReasoningUsd);
        model.setPriceNote(blankToNull(priceNote));
    }

    private static String blankToNull(String value) {
        return value != null && value.isBlank() ? null : value;
    }
}
