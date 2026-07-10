package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.events.WorkspaceCreatedEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seeds a grounded default practice catalog (process-level areas + their practices) into a workspace so its
 * practices exist the moment it does. It seeds at TWO points: the default (lowest-id) workspace once
 * workspaces exist at startup ({@link WorkspacesInitializedEvent}), AND every newly-created workspace
 * ({@link WorkspaceCreatedEvent}) — otherwise a workspace created at runtime (via the API/UI, after boot)
 * would have no practices at all, and the practice catalog is a prerequisite for detection (a runnable agent
 * config must additionally be attached before detection actually runs). The catalog lives as data in
 * {@code resources/practices/default-catalog.json} so it stays editable without code changes, and every
 * row remains fully configurable afterwards through the normal practice/area CRUD endpoints.
 *
 * <p>Idempotent: an area that already exists in the workspace is skipped, so re-running (startup, after an
 * admin edit, or a create-event that races the startup seed) never overwrites configured state; a concurrent
 * double-seed is caught by the {@code uk_practice_workspace_slug} unique constraint and self-heals. Failures
 * are isolated from the rest of startup/creation, mirroring {@code DefaultAgentConfigSeeder}.
 */
@Component
class DefaultPracticeCatalogSeeder {

    private static final Logger log = LoggerFactory.getLogger(DefaultPracticeCatalogSeeder.class);

    private static final String CATALOG_RESOURCE = "practices/default-catalog.json";

    private final boolean enabled;
    private final JsonMapper objectMapper;
    private final PracticeAreaService areaService;
    private final PracticeService practiceService;
    private final PracticeAreaRepository areaRepository;
    private final PracticeRepository practiceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AsyncTaskExecutor monitoringExecutor;

    DefaultPracticeCatalogSeeder(
        @Value("${hephaestus.practices.seed-default-catalog:true}") boolean enabled,
        JsonMapper objectMapper,
        PracticeAreaService areaService,
        PracticeService practiceService,
        PracticeAreaRepository areaRepository,
        PracticeRepository practiceRepository,
        WorkspaceRepository workspaceRepository,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        this.areaService = areaService;
        this.practiceService = practiceService;
        this.areaRepository = areaRepository;
        this.practiceRepository = practiceRepository;
        this.workspaceRepository = workspaceRepository;
        this.monitoringExecutor = monitoringExecutor;
    }

    @EventListener(WorkspacesInitializedEvent.class)
    public void seed() {
        if (!enabled) {
            return;
        }
        // Deterministic target: the lowest-id workspace. findAll() has no ORDER BY, so without this sort the
        // seeded workspace would be whatever row Postgres returned first — arbitrary and inconsistent across
        // restarts when multiple workspaces exist (the startup listener bootstraps several).
        Workspace workspace = workspaceRepository
            .findAll()
            .stream()
            .min(Comparator.comparing(Workspace::getId, Comparator.nullsLast(Long::compareTo)))
            .orElse(null);
        if (workspace == null) {
            log.warn("Default practice catalog enabled but no workspace exists yet; skipping.");
            return;
        }
        seedInto(workspace, "startup");
    }

    /**
     * Seed the catalog into a workspace the moment it is created at runtime (after boot), so its practices
     * exist without waiting for a restart. Fired post-commit ({@link WorkspaceCreatedEvent}), so the workspace
     * row is visible; idempotent against the startup seed if the two ever race.
     *
     * <p>Runs OFF the request thread on {@code monitoringExecutor}: {@code WorkspaceCreatedEvent}'s contract is
     * fire-and-forget (the HTTP response is already sent), and seeding is ~75 sequential sub-transactions —
     * far too much to block workspace creation on. This mirrors the sibling listeners on the same event
     * ({@code GitLabWorkspaceInitializationService}, {@code WorkspaceActivationService}). The startup
     * {@link #seed()} path stays synchronous — it must complete during boot before any request traffic.
     */
    @EventListener(WorkspaceCreatedEvent.class)
    public void onWorkspaceCreated(WorkspaceCreatedEvent event) {
        if (!enabled) {
            return;
        }
        monitoringExecutor.submit(() ->
            workspaceRepository
                .findById(event.workspaceId())
                .ifPresent(workspace -> seedInto(workspace, "workspace-created"))
        );
    }

    private void seedInto(Workspace workspace, String occasion) {
        try {
            seedCatalog(workspace);
        } catch (RuntimeException e) {
            log.error("Default practice catalog seeding failed ({}); continuing.", occasion, e);
        }
    }

