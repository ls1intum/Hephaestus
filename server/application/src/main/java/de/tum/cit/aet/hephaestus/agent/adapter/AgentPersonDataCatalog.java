package de.tum.cit.aet.hephaestus.agent.adapter;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJobLifecycleService;
import de.tum.cit.aet.hephaestus.core.privacy.spi.JdbcPersonDataStore;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCatalog;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataContributor;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataSelection;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataStores;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonEvidenceErasure;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
@PersonDataStores({"review_job", "review_composition_basis", "llm_usage_event", "instance_llm_settings"})
public class AgentPersonDataCatalog implements PersonDataCatalog {
    private static final String SUBJECT_JOBS = """
        SELECT j.id FROM review_job j JOIN review r ON (r.id,r.workspace_id)=(j.review_id,j.workspace_id)
        WHERE r.requested_by_id=:account
          OR EXISTS(SELECT 1 FROM result x WHERE (x.review_id,x.workspace_id)=(r.id,r.workspace_id)
            AND x.developer_id=:account)
          OR EXISTS(SELECT 1 FROM feedback f WHERE (f.review_id,f.workspace_id)=(r.id,r.workspace_id)
            AND f.developer_id=:account)
        """;
    private static final String COPYING_JOBS = """
        SELECT j.id FROM review_job j JOIN review r ON (r.id,r.workspace_id)=(j.review_id,j.workspace_id)
        WHERE j.id=ANY(:derivedJobs)
          OR EXISTS(SELECT 1 FROM item i WHERE i.workspace_id=j.workspace_id
            AND (i.id=r.item_id OR EXISTS(SELECT 1 FROM item reviewed
              WHERE reviewed.workspace_id=r.workspace_id AND reviewed.id=r.item_id AND reviewed.parent_id=i.id)
              OR EXISTS(SELECT 1 FROM review_item a
              WHERE (a.review_id,a.workspace_id)=(r.id,r.workspace_id) AND a.item_id=i.id))
            AND ((i.kind IN ('scm.issue','scm.pull_request') AND i.source_id=ANY(:artifacts))
              OR (i.kind='chat.thread' AND i.source_id=ANY(:conversations))
              OR (i.kind='docs.document' AND i.source_id=ANY(:documents))))
        """;
    private static final String SELECTED_JOBS = SUBJECT_JOBS + " UNION " + COPYING_JOBS;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final List<PersonEvidenceErasure> evidenceCopies;
    private final ReviewJobLifecycleService lifecycle;

    @Override
    public List<PersonDataContributor> contributors() {
        return List.of(
                new JdbcPersonDataStore(
                        jdbc,
                        mapper,
                        "review_composition_basis",
                        "review_composition_basis",
                        "t.job_id IN (" + SELECTED_JOBS + ")",
                        "job_id,workspace_id,admission_attempt,admission_digest,basis_digest,basis_version,copy_id,expires_at",
                        "job_id,workspace_id",
                        "",
                        -82),
                new AgentJobStore(),
                new JdbcPersonDataStore(
                        jdbc,
                        mapper,
                        "llm_usage_event",
                        "llm_usage_event",
                        "t.source_id IN (" + SELECTED_JOBS
                                + ") OR t.review_id IN (SELECT review_id FROM review_job WHERE id IN ("
                                + SELECTED_JOBS
                                + ")) OR (t.source_type='MENTOR_TURN' AND t.source_id IN (SELECT m.id FROM"
                                + " chat_message m JOIN chat_thread c ON c.id=m.thread_id WHERE"
                                + " c.account_id=:account))",
                        "id,workspace_id,job_type,source_type,source_id,source_attempt,review_id,model,input_tokens,output_tokens,cache_read_tokens,cache_write_tokens,reasoning_tokens,search_units,total_calls,unreported_calls,cost_usd,pricing_state,funding_source,applied_price_id,applied_workspace_model_id,applied_per_1m_input_usd,applied_per_1m_output_usd,applied_per_1m_cache_read_usd,applied_per_1m_cache_write_usd,occurred_at,usage_provenance",
                        "id,workspace_id",
                        "source_id=gen_random_uuid(),review_id=NULL",
                        -85),
                new JdbcPersonDataStore(
                        jdbc,
                        mapper,
                        "instance_llm_settings",
                        "instance_llm_settings",
                        "t.updated_by_account_id=:account",
                        "id,allowed_egress_hosts,allow_workspace_connections,updated_at,updated_by_account_id",
                        "id",
                        "updated_by_account_id=NULL",
                        400));
    }

    private record JobRow(UUID id, long workspaceId, ReviewJobStatus status, boolean evidence) {}

    private final class AgentJobStore extends JdbcPersonDataStore {
        AgentJobStore() {
            super(
                    jdbc,
                    mapper,
                    "review_job",
                    "review_job",
                    "t.id IN (" + SELECTED_JOBS + ")",
                    "id,workspace_id,review_id,status,attempt,submitted_at,started_at,completed_at,processing_status",
                    "id,workspace_id",
                    "",
                    -80);
        }

        private List<JobRow> jobs(PersonDataSelection selection) {
            if (selection.rows().isEmpty()) return List.of();
            var ids = selection.rows().stream()
                    .map(row -> UUID.fromString(row.columns().get("id")))
                    .toArray();
            return jdbc.query(
                    "SELECT id,workspace_id,status,capture IS NOT NULL AS evidence FROM review_job WHERE id=ANY(:ids)",
                    Map.of("ids", new SqlArrayValue("uuid", ids)),
                    (row, number) -> new JobRow(
                            Objects.requireNonNull(row.getObject("id", UUID.class)),
                            row.getLong("workspace_id"),
                            ReviewJobStatus.valueOf(Objects.requireNonNull(row.getString("status"))),
                            row.getBoolean("evidence")));
        }

        @Override
        public void prepareErasure(PersonDataSelection selection) {
            for (var row : jobs(selection)) {
                if (row.status() == ReviewJobStatus.QUEUED || row.status() == ReviewJobStatus.RUNNING)
                    lifecycle.cancel(row.workspaceId(), row.id());
            }
            eraseEvidence(selection);
        }

        private void eraseEvidence(PersonDataSelection selection) {
            if (selection.rows().isEmpty()) return;
            Set<UUID> ids = selection.rows().stream()
                    .map(row -> UUID.fromString(row.columns().get("id")))
                    .collect(Collectors.toUnmodifiableSet());
            if (evidenceCopies.isEmpty() && jobs(selection).stream().anyMatch(JobRow::evidence))
                throw new IllegalStateException("Evidence-copy erasure acknowledgement is unavailable");
            evidenceCopies.forEach(copy -> copy.eraseJobEvidence(ids));
        }

        @Override
        public long erase(PersonDataSelection selection) {
            if (selection.rows().isEmpty()) return 0;
            return super.erase(selection);
        }
    }
}
