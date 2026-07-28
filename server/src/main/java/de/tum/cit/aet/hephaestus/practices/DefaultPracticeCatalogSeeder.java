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
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seeds {@code practices/default-catalog.json} into every workspace at startup and when a workspace is
 * created.
 *
 * <p>Existing practice fields are not overwritten. Unbound defaults are reattached so interrupted seeds
 * can recover.
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
    private final AsyncTaskExecutor taskExecutor;

    DefaultPracticeCatalogSeeder(
        @Value("${hephaestus.practices.seed-default-catalog:true}") boolean enabled,
        JsonMapper objectMapper,
        PracticeAreaService areaService,
        PracticeService practiceService,
        PracticeAreaRepository areaRepository,
        PracticeRepository practiceRepository,
        WorkspaceRepository workspaceRepository,
        @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) AsyncTaskExecutor taskExecutor
    ) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        this.areaService = areaService;
        this.practiceService = practiceService;
        this.areaRepository = areaRepository;
        this.practiceRepository = practiceRepository;
        this.workspaceRepository = workspaceRepository;
        this.taskExecutor = taskExecutor;
    }

    @EventListener(WorkspacesInitializedEvent.class)
    public void seed() {
        if (!enabled) {
            return;
        }
        try {
            workspaceRepository
                .findAll()
                .stream()
                .sorted(Comparator.comparing(Workspace::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(this::seedCatalogSafely);
        } catch (RuntimeException e) {
            log.error("Could not load workspaces for default practice catalog reconciliation", e);
        }
    }

    @EventListener(WorkspaceCreatedEvent.class)
    public void onWorkspaceCreated(WorkspaceCreatedEvent event) {
        if (!enabled) {
            return;
        }
        try {
            taskExecutor.execute(() -> {
                try {
                    workspaceRepository.findById(event.workspaceId()).ifPresent(this::seedCatalogSafely);
                } catch (RuntimeException e) {
                    log.error(
                        "Could not load workspace {} for default practice catalog seeding",
                        event.workspaceId(),
                        e
                    );
                }
            });
        } catch (RuntimeException e) {
            log.error("Could not schedule default practice catalog seeding: workspaceId={}", event.workspaceId(), e);
        }
    }

    private void seedCatalogSafely(Workspace workspace) {
        try {
            seedCatalog(workspace);
        } catch (RuntimeException e) {
            log.error("Default practice catalog seeding failed: workspaceId={}", workspace.getId(), e);
        }
    }

    private void seedCatalog(Workspace workspace) {
        WorkspaceContext ctx = WorkspaceContext.fromWorkspace(workspace, Set.of(), null);

        JsonNode catalog = readCatalog();
        int seededAreas = 0;
        int seededPractices = 0;
        for (JsonNode areaNode : catalog.path("areas")) {
            String areaSlug = areaNode.path("slug").asString();
            // Walk existing areas too so an interrupted seed can add missing practices.
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

    /** Returns whether the practice was created or an unbound default was reattached. */
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
        // A practice may opt into a non-default preamble via a "preamble" key; otherwise the focus name is the key.
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
     * classpath; {@code null} when a practice has no script (the common case). The script emits
     * hints/metrics, never an observation — the LLM still does the heavy lifting.
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
     * Prepends the shared evidence-contract preamble ({@code criteriaPreambles.<KEY>}) to the
     * practice-specific criteria so every practice of a focus inherits the same artifact contract without
     * restating it. The preamble defers to the inline criteria, so composition never weakens the validated
     * detection logic. When no preamble is configured the criteria is stored verbatim.
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
