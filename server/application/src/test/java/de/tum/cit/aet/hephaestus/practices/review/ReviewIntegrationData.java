package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.integration.core.item.Item;
import de.tum.cit.aet.hephaestus.integration.core.item.ItemRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionService;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Problem;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.model.ResultMark;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultMarkRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewItem;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewPractice;
import de.tum.cit.aet.hephaestus.practices.spi.BoundModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.FrozenModel;
import de.tum.cit.aet.hephaestus.practices.spi.HostCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.IntroducedBasis;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewItemState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewMethod;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewPracticeStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@TestComponent
@RequiredArgsConstructor
public class ReviewIntegrationData {
    private final WorkspaceRepository workspaces;
    private final AccountRepository accounts;
    private final MembershipRepository memberships;
    private final ItemRepository items;
    private final PracticeVersionService versions;
    private final ReviewRepository reviews;
    private final ReviewPracticeRepository assignments;
    private final ReviewItemRepository reviewItems;
    private final ResultRepository results;
    private final ProblemRepository problems;
    private final ResultMarkRepository marks;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public record Scenario(Workspace workspace, Account account, Practice practice, Item item) {}

    @Transactional
    public Scenario scenario() {
        return scenario(DefinitionTestFixtures.definition());
    }

    @Transactional
    public Scenario scenario(PracticeDefinition definition) {
        var workspace = WorkspaceTestFixtures.activeWorkspace("s5-" + UUID.randomUUID());
        workspace.getFeatures().setPracticesEnabled(true);
        workspaces.saveAndFlush(workspace);
        var account = accounts.saveAndFlush(new Account("S5 developer"));
        var membership = new Membership();
        membership.setId(new Membership.Id(workspace.getId(), account.getId()));
        membership.setWorkspace(workspace);
        membership.setRole(Membership.WorkspaceRole.OWNER);
        memberships.saveAndFlush(membership);
        var item = new Item();
        item.setWorkspaceId(workspace.getId());
        item.setKind(new ItemKind("scm.pull_request"));
        item.setSourceId(ThreadLocalRandom.current().nextLong(1000000000000L, Long.MAX_VALUE));
        item.setCreatedAt(Instant.now().minusSeconds(3600));
        items.saveAndFlush(item);
        var practice = versions.create(
                workspace,
                "s5-practice-" + UUID.randomUUID().toString().substring(0, 8),
                item.getKind(),
                definition,
                null,
                0,
                account.getId());
        return new Scenario(workspace, account, practice, item);
    }

    @Transactional
    public Account member(long workspaceId) {
        var workspace = workspaces.findById(workspaceId).orElseThrow();
        var account = accounts.saveAndFlush(new Account("Another developer"));
        var membership = new Membership();
        membership.setId(new Membership.Id(workspaceId, account.getId()));
        membership.setWorkspace(workspace);
        membership.setRole(Membership.WorkspaceRole.MEMBER);
        memberships.saveAndFlush(membership);
        return account;
    }

