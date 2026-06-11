package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.FocusArtifact;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seeds a grounded default practice catalog (process-level goals + their practices) into the default
 * workspace once workspaces exist ({@link WorkspacesInitializedEvent}). The catalog lives as data in
 * {@code resources/practices/default-catalog.json} so it stays editable without code changes, and every
 * row remains fully configurable afterwards through the normal practice/goal CRUD endpoints.
 *
 * <p>Idempotent: a goal that already exists in the workspace is skipped, so re-running on startup (or
 * after an admin has edited the catalog) never overwrites configured state. Failures are isolated from
 * the rest of startup, mirroring {@code DefaultAgentConfigSeeder}.
 */
@Component
class DefaultPracticeCatalogSeeder {

    private static final Logger log = LoggerFactory.getLogger(DefaultPracticeCatalogSeeder.class);

    private static final String CATALOG_RESOURCE = "practices/default-catalog.json";

    private final boolean enabled;
    private final JsonMapper objectMapper;
    private final PracticeGoalService goalService;
    private final PracticeService practiceService;
    private final PracticeGoalRepository goalRepository;
    private final WorkspaceRepository workspaceRepository;

    DefaultPracticeCatalogSeeder(
        @Value("${hephaestus.practices.seed-default-catalog:true}") boolean enabled,
        JsonMapper objectMapper,
        PracticeGoalService goalService,
        PracticeService practiceService,
        PracticeGoalRepository goalRepository,
        WorkspaceRepository workspaceRepository
    ) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        this.goalService = goalService;
        this.practiceService = practiceService;
        this.goalRepository = goalRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @EventListener(WorkspacesInitializedEvent.class)
    public void seed() {
        if (!enabled) {
            return;
        }
        try {
            seedCatalog();
        } catch (RuntimeException e) {
            log.error("Default practice catalog seeding failed; continuing startup.", e);
        }
    }

    private void seedCatalog() {
        Workspace workspace = workspaceRepository.findAll().stream().findFirst().orElse(null);
        if (workspace == null) {
            log.warn("Default practice catalog enabled but no workspace exists yet; skipping.");
            return;
        }
        WorkspaceContext ctx = WorkspaceContext.fromWorkspace(workspace, Set.of(), null);

        JsonNode catalog = readCatalog();
        int seededGoals = 0;
        int seededPractices = 0;
        for (JsonNode goalNode : catalog.path("goals")) {
            String goalSlug = goalNode.path("slug").asString();
            if (goalRepository.existsByWorkspaceIdAndSlug(ctx.id(), goalSlug)) {
                // Already present — respect any admin edits and do not overwrite.
                continue;
            }
            goalService.createGoal(ctx, goalSlug, goalNode.path("name").asString(), text(goalNode, "description"));
            goalService.updateGoal(ctx, goalSlug, null, null, goalNode.path("displayOrder").asInt());
            seededGoals++;

            for (JsonNode practiceNode : goalNode.path("practices")) {
                String practiceSlug = practiceNode.path("slug").asString();
                practiceService.createPractice(ctx, toCreateRequest(practiceNode));
                goalService.bindPractice(ctx, practiceSlug, goalSlug);
                seededPractices++;
            }
        }
        if (seededGoals > 0) {
            log.info(
                "Seeded default practice catalog: {} goals, {} practices into workspace {}",
                seededGoals,
                seededPractices,
                workspace.getId()
            );
        }
    }

    private CreatePracticeRequestDTO toCreateRequest(JsonNode practiceNode) {
        List<String> triggerEvents = new ArrayList<>();
        practiceNode.path("triggerEvents").forEach(t -> triggerEvents.add(t.asString()));
        FocusArtifact focus = FocusArtifact.valueOf(practiceNode.path("focusArtifact").asString());
        return new CreatePracticeRequestDTO(
            practiceNode.path("slug").asString(),
            practiceNode.path("name").asString(),
            null,
            triggerEvents,
            practiceNode.path("criteria").asString(),
            null,
            focus
        );
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
