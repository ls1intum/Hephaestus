package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Injects the practice registry, criteria, and precompute scripts into a job's workspace under
 * {@code .practices/} and {@code .precompute/}. Shared by every {@link de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler}
 * regardless of artifact — the catalog is per-job (workspace-active practices), not provider-shaped,
 * so it does not live behind the {@code ContentProvider} SPI.
 *
 * <p>Filters by {@link WorkArtifact}: a PR job injects only PR-focus practices, an issue job only
 * issue-focus practices — so a diff-anchored practice never reaches an issue (and vice-versa).
 *
 * <p>Package-private; instantiated as a {@code @Bean} in {@link JobTypeHandlerConfiguration}.
 */
class PracticeCatalogInjector {

    private static final Logger log = LoggerFactory.getLogger(PracticeCatalogInjector.class);

    private final JsonMapper objectMapper;
    private final PracticeRepository practiceRepository;

    PracticeCatalogInjector(JsonMapper objectMapper, PracticeRepository practiceRepository) {
        this.objectMapper = objectMapper;
        this.practiceRepository = practiceRepository;
    }

    /**
     * Inject the {@code focus}-scoped active practice catalog for {@code job}'s workspace into
     * {@code files}.
     *
     * @throws JobPreparationException if the job has no workspace, no matching active practices, or a
     *     slug violates the workspace ABI pattern.
     */
    void inject(Map<String, byte[]> files, AgentJob job, WorkArtifact focus) {
        if (job.getWorkspace() == null) {
            throw new JobPreparationException("Job has no workspace: jobId=" + job.getId());
        }
        Long workspaceId = job.getWorkspace().getId();
        List<Practice> practices = practiceRepository.findByWorkspaceIdAndActiveTrueAndFocusArtifact(
            workspaceId,
            focus
        );
        if (practices.isEmpty()) {
            throw new JobPreparationException(
                "No active " + focus + " practices for workspace: workspaceId=" + workspaceId + ", jobId=" + job.getId()
            );
        }
        // Defense in depth: slugs are interpolated into filesystem paths below; reject any value that
        // doesn't match the ABI pattern before it can escape ".practices/" / ".precompute/".
        for (Practice p : practices) {
            String slug = p.getSlug();
            if (slug == null || !WorkspaceAbi.PRACTICE_SLUG.matcher(slug).matches()) {
                throw new JobPreparationException(
                    "Practice slug fails ABI pattern " + WorkspaceAbi.PRACTICE_SLUG.pattern() + ": " + slug
                );
            }
        }

        StringBuilder indexJson = new StringBuilder("[\n");
        for (int i = 0; i < practices.size(); i++) {
            Practice p = practices.get(i);
            if (i > 0) indexJson.append(",\n");
            indexJson
                .append("  {\"slug\": \"")
                .append(escapeJson(p.getSlug()))
                .append("\", \"name\": \"")
                .append(escapeJson(p.getName()))
                .append("\", \"category\": \"")
                .append(escapeJson(p.getCategory() != null ? p.getCategory() : ""))
                .append("\"}");
        }
        indexJson.append("\n]");
        files.put(WorkspaceAbi.PRACTICES_PREFIX + "index.json", indexJson.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder bundle = new StringBuilder();
        for (Practice p : practices) {
            String criteria = p.getCriteria();
            files.put(WorkspaceAbi.PRACTICES_PREFIX + p.getSlug() + ".md", criteria.getBytes(StandardCharsets.UTF_8));
            bundle.append("# ").append(p.getSlug()).append("\n\n").append(criteria).append("\n\n---\n\n");
        }
        files.put(
            WorkspaceAbi.PRACTICES_PREFIX + "all-criteria.md",
            bundle.toString().getBytes(StandardCharsets.UTF_8)
        );

        files.put(WorkspaceAbi.ANALYSIS_PRACTICES_PREFIX + ".gitkeep", new byte[0]);

        int precomputeCount = 0;
        for (Practice p : practices) {
            String script = p.getPrecomputeScript();
            if (script != null && !script.isBlank()) {
                files.put(
                    WorkspaceAbi.PRECOMPUTE_PREFIX + "practices/" + p.getSlug() + ".ts",
                    script.getBytes(StandardCharsets.UTF_8)
                );
                precomputeCount++;
            }
        }

        log.info(
            "Injected practice catalog: {} {} practices ({} with precompute), workspaceId={}, jobId={}",
            practices.size(),
            focus,
            precomputeCount,
            workspaceId,
            job.getId()
        );
    }

    /** JSON string escaping via Jackson (handles all control characters correctly). */
    private String escapeJson(String s) {
        try {
            String quoted = objectMapper.writeValueAsString(s);
            return quoted.substring(1, quoted.length() - 1);
        } catch (JacksonException e) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        }
    }
}
