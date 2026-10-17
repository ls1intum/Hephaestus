package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountSummaryQuery;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.scm.context.MentorContextQueryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.context.MergeReadinessContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.context.RecentAuthoredWorkContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.context.ScmMentorWorkQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RecentAuthoredWorkContentSourceTest extends BaseUnitTest {

    @Mock
    AccountSummaryQuery accounts;

    @Mock
    MentorContextQueryRepository queryRepository;

    @Mock
    ScmMentorWorkQuery work;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    RecentAuthoredWorkContentSource source;

    @Test
    void shouldPublishMergedPullRequestResourcesUsingItsSourceId() {
        when(accounts.findAllByIds(List.of(2L)))
                .thenReturn(Map.of(2L, new AccountSummaryQuery.AccountSummary(2, "Student", null)));
        when(work.actorIds(1L, 2L)).thenReturn(List.of(200L));
        PullRequest merged = new PullRequest();
        merged.setId(4009553323L);
        merged.setNumber(9);
        merged.setTitle("Add the simulator note");
        merged.setState(Issue.State.MERGED);
        when(queryRepository.findRecentAuthoredPullRequests(eq(1L), eq(List.of(200L)), any(Pageable.class)))
                .thenReturn(List.of(merged));
        Issue issue = new Issue();
        issue.setId(77L);
        issue.setNumber(14);
        issue.setTitle("Follow up on the runtime check");
        issue.setState(Issue.State.OPEN);
        when(queryRepository.findRecentAuthoredIssues(eq(1L), eq(List.of(200L)), any(Pageable.class)))
                .thenReturn(List.of(issue));

        JsonNode payload = source.buildPayload(1L, 2L);

        String resource = payload.get("pullRequests").get(0).get("resource").asString();
        assertThat(resource).isEqualTo("inputs/context/merge_readiness/4009553323.json");
        assertThat(MergeReadinessContentSource.pullRequestIdOf(resource)).contains(4009553323L);
        assertThat(payload.get("issues").get(0).has("resource")).isFalse();

        String pullRequestReviews =
                payload.get("pullRequests").get(0).get("reviewsResource").asString();
        String issueReviews =
                payload.get("issues").get(0).get("reviewsResource").asString();
        assertThat(pullRequestReviews).isEqualTo("inputs/context/review_attempts/pull_request/4009553323.json");
        assertThat(issueReviews).isEqualTo("inputs/context/review_attempts/issue/77.json");
        assertThat(ReviewAttemptsContentSource.selectionOf(pullRequestReviews))
                .contains(new ReviewAttemptsContentSource.ItemSelection(new ItemKind("scm.pull_request"), 4009553323L));
        assertThat(ReviewAttemptsContentSource.selectionOf(issueReviews))
                .contains(new ReviewAttemptsContentSource.ItemSelection(new ItemKind("scm.issue"), 77L));
    }
}
