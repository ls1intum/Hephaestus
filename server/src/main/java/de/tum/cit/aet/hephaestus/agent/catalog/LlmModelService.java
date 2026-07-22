package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.LlmModelAudit;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD, pricing, and sharing for instance-owned LLM catalog models (#1368). GLOBAL, {@code
 * app_admin}-owned, so this service is {@link WorkspaceAgnostic}; access is gated by
 * {@code hasAuthority('app_admin')} on {@link LlmModelAdminController}.
 *
 * <p>Audited on {@code auth_event} via the {@link LlmModelAudit} SPI port, same reasoning as
 * {@link LlmConnectionService}: this catalog is GLOBAL and {@code config_audit_event.workspace_id} is
 * NOT NULL. {@code @ConditionalOnServerRole} follows from the port's sole implementation being
 * server-role-only — nothing outside the admin controller consumes this service.
 */
@Service
@RequiredArgsConstructor
@WorkspaceAgnostic("Instance LLM model catalog is global (app_admin-owned), not tenant-scoped")
@ConditionalOnServerRole
public class LlmModelService {

    private final LlmModelRepository modelRepository;
    private final LlmConnectionRepository connectionRepository;
    private final LlmModelPriceRepository priceRepository;
    private final LlmModelWorkspaceGrantRepository grantRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final LlmModelAudit llmModelAudit;

    @Transactional(readOnly = true)
    public List<LlmModel> list() {
        return modelRepository.findAllWithConnection();
    }

    @Transactional(readOnly = true)
    public LlmModel get(Long id) {
        // Eager-fetches connection: every caller of get() (the admin controller's GET, and its
        // toDTO() calls after update-price/other mutations) immediately reads connection.displayName
        // via LlmModelDTO#from, which would otherwise throw LazyInitializationException once the
        // transaction below has closed (OSIV is off).
        return modelRepository
            .findByIdWithConnection(id)
            .orElseThrow(() -> new EntityNotFoundException("LlmModel", id));
    }

    /** Batched current-price lookup for {@link #list()}, keyed by model id. */
    @Transactional(readOnly = true)
    public Map<Long, LlmModelPrice> currentPricesByModelId(List<Long> modelIds) {
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        return priceRepository
            .findByModelIdInAndEffectiveToIsNull(modelIds)
            .stream()
            .collect(Collectors.toMap(price -> price.getModel().getId(), price -> price));
    }

    /** Batched grant lookup for {@link #list()}, keyed by model id. */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> grantedWorkspaceIdsByModelId(List<Long> modelIds) {
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        return grantRepository
            .findByIdModelIdIn(modelIds)
            .stream()
            .collect(
                Collectors.groupingBy(
                    grant -> grant.getId().getModelId(),
                    Collectors.mapping(grant -> grant.getId().getWorkspaceId(), Collectors.toList())
                )
            );
    }

    @Transactional(readOnly = true)
    public List<Long> grantedWorkspaceIds(Long modelId) {
        return grantRepository
            .findByIdModelId(modelId)
            .stream()
            .map(grant -> grant.getId().getWorkspaceId())
            .toList();
    }

    @Transactional(readOnly = true)
    public LlmModelPrice currentPrice(Long modelId) {
        return priceRepository.findByModelIdAndEffectiveToIsNull(modelId).orElse(null);
    }