    @Transactional
    public Result result(Scenario s, ResultOutcome outcome, Instant at) {
        var review = Review.builder()
                .id(UUID.randomUUID())
                .workspaceId(s.workspace().getId())
                .itemId(s.item().getId())
                .kind(ReviewKind.ITEM)
                .origin(ReviewOrigin.REQUEST)
                .requestedById(s.account().getId())
                .status(ReviewStatus.COMPLETE)
                .itemVersion("version-" + UUID.randomUUID())
                .orderedAt(at)
                .createdAt(at)
                .admittedAt(at)
                .completedAt(at)
                .build();
        reviews.saveAndFlush(review);
        var slots = List.of(
                new BoundModelSlot("chat", ModelKind.CHAT, null),
                new BoundModelSlot("decision", ModelKind.DECISION, null),
                new BoundModelSlot("embedding", ModelKind.EMBEDDING, null),
                new BoundModelSlot("reranking", ModelKind.RERANKING, null),
                new BoundModelSlot(
                        "review",
                        ModelKind.CHAT,
                        new FrozenModel("instance:1", "OPENAI_COMPLETIONS", "test-model", null, Map.of())));
        String digest = jdbc.sql("SELECT review_model_digest(CAST(:models AS jsonb))")
                .param("models", json.writeValueAsString(slots))
                .query(String.class)
                .single();
        assignments.saveAndFlush(ReviewPractice.builder()
                .workspaceId(s.workspace().getId())
                .reviewId(review.getId())
                .practiceId(s.practice().getId())
                .practiceVersionId(s.practice().getCurrentVersion().getId())
                .status(ReviewPracticeStatus.DONE)
                .itemsListed(1)
                .listingComplete(true)
                .captureDecision(json.createObjectNode())
                .method(new ReviewMethod(1, digest, slots))
                .build());
        var evidence = json.readTree("""
            {"citations":[{"sourceKind":"scm.pull-request.diff","sourcePath":"inputs/sources/scm/diff.patch","path":"src/Main.java","side":"NEW","revision":"commit-1","startLine":1,"endLine":1,"quote":"changed();","verification":{"status":"VERIFIED","artifactSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","quoteSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}}],"openQuestions":[]}
            """);
        var result = Result.builder()
                .id(UUID.randomUUID())
                .workspaceId(s.workspace().getId())
                .reviewId(review.getId())
                .practiceId(s.practice().getId())
                .practiceVersionId(s.practice().getCurrentVersion().getId())
                .itemId(s.item().getId())
                .itemVersion(review.getItemVersion())
                .developerId(s.account().getId())
                .outcome(outcome)
                .summary("Recorded result")
                .explanation("The captured change supports this result.")
                .severity(outcome == ResultOutcome.NOT_MET ? Severity.MAJOR : null)
                .evidence(evidence)
                .coverage(json.valueToTree(new HostCoverage(true, List.of(), 1, 0, 0, List.of())))
                .recordedAt(at)
                .build();
        results.save(result);
        if (outcome == ResultOutcome.NOT_MET)
            problems.save(Problem.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(s.workspace().getId())
                    .resultId(result.getId())
                    .position(0)
                    .key("problem-1")
                    .severity(Severity.MAJOR)
                    .summary("A recorded problem")
                    .explanation("The captured change introduces the problem.")
                    .citations(evidence.get("citations"))
                    .basis(json.valueToTree(new IntroducedBasis(0, "The change introduces this problem.")))
                    .nextStep("Adjust the changed branch.")
                    .build());
        reviewItems.save(ReviewItem.builder()
                .workspaceId(s.workspace().getId())
                .reviewId(review.getId())
                .practiceId(s.practice().getId())
                .itemId(s.item().getId())
                .itemVersion(review.getItemVersion())
                .state(ReviewItemState.JUDGED)
                .resultId(result.getId())
                .attemptedAt(at)
                .build());
        results.flush();
        return result;
    }

    @Transactional
    public ResultMark mark(Result result, ResultMarkKind kind, long account) {
        return marks.saveAndFlush(ResultMark.builder()
                .id(UUID.randomUUID())
                .workspaceId(result.getWorkspaceId())
                .resultId(result.getId())
                .kind(kind)
                .reason("Recorded review decision")
                .createdById(account)
                .createdAt(result.getRecordedAt().plusSeconds(1))
                .build());
    }

    @Transactional
    public UUID job(Result result) {
        var id = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO review_job(id,review_id,workspace_id,status,attempt,processing_status,trace_id,input,config_snapshot,completed_at)
            VALUES(:id,:review,:ws,'COMPLETED',1,'DONE',:trace,CAST(:input AS jsonb),'{}'::jsonb,:at)
            """)
                .param("id", id)
                .param("review", result.getReviewId())
                .param("ws", result.getWorkspaceId())
                .param("trace", id.toString().replace("-", ""))
                .param(
                        "input",
                        json.writeValueAsString(Map.of(
                                "itemId",
                                result.getItemId(),
                                "assignments",
                                List.of(new ReviewAssignmentId(
                                        result.getReviewId(), result.getPracticeId(), result.getItemId())))))
                .param("at", result.getRecordedAt())
                .update();
        return id;
    }

    @Transactional
    public void end(Result result, Instant at) {
        var saved = results.findByIdAndWorkspaceId(result.getId(), result.getWorkspaceId())
                .orElseThrow();
        saved.end(ResultEndReason.REPLACED, at);
        results.saveAndFlush(saved);
    }

    public static String memberToken(Scenario scenario) {
        return "mock-jwt-member-" + scenario.account().getId();
    }
}
