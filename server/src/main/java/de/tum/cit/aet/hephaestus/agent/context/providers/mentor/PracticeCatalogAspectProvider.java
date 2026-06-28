package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises {@code inputs/context/practice_catalog.json} for {@link MentorChatRequest}.
 *
 * <p>Lists workspace practices with criteria so the mentor agent can talk about specific
 * coding standards that apply to the user's contributions. Independent of {@code developerId}
 * — every member of a workspace sees the same practice catalog, so the cache key is just
 * the workspace.
 *
 * <p><b>Freshness is TTL-bounded only.</b> The {@code mentor_practice_aspect} cache has no
 * event-driven invalidation: there is no Practice-change domain event to hang an eviction off,
 * and {@link MentorContextInvalidator} only evicts per-user SCM/detection-driven caches. So an
 * admin edit to a practice (criteria text, activate/deactivate, rename, area reassignment) is
 * picked up only after the entry expires (MENTOR_ASPECT_TTL, see {@code CacheConfig}). This is an
 * accepted limit — the catalog is low-churn admin data and a few minutes of staleness in mentor
 * chat is harmless — not an oversight; wire a Practice-change event into the invalidator if that
 * window ever needs to close.
 */
@Component
@RequiredArgsConstructor
public class PracticeCatalogAspectProvider implements ContentProvider {

    @Override
    public String connectorId() {
        return "core";
    }

    /** Workspace-relative output key. Whitelisted in {@code MentorAspects#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "practice_catalog.json";

    private static final String CACHE_NAME = "mentor_practice_aspect";

    private final PracticeRepository practiceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof MentorChatRequest;
    }

    @Override
    public boolean required() {
        return false;
    }

    /** Tx-on-contribute / not-on-buildPayload AOP convention documented at {@link MentorAspects}. */
    @Override
    @Transactional(readOnly = true)
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        MentorChatRequest req = (MentorChatRequest) request;
        Long key = req.workspaceId();
        Cache cache = cacheManager.getCache(CACHE_NAME);
        // Atomic compute-if-absent closes the get/build/put race on invalidation events.
        ObjectNode payload = (cache != null)
            ? cache.get(key, () -> buildPayload(req.workspaceId()))
            : buildPayload(req.workspaceId());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(payload));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize practice catalog aspect", e);
        }
    }

    /** Pure function of (workspaceId). Callers cache through {@link CacheManager}. */
    public ObjectNode buildPayload(Long workspaceId) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));

        // Active practices only — the mentor should not talk about practices the workspace
        // has explicitly disabled.
        List<Practice> practices = practiceRepository.findByWorkspaceIdAndActiveTrue(workspaceId);

        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("workspace").put("slug", workspace.getWorkspaceSlug());

        ArrayNode arr = root.putArray("practices");
        for (Practice practice : practices) {
            ObjectNode node = arr.addObject();
            node.put("slug", practice.getSlug());
            node.put("displayName", practice.getName());
            node.put("criteria", practice.getCriteria());
        }
        return root;
    }
}
