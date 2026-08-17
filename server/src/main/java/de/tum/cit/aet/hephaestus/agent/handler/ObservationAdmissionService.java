package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Java trust boundary between measurement and same-session feedback composition. */
@Service
public class ObservationAdmissionService {

    public static final String DIGEST_METADATA_KEY = "observation_admission_digest";

    private final AgentJobRepository jobs;
    private final ObservationRepository observations;
    private final PullRequestReviewHandler pullRequests;
    private final IssueReviewHandler issues;
    private final JsonMapper mapper;

    public ObservationAdmissionService(
        AgentJobRepository jobs,
        ObservationRepository observations,
        PullRequestReviewHandler pullRequests,
        IssueReviewHandler issues,
        JsonMapper mapper
    ) {
        this.jobs = jobs;
        this.observations = observations;
        this.pullRequests = pullRequests;
        this.issues = issues;
        this.mapper = mapper;
    }

    @Transactional
    public ObjectNode admit(UUID jobId, JsonNode submitted) {
        AgentJob job = jobs.findByIdWithWorkspaceForUpdate(jobId).orElseThrow();
        if (job.getStatus() != de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus.RUNNING) {
            throw new IllegalStateException("Observation admission requires a RUNNING job");
        }
        String digest = ProvenanceDigest.sha256Hex(canonical(submitted));
        String existing = job.getMetadata().path(DIGEST_METADATA_KEY).asString();
        if (!existing.isBlank()) {
            if (!existing.equals(digest)) throw new AdmissionConflictException();
            return response(job, existing, observations.findByAgentJobId(jobId));
        }
        switch (job.getJobType()) {
            case PULL_REQUEST_REVIEW -> pullRequests.admitObservations(job, submitted);
            case ISSUE_REVIEW -> issues.admitObservations(job, submitted);
            default -> throw new IllegalArgumentException("Job type does not admit review observations");
        }
        ((ObjectNode) job.getMetadata()).put(DIGEST_METADATA_KEY, digest);
        jobs.save(job);
        return response(job, digest, observations.findByAgentJobId(jobId));
    }

    private byte[] canonical(JsonNode submitted) {
        try {
            return mapper.writeValueAsBytes(submitted);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid observation payload", e);
        }
    }

    private ObjectNode response(AgentJob job, String digest, List<Observation> admitted) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("admissionDigest", digest);
        ArrayNode rows = root.putArray("observations");
        java.util.Map<String, java.util.TreeSet<Integer>> validLines =
            job.getJobType() == AgentJobType.PULL_REQUEST_REVIEW
                ? pullRequests.validDiffLines(job)
                : java.util.Map.of();
        admitted.forEach(o -> rows.add(project(o, validLines)));
        return root;
    }

    private ObjectNode project(Observation observation, java.util.Map<String, java.util.TreeSet<Integer>> validLines) {
        ObjectNode out = mapper.createObjectNode();
        out.put("id", observation.getId().toString());
        out.put("practiceSlug", observation.getPractice().getSlug());
        out.put("summary", observation.getSummary());
        out.put("presence", observation.getPresence().name());
        if (observation.getAssessment() != null) out.put("assessment", observation.getAssessment().name());
        if (observation.getSeverity() != null) out.put("severity", observation.getSeverity().name());
        out.put("evidenceRationale", observation.getEvidenceRationale());
        out.set("evidence", observation.getEvidence());
        ArrayNode citations = out.putArray("citations");
        JsonNode source = observation.getEvidence() == null ? null : observation.getEvidence().path("citations");
        if (source != null && source.isArray()) {
            int index = 0;
            for (JsonNode citation : source) {
                ObjectNode copy = citations.addObject();
                copy.put("index", index++);
                citation.properties().forEach(entry -> copy.set(entry.getKey(), entry.getValue()));
                boolean anchorable =
                    "scm.pull-request.diff".equals(citation.path("sourceKind").asString()) &&
                    citation.path("path").isTextual() &&
                    citation.path("startLine").isIntegralNumber() &&
                    validLines
                        .getOrDefault(citation.path("path").asString(), new java.util.TreeSet<>())
                        .contains(citation.path("startLine").asInt());
                copy.put("anchorable", anchorable);
            }
        }
        out.put("anchorable", citations.valueStream().anyMatch(c -> c.path("anchorable").asBoolean()));
        return out;
    }

    public static class AdmissionConflictException extends RuntimeException {}
}