    private void seedCatalog(Workspace workspace) {
        WorkspaceContext ctx = WorkspaceContext.fromWorkspace(workspace, Set.of(), null);

        JsonNode catalog = readCatalog();
        int seededAreas = 0;
        int seededPractices = 0;
        for (JsonNode areaNode : catalog.path("areas")) {
            String areaSlug = areaNode.path("slug").asString();
            // Per-ROW idempotency (not per-area): create the area only if absent, but ALWAYS walk its practices
            // and create each only if absent. A per-area skip would, after a mid-area failure (area created, only
            // some practices seeded), permanently leave the remaining practices unseeded because the area exists.
            if (!areaRepository.existsByWorkspaceIdAndSlug(ctx.id(), areaSlug)) {
                areaService.createArea(
                    ctx,
                    areaSlug,
                    new AreaAttributes(
                        areaNode.path("name").asString(),
                        text(areaNode, "description"),
                        areaNode.path("displayOrder").asInt(),
                        text(areaNode, "icon"),
                        text(areaNode, "color")
                    )
                );
                seededAreas++;
            }

            for (JsonNode practiceNode : areaNode.path("practices")) {
                String practiceSlug = practiceNode.path("slug").asString();
                // Per-ROW resilience: one malformed catalog entry (e.g. an unknown artifactType that makes
                // WorkArtifact.valueOf throw) must skip only that row, not abort the rest of the catalog.
                try {
                    if (seedPractice(ctx, catalog, areaSlug, practiceNode, practiceSlug)) {
                        seededPractices++;
                    }
                } catch (RuntimeException e) {
                    log.error("Skipping malformed catalog practice '{}': {}", practiceSlug, e.getMessage());
                }
            }
        }
        if (seededAreas > 0 || seededPractices > 0) {
            log.info(
                "Seeded default practice catalog: {} areas, {} practices into workspace {}",
                seededAreas,
                seededPractices,
                workspace.getId()
            );
        }
    }

    /**
     * Seed a single practice row idempotently. Returns {@code true} when it created or (re-)bound a practice,
     * {@code false} when the practice already existed and was left untouched.
     *
     * <p>Resumable seeding: create + bind run in SEPARATE transactions, so a mid-seed failure can strand a
     * practice that exists but is unbound (area=NULL). A plain exists-then-skip guard would never re-bind it.
     * Instead, fetch-or-create, then bind only when the area is still null — so a half-seeded practice
     * self-heals on the next boot, while an admin who has intentionally bound (or unbound) a practice is never
     * overwritten.
     */
    private boolean seedPractice(
        WorkspaceContext ctx,
        JsonNode catalog,
        String areaSlug,
        JsonNode practiceNode,
        String practiceSlug
    ) {
        var existing = practiceRepository.findByWorkspaceIdAndSlug(ctx.id(), practiceSlug);
        if (existing.isPresent()) {
            if (existing.get().getArea() == null) {
                areaService.bindPractice(ctx, practiceSlug, areaSlug);
                return true;
            }
            // Otherwise already bound — respect any admin edits and do not overwrite.
            return false;
        }
        practiceService.createPractice(ctx, toCreateRequest(catalog, practiceNode));
        areaService.bindPractice(ctx, practiceSlug, areaSlug);
        return true;
    }

    private CreatePracticeRequestDTO toCreateRequest(JsonNode catalog, JsonNode practiceNode) {
        List<String> triggerEvents = new ArrayList<>();
        practiceNode.path("triggerEvents").forEach(t -> triggerEvents.add(t.asString()));
        WorkArtifact focus = WorkArtifact.valueOf(practiceNode.path("artifactType").asString());
        // A practice may opt into a non-default preamble (e.g. the code-judging PULL_REQUEST_CODE contract
        // for practices that read the diff) via a "preamble" key; otherwise the focus name is the key.
        String preambleKey = text(practiceNode, "preamble");
        if (preambleKey == null) {
            preambleKey = focus.name();
        }
        String slug = practiceNode.path("slug").asString();
        return new CreatePracticeRequestDTO(
            slug,
            practiceNode.path("name").asString(),
            triggerEvents,
            composeCriteria(catalog, preambleKey, practiceNode.path("criteria").asString()),
            loadPrecomputeScript(slug),
            focus,
            text(practiceNode, "whyItMatters"),
            text(practiceNode, "whatGoodLooksLike")
        );
    }

    /**
     * Loads the optional per-practice precompute script from {@code practices/precompute/<slug>.ts} on the
     * classpath. Returns {@code null} when a practice has no script — the common case (precompute is the
     * downstream Transform home only for practices whose grounding genuinely benefits from pre-staging facts
     * the runner can derive from {repo, diff, metadata}). The script emits hints/metrics/directions, never a
     * observation — the LLM still does the heavy lifting.
     */
    @Nullable
    private String loadPrecomputeScript(String slug) {
        var resource = new ClassPathResource("practices/precompute/" + slug + ".ts");
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not read precompute script for practice {}: {}", slug, e.getMessage());
            return null;
        }
    }

    /**
     * Prepends the shared per-focus evidence-contract preamble (authored once in
     * {@code criteriaPreambles.<FOCUS>}) to the practice-specific criteria, so every seeded practice —
     * and any future one of that focus — inherits the same artifact contract without restating it. The
     * preamble is reinforcing and explicitly subordinate to the inline criteria (it ends by deferring to
     * the practice-specific contract), so composition never weakens the validated detection logic. When
     * no preamble is configured for the key the criteria is stored verbatim, keeping older catalogs and
     * already-seeded workspaces byte-for-byte unchanged.
     */
    private static String composeCriteria(JsonNode catalog, String preambleKey, String criteria) {
        String preamble = text(catalog.path("criteriaPreambles"), preambleKey);
        if (preamble == null || preamble.isBlank()) {
            return criteria;
        }
        return preamble + "\n\n---\n\n" + criteria;
    }

    private JsonNode readCatalog() {
        try (InputStream in = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read default practice catalog: " + CATALOG_RESOURCE, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
