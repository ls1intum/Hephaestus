package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPrice;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPriceRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.PricingMode;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.handler.AgentHandlerTestDoubles;
import de.tum.cit.aet.hephaestus.agent.usage.LlmAdmissionService;
import de.tum.cit.aet.hephaestus.practices.spi.BoundModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewMethod;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewWriteGuard;
import de.tum.cit.aet.hephaestus.testconfig.LlmCatalogTestFixtures;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.RecordingScmEventListener;
import de.tum.cit.aet.hephaestus.testconfig.SharedTestDoubles;
import de.tum.cit.aet.hephaestus.testconfig.StubInstallationRepositoryEnumerator;
import de.tum.cit.aet.hephaestus.testconfig.TestAsyncConfiguration;
import de.tum.cit.aet.hephaestus.testconfig.TestSecurityConfig;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceEchoControllers;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient(timeout = "30s")
@Import({
    TestSecurityConfig.class,
    TestAsyncConfiguration.class,
    RecordingScmEventListener.class,
    StubInstallationRepositoryEnumerator.class,
    SharedTestDoubles.class,
    AgentHandlerTestDoubles.class,
    WorkspaceEchoControllers.ScopedEchoController.class,
    WorkspaceEchoControllers.WorkspaceContextEchoController.class
})
@TestPropertySource(
        properties = {"hephaestus.features.flags.gitlab-workspace-creation=true", "hephaestus.llm.display-currency="})