    @Transactional
    public LlmModel create(Long connectionId, CreateLlmModelRequestDTO request) {
        if (Boolean.TRUE.equals(request.enabled())) {
            throw new IllegalArgumentException("Create the model disabled, set its price, then activate it.");
        }
        LlmConnection connection = connectionRepository
            .findById(connectionId)
            .orElseThrow(() -> new EntityNotFoundException("LlmConnection", connectionId));
        String slug = modelSlug(connectionId, request.slug(), request.displayName());
        if (
            org.springframework.util.StringUtils.hasText(request.slug()) &&
            modelRepository.findByConnectionIdAndSlug(connectionId, slug).isPresent()
        ) {
            throw new LlmModelSlugConflictException(connectionId, slug);
        }
        if (modelRepository.existsByConnectionIdAndUpstreamModelId(connectionId, request.upstreamModelId())) {
            throw new LlmModelUpstreamIdConflictException(connectionId, request.upstreamModelId());
        }

        LlmModel model = new LlmModel();
        model.setConnection(connection);
        model.setSlug(slug);
        model.setDisplayName(request.displayName());
        model.setUpstreamModelId(request.upstreamModelId());
        model.setContextWindow(request.contextWindow());
        model.setMaxOutputTokens(request.maxOutputTokens());
        if (request.supportsReasoning() != null) {
            model.setSupportsReasoning(request.supportsReasoning());
        }
        if (request.enabled() != null) {
            if (request.enabled()) {
                requireActivatable(model);
            }
            model.setEnabled(request.enabled());
        }

        LlmModel saved;
        try {
            // saveAndFlush (not save): a generated-id entity like this one doesn't necessarily hit the
            // DB inside save() — Hibernate can defer the INSERT to the transaction's implicit flush at
            // commit, which is OUTSIDE this try/catch. A concurrent-create race would then surface the
            // unique-constraint violation as an uncaught 500 instead of the 409 this catch exists to
            // produce (#1368 fix wave). Flushing synchronously here brings the violation back inside.
            saved = modelRepository.saveAndFlush(model);
        } catch (DataIntegrityViolationException e) {
            // The fast-path checks above are racy; the unique constraints backstop the loser of a
            // concurrent create. Report the same 409 rather than leaking a 500 — pick the exception that
            // matches whichever constraint actually fired so the message names the right conflict.
            if (isUpstreamIdConflict(e)) {
                throw new LlmModelUpstreamIdConflictException(connectionId, request.upstreamModelId());
            }
            throw new LlmModelSlugConflictException(connectionId, slug);
        }
        llmModelAudit.modelCreated(saved.getId(), saved.getConnection().getId(), saved.getSlug());
        return saved;
    }

    private String modelSlug(Long connectionId, String requested, String displayName) {
        String base = org.springframework.util.StringUtils.hasText(requested)
            ? requested
            : CatalogSlug.from(displayName);
        if (org.springframework.util.StringUtils.hasText(requested)) return base;
        String candidate = base;
        for (int i = 2; modelRepository.findByConnectionIdAndSlug(connectionId, candidate).isPresent(); i++) candidate =
            CatalogSlug.suffix(base, i);
        return candidate;
    }

    private void requireActivatable(LlmModel model) {
        LlmModelPrice price = priceRepository.findByModelIdAndEffectiveToIsNull(model.getId()).orElse(null);
        if (!model.getConnection().isEnabled() || price == null || price.getPricingMode() == PricingMode.UNPRICED) {
            throw new IllegalArgumentException(
                "Activate the connection and configure a price before activating the model."
            );
        }
    }

    @Transactional
    public LlmModel update(Long id, UpdateLlmModelRequestDTO request) {
        // Eager-fetches connection — the controller converts the returned entity straight to
        // LlmModelDTO after this transaction closes; see get()'s javadoc comment for why.
        LlmModel model = modelRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new EntityNotFoundException("LlmModel", id));

