package de.tum.cit.aet.hephaestus.practices.profile;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.item.Item;
import de.tum.cit.aet.hephaestus.integration.core.item.ItemRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionService;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class PracticeProfileReviewIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ReviewIntegrationData data;

    @Autowired
    WebTestClient http;

    @MockitoBean
    EvidenceAuthorization evidenceAuthorization;

    @BeforeEach
    void permitStoredEvidence() {
        Mockito.when(evidenceAuthorization.permitsAll(
                        ArgumentMatchers.anyLong(), ArgumentMatchers.anyCollection(), ArgumentMatchers.any()))
                .thenAnswer(i -> {
                    long ws = i.getArgument(0);
                    Collection<Result> rows = i.getArgument(1);
                    return rows.stream()
                            .filter(r -> r.getWorkspaceId() == ws)
                            .map(Result::getId)
                            .collect(Collectors.toSet());
                });
    }

    @Autowired
    ItemIndexService index;

    @Autowired
    ItemRepository items;

    @Autowired
    PracticeVersionService versions;

    Scenario scenario;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        scenario = data.scenario();
    }

    String root() {
        return "/workspaces/" + scenario.workspace().getWorkspaceSlug();
    }

    WebTestClient.ResponseSpec get(String path) {
        return http.get()
                .uri(root() + path)
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .exchange();
    }

    @Test
    void shouldListOnlyOwnDirectReviewChildrenWithTheirParentPullRequest() {
        var at = Instant.now();
        var parentResult = data.result(scenario, ResultOutcome.MET, at);
        var first = historyItem(scenario.account(), "scm.pull_request_review", scenario.item());
        var firstResult = data.result(first, ResultOutcome.NOT_MET, at.plusSeconds(1));
        var second = historyItem(scenario.account(), "scm.pull_request_review", scenario.item());
        var secondResult = data.result(second, ResultOutcome.MET, at.plusSeconds(2));
        var anotherDeveloper =
                historyItem(data.member(scenario.workspace().getId()), "scm.pull_request_review", scenario.item());
        data.result(anotherDeveloper, ResultOutcome.NOT_MET, at.plusSeconds(3));
        data.result(historyItem(scenario.account(), "scm.file", scenario.item()), ResultOutcome.MET, at.plusSeconds(4));
        var otherParent = historyItem(scenario.account(), "scm.pull_request", scenario.item());
        data.result(
                historyItem(scenario.account(), "scm.pull_request_review", otherParent.item()),
                ResultOutcome.NOT_MET,
                at.plusSeconds(5));
        data.result(data.scenario(), ResultOutcome.NOT_MET, at.plusSeconds(6));
        scenario.item().setRemovedAt(at.plusSeconds(7));
        items.saveAndFlush(scenario.item());

        get("/practices/items/" + scenario.item().getId() + "/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(3)
                .jsonPath("$.content[*].review.id")
                .value(ids -> assertThat(ids)
                        .asInstanceOf(InstanceOfAssertFactories.list(String.class))
                        .containsExactlyInAnyOrder(
                                parentResult.getReviewId().toString(),
                                firstResult.getReviewId().toString(),
                                secondResult.getReviewId().toString()));
        get("/practices/items/" + first.item().getId() + "/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(1)
                .jsonPath("$.content[0].review.id")
                .isEqualTo(firstResult.getReviewId().toString());
    }

    @Test
    void shouldKeepReviewChildEvidenceHiddenInParentHistoryWhenNotAuthorized() {
        var child = historyItem(scenario.account(), "scm.pull_request_review", scenario.item());
        var result = data.result(child, ResultOutcome.NOT_MET, Instant.now());
        Mockito.when(evidenceAuthorization.permitsAll(
                        ArgumentMatchers.anyLong(), ArgumentMatchers.anyCollection(), ArgumentMatchers.any()))
                .thenReturn(Set.of());
        get("/practices/items/" + scenario.item().getId() + "/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(1)
                .jsonPath("$.content[0].review.id")
                .isEqualTo(result.getReviewId().toString())
                .jsonPath("$.content[0].results[0].summary")
                .isEmpty()
                .jsonPath("$.content[0].results[0].problems.length()")
                .isEqualTo(0);
    }

    private Scenario historyItem(Account account, String kind, Item parent) {
        var item = new Item();
        item.setWorkspaceId(scenario.workspace().getId());
        item.setKind(new ItemKind(kind));
        item.setParentId(parent.getId());
        item.setSourceId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        item.setCreatedAt(Instant.now().minusSeconds(3600));
        items.saveAndFlush(item);
        var definition = DefinitionTestFixtures.definition(
                item.getKind(), "Historical readback", "Check the captured work.", null);
        if (kind.equals("scm.pull_request_review")) {
            definition = DefinitionTestFixtures.withAbout(
                    DefinitionTestFixtures.withReviewOn(
                            definition,
                            new ReviewOn(List.of(new ItemEventName("scm.pull_request_review.submitted")), Map.of())),
                    AboutRole.REVIEWER);
        } else if (kind.equals("scm.file")) {
            definition = DefinitionTestFixtures.withReviewOn(definition, new ReviewOn(List.of(), Map.of()));
        }
        var practice = versions.create(new PracticeVersionService.NewPractice(
                scenario.workspace(),
                "history-" + UUID.randomUUID(),
                item.getKind(),
                definition,
                null,
                0,
                account.getId()));
        return new Scenario(scenario.workspace(), account, practice, item);
    }

    @Test
    void profileListsDurableReviewIds() {
        var r = data.result(scenario, ResultOutcome.MET, Instant.now());
        get("/practice-profile/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(1)
                .jsonPath("$.content[0].review.id")
                .isEqualTo(r.getReviewId().toString());
    }

    @Test
    void foreignReviewIsNotFound() {
        var r = data.result(data.scenario(), ResultOutcome.MET, Instant.now());
        get("/practice-profile/reviews/" + r.getReviewId())
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
    }

    @Test
    void kindFilterIsAppliedBeforePagination() {
        data.result(scenario, ResultOutcome.MET, Instant.now());
        get("/practice-profile/reviews?itemKind=chat.thread&size=1")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(0)
                .jsonPath("$.content.length()")
                .isEqualTo(0);
    }

    @Test
    void shouldKeepOwnReviewMetadataWhenItsTextIsHidden() {
        data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        Mockito.when(evidenceAuthorization.permitsAll(
                        ArgumentMatchers.anyLong(), ArgumentMatchers.anyCollection(), ArgumentMatchers.any()))
                .thenReturn(Set.of());
        get("/practice-profile/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(1)
                .jsonPath("$.content[0].results[0].summary")
                .isEmpty()
                .jsonPath("$.content[0].results[0].problems.length()")
                .isEqualTo(0);
    }

    @Test
    void endedResultsStillHaveAReviewHistory() {
        var r = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        data.end(r, Instant.now().plusSeconds(1));
        get("/practice-profile/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(1);
    }

    @Test
    void shouldResolveRetainedFeedbackWorkForAMemberWithoutProviderCapture() {
        long workspace = scenario.workspace().getId();
        long source = 9_007_199_254_740_993L;
        var item = index.upsert(new ItemRegistration(workspace, new ItemKind("scm.pull_request"), source, null, null));
        index.remove(workspace, item.itemId(), Instant.now());
        String root = "/workspaces/" + scenario.workspace().getWorkspaceSlug() + "/practices/items/legacy-feedback/";
        http.get()
                .uri(root + "scm.pull_request/" + source)
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.itemId")
                .isEqualTo(Long.toString(item.itemId()));
        http.get()
                .uri(root + "scm.issue/" + source)
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
        http.get()
                .uri(root + "scm.repository/" + source)
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
        http.get()
                .uri(root + "scm.pull_request/" + source)
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody(Void.class);
    }
}