@Tag("database")
class ReviewMethodPersistenceDatabaseTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        var postgres = PostgreSQLTestContainer.getInstance();
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private LlmConnectionRepository connections;

    @Autowired
    private LlmModelRepository models;

    @Autowired
    private LlmModelPriceRepository prices;

    @Autowired
    private WorkspaceAgentBindingRepository bindings;

    @Autowired
    private ReviewJobRepository jobs;

    @Autowired
    private ReviewJobAssignments assignments;

    @Autowired
    private ReviewAssignmentStore assignmentStore;

    @Autowired
    private ReviewWriteGuard guard;

    @Autowired
    private LlmModelResolver resolver;

    @Autowired
    private ReviewMethodVerifier methods;

    @Autowired
    private LlmAdmissionService admission;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ReviewSnapshotQuery snapshots;

    @Test
    void shouldEnqueueTheUnchangedMethodAfterDatabaseJsonPersistence() {
        new ResourceDatabasePopulator(new ClassPathResource("db/practice-native/result-parent.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
        jdbc.update("""
            UPDATE review_item SET detail=jsonb_build_object('developerId',978003)
            WHERE workspace_id=978001 AND review_id='00000000-0000-0000-0000-000000978011'
              AND practice_id=978004 AND item_id=978006
            """);
        var workspace = workspaces.findById(978001L).orElseThrow();
        var slug = "method-" + UUID.randomUUID();
        var connection = connections.saveAndFlush(LlmCatalogTestFixtures.connection(slug));
        var model = models.saveAndFlush(LlmCatalogTestFixtures.model(connection, slug, slug));
        var price = new LlmModelPrice();
        price.setModel(model);
        price.setPricingMode(PricingMode.NO_CHARGE);
        price.setNote("No charge for the declared test model.");
        price.setEffectiveFrom(Instant.now());
        prices.saveAndFlush(price);
        var binding = new WorkspaceAgentBinding();
        binding.setWorkspace(workspace);
        binding.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        binding.setDataHandlingTier(DataHandlingTier.UNDECLARED);
        binding.setInstanceModel(model);
        binding.setEnabled(true);
        var savedBinding = bindings.saveAndFlush(binding);
        var frozen = transactions.execute(status -> methods.freeze(savedBinding));
        var slots = List.of(
                new BoundModelSlot("review", ModelKind.CHAT, frozen),
                new BoundModelSlot("chat", ModelKind.CHAT, null),
                new BoundModelSlot("decision", ModelKind.DECISION, null),
                new BoundModelSlot("embedding", ModelKind.EMBEDDING, null),
                new BoundModelSlot("reranking", ModelKind.RERANKING, null));
        String digest = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT review_model_digest(CAST(? AS jsonb))", String.class, mapper.writeValueAsString(slots)));
        var method = new ReviewMethod(1, digest, slots);
        var reviewId = UUID.fromString("00000000-0000-0000-0000-000000978011");
        jdbc.update(
                "UPDATE review_practice SET method=CAST(? AS jsonb) WHERE workspace_id=? AND review_id=? AND"
                        + " practice_id=?",
                mapper.writeValueAsString(method),
                workspace.getId(),
                reviewId,
                978004L);
        var stored = mapper.readValue(
                Objects.requireNonNull(jdbc.queryForObject(
                        "SELECT method::text FROM review_practice WHERE workspace_id=? AND review_id=? AND"
                                + " practice_id=?",
                        String.class,
                        workspace.getId(),
                        reviewId,
                        978004L)),
                ReviewMethod.class);
        assertThat(stored).isEqualTo(method);
        var policy = mock(ReviewMemberAiPolicy.class);
        when(policy.binding(any())).thenReturn(Optional.of(savedBinding));
        var queue = new ReviewJobService(
                jobs, guard, assignments, assignmentStore, policy, resolver, mapper, admission, methods);
        UUID jobId = Objects.requireNonNull(transactions.execute(status -> {
            var review = snapshots.find(workspace.getId(), reviewId).orElseThrow();
            return queue.enqueue(review, 978006L, List.of(new ReviewAssignmentId(reviewId, 978004L, 978006L)));
        }));
        assertThat(jobs.findByIdAndWorkspaceId(jobId, workspace.getId()))
                .get()
                .extracting(ReviewJob::getStatus, ReviewJob::getReviewId)
                .containsExactly(ReviewJobStatus.QUEUED, reviewId);
        assertThat(jdbc.queryForObject(
                        "SELECT job_id FROM review_item WHERE workspace_id=? AND review_id=? AND practice_id=? AND"
                                + " item_id=?",
                        UUID.class,
                        workspace.getId(),
                        reviewId,
                        978004L,
                        978006L))
                .isEqualTo(jobId);
        jdbc.update(
                "UPDATE review_job SET"
                    + " status='CANCELLED',completed_at=now(),cancellation_reason='CANCELLED_BY_REQUESTER' WHERE id=?"
                    + " AND workspace_id=?",
                jobId,
                workspace.getId());
        jdbc.update("""
            WITH changed AS (
                SELECT workspace_id, review_id, practice_id,
                       jsonb_set(method,'{models,0,model,options,directModelTokensPerAttempt}','200001'::jsonb) AS method
                FROM review_practice WHERE workspace_id=? AND review_id=? AND practice_id=?
            )
            UPDATE review_practice rp
            SET method=jsonb_set(changed.method,'{modelDigest}',to_jsonb(review_model_digest(changed.method->'models')))
            FROM changed WHERE rp.workspace_id=changed.workspace_id AND rp.review_id=changed.review_id
                         AND rp.practice_id=changed.practice_id
            """, workspace.getId(), reviewId, 978004L);
        assertThatThrownBy(() -> transactions.execute(status -> {
                    var review = snapshots.find(workspace.getId(), reviewId).orElseThrow();
                    return queue.enqueue(review, 978006L, List.of(new ReviewAssignmentId(reviewId, 978004L, 978006L)));
                }))
                .isInstanceOf(ReviewJobStateConflictException.class);
        assertThat(jobs.findByIdAndWorkspaceId(jobId, workspace.getId()))
                .get()
                .extracting(ReviewJob::getStatus)
                .isEqualTo(ReviewJobStatus.CANCELLED);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM review_job WHERE workspace_id=? AND review_id=?",
                        Long.class,
                        workspace.getId(),
                        reviewId))
                .isEqualTo(1L);
    }
}