        if (request.displayName() != null) {
            model.setDisplayName(request.displayName());
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
        if (request.enabled() != null) {
            model.setEnabled(request.enabled());
        }
        if (model.isEnabled()) {
            requireActivatable(model);
        }

        LlmModel saved;
        try {
            // saveAndFlush (not save) — see create()'s identical comment: an unflushed save() would let
            // a concurrent-update race's constraint violation escape this catch as an uncaught 500.
            saved = modelRepository.saveAndFlush(model);
        } catch (DataIntegrityViolationException e) {
            // The fast-path check above is racy; the unique constraint backstops the loser of a
            // concurrent update.
            if (isUpstreamIdConflict(e)) {
                throw new LlmModelUpstreamIdConflictException(
                    model.getConnection().getId(),
                    model.getUpstreamModelId()
                );
            }
            throw e;
        }
        llmModelAudit.modelUpdated(saved.getId(), saved.getConnection().getId(), saved.getSlug());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        LlmModel model = modelRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("LlmModel", id));
        if (agentConfigRepository.existsByInstanceModelId(id)) {
            throw new LlmModelInUseException(id);
        }
        modelRepository.delete(model);
        llmModelAudit.modelDeleted(model.getId(), model.getConnection().getId(), model.getSlug());
    }

    /**
     * Repricing is temporal supersede-on-insert: close the current open row (if any) and insert the new
     * one as the open row, both in this transaction — {@code ux_llm_model_price_open} allows at most one
     * open row per model at a time.
     */
    @Transactional
    public LlmModelPrice updatePrice(Long modelId, UpdateLlmModelPriceRequestDTO request) {
        LlmModel model = modelRepository
            .findByIdForUpdate(modelId)
            .orElseThrow(() -> new EntityNotFoundException("LlmModel", modelId));
        validatePriceRequest(request);
        if (model.isEnabled() && request.pricingMode() == PricingMode.UNPRICED) {
            throw new IllegalArgumentException("Disable the model before changing its price to UNPRICED.");
        }

        Instant now = Instant.now();
        priceRepository
            .findByModelIdAndEffectiveToIsNull(modelId)
            .ifPresent(open -> {
                open.setEffectiveTo(now);
                priceRepository.save(open);
            });

        LlmModelPrice price = new LlmModelPrice();
        price.setModel(model);
        price.setPricingMode(request.pricingMode());
        price.setPer1mInputUsd(request.per1mInputUsd());
        price.setPer1mOutputUsd(request.per1mOutputUsd());
        price.setPer1mCacheReadUsd(request.per1mCacheReadUsd());
        price.setPer1mCacheWriteUsd(request.per1mCacheWriteUsd());
        price.setNote(blankToNull(request.note()));
        price.setEffectiveFrom(now);

        LlmModelPrice saved = priceRepository.save(price);
        llmModelAudit.modelPriceChanged(modelId, saved.getPricingMode().name());
        return saved;
    }

    private static void validatePriceRequest(UpdateLlmModelPriceRequestDTO request) {
        LlmPriceValidation.validate(
            request.pricingMode(),
            request.per1mInputUsd(),
            request.per1mOutputUsd(),
            request.per1mCacheReadUsd(),
            request.per1mCacheWriteUsd(),
            request.note()
        );
    }

    /**
     * Shares a model with all workspaces or replaces its grant set with exactly the given workspaces.
     * Unknown workspace ids are rejected with a 400 rather than silently dropped.
     */
    @Transactional
    public LlmModel updateSharing(Long modelId, UpdateLlmModelSharingRequestDTO request) {
        // Eager-fetches connection — same reasoning as update() above.
        LlmModel model = modelRepository
            .findByIdWithConnection(modelId)
            .orElseThrow(() -> new EntityNotFoundException("LlmModel", modelId));

        List<LlmModelWorkspaceGrant> existing = grantRepository.findByIdModelId(modelId);

        if (request.visibility() == ModelVisibility.PUBLIC) {
            if (!existing.isEmpty()) {
                grantRepository.deleteAll(existing);
            }
        } else {
            Set<Long> requested =
                request.workspaceIds() != null ? new LinkedHashSet<>(request.workspaceIds()) : Set.of();
            assertWorkspacesExist(requested);

            Set<Long> existingWorkspaceIds = existing
                .stream()
                .map(grant -> grant.getId().getWorkspaceId())
                .collect(Collectors.toSet());

            List<LlmModelWorkspaceGrant> toRemove = existing
                .stream()
                .filter(grant -> !requested.contains(grant.getId().getWorkspaceId()))
                .toList();
            if (!toRemove.isEmpty()) {
                grantRepository.deleteAll(toRemove);
            }

            Instant now = Instant.now();
            List<LlmModelWorkspaceGrant> toAdd = requested
                .stream()
                .filter(workspaceId -> !existingWorkspaceIds.contains(workspaceId))
                .map(workspaceId -> {
                    LlmModelWorkspaceGrant grant = new LlmModelWorkspaceGrant(modelId, workspaceId);
                    grant.setGrantedAt(now);
                    return grant;
                })
                .toList();
            if (!toAdd.isEmpty()) {
                grantRepository.saveAll(toAdd);
            }
        }

        model.setVisibility(request.visibility());
        LlmModel saved = modelRepository.save(model);
        int workspaceCount =
            request.visibility() == ModelVisibility.GRANTED && request.workspaceIds() != null
                ? request.workspaceIds().size()
                : 0;
        llmModelAudit.modelSharingChanged(modelId, saved.getVisibility().name(), workspaceCount);
        return saved;
    }

    private void assertWorkspacesExist(Set<Long> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return;
        }
        Set<Long> found = workspaceRepository
            .findAllById(workspaceIds)
            .stream()
            .map(Workspace::getId)
            .collect(Collectors.toSet());
        Set<Long> unknown = new LinkedHashSet<>(workspaceIds);
        unknown.removeAll(found);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                "Cannot share with unknown workspace(s): " +
                    unknown.stream().map(String::valueOf).collect(Collectors.joining(", "))
            );
        }
    }

    private static String blankToNull(String value) {
        return value != null && value.isBlank() ? null : value;
    }

    /**
     * Matches the {@code ux_llm_model_connection_upstream} unique index by name so a save() failure is
     * only reported as an upstream-id conflict when that specific constraint fired — any other integrity
     * failure (e.g. a future NOT NULL column) should not be mislabelled (#1368).
     */
    private static boolean isUpstreamIdConflict(DataIntegrityViolationException e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                return name != null && name.equalsIgnoreCase("ux_llm_model_connection_upstream");
            }
            cur = cur.getCause();
        }
        return false;
    }
}
