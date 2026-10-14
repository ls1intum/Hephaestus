package de.tum.cit.aet.hephaestus.integration.scm.github.check;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.CheckState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class GitHubHeadCheckConcurrencyIntegrationTest extends BaseIntegrationTest {

    private static final String HEAD = "a".repeat(40);
    private static final String OTHER_HEAD = "b".repeat(40);

    @Autowired
    private GitHubHeadCheckProcessor processor;

    @Autowired
    private PullRequestRepository pullRequests;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    private Repository repository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        repository = repository(provider("https://github.com"), 1L, "fixture/checks");
    }

    @ParameterizedTest
    @CsvSource({"FAILURE, CANCELLED", "CANCELLED, FAILURE"})
    void shouldKeepTheWorstStateWhenCheckEventsOverlap(CheckState first, CheckState second) throws Exception {
        PullRequest pr = pullRequest(repository, 11, HEAD);
        ProcessingContext context = ProcessingContext.forWebhook(1L, repository, "completed");

        try (var threads = Executors.newSingleThreadExecutor()) {
            var waiting = Objects.requireNonNull(transactions.execute(status -> {
                // Hold the real row lock before either processor reads it. Both paths then read in their own
                // transaction; the second event must compare against what the first commits, not an old entity.
                assertThat(pullRequests.lockById(pr.getId())).contains(pr.getId());
                processor.observe(HEAD, first, context);
                pullRequests.flush();
                Integer holder = Objects.requireNonNull(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                var observer = threads.submit(() -> processor.observe(HEAD, second, context));
                Awaitility.await()
                        .pollInSameThread()
                        .atMost(Duration.ofSeconds(20))
                        .until(() -> {
                            jdbc.execute("SELECT pg_stat_clear_snapshot()");
                            return Boolean.TRUE.equals(jdbc.queryForObject(
                                    "SELECT EXISTS (SELECT FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))",
                                    Boolean.class,
                                    holder));
                        });
                assertThat(observer).isNotDone();
                return observer;
            }));
            waiting.get(20, TimeUnit.SECONDS);
        }

        PullRequest stored = pullRequests.findById(pr.getId()).orElseThrow();
        assertThat(stored.getHeadCheckState()).isEqualTo(CheckState.FAILURE);
        assertThat(stored.getHeadCheckSha()).isEqualTo(HEAD);
    }

    @Test
    void shouldUpdateEveryMatchingHeadWithoutChangingOtherRepositoriesOrHeads() {
        PullRequest first = pullRequest(repository, 11, HEAD);
        PullRequest sameHead = pullRequest(repository, 12, HEAD);
        PullRequest otherHead = pullRequest(repository, 13, OTHER_HEAD);
        Repository sibling = repository(repository.getProvider(), 2L, "fixture/sibling");
        PullRequest otherRepository = pullRequest(sibling, 21, HEAD);
        Repository otherInstance = repository(provider("https://github.example"), 1L, "fixture/checks");
        PullRequest otherProvider = pullRequest(otherInstance, 11, HEAD);
        ProcessingContext context = ProcessingContext.forWebhook(1L, repository, "completed");

        processor.observe(HEAD, CheckState.FAILURE, context);
        processor.observe(HEAD, CheckState.CANCELLED, context);
        processor.observe(HEAD, CheckState.FAILURE, context);

        for (PullRequest changed : List.of(first, sameHead)) {
            PullRequest stored = pullRequests.findById(changed.getId()).orElseThrow();
            assertThat(stored.getHeadCheckState()).isEqualTo(CheckState.FAILURE);
            assertThat(stored.getHeadCheckSha()).isEqualTo(HEAD);
        }
        for (PullRequest unchanged : List.of(otherHead, otherRepository, otherProvider)) {
            PullRequest stored = pullRequests.findById(unchanged.getId()).orElseThrow();
            assertThat(stored.getHeadCheckState()).isEqualTo(CheckState.SUCCESS);
            assertThat(stored.getHeadCheckSha()).isEqualTo(unchanged.getHeadRefOid());
        }
    }

    private IdentityProvider provider(String origin) {
        return providers
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, origin)
                .orElseGet(() -> providers.save(new IdentityProvider(IdentityProviderType.GITHUB, origin)));
    }

    private Repository repository(IdentityProvider provider, long nativeId, String name) {
        Repository result = new Repository();
        result.setProvider(provider);
        result.setNativeId(nativeId);
        result.setName(name.substring(name.indexOf('/') + 1));
        result.setNameWithOwner(name);
        result.setHtmlUrl(provider.getServerUrl() + "/" + name);
        result.setVisibility(Repository.Visibility.PUBLIC);
        result.setPushedAt(Instant.parse("2026-10-01T00:00:00Z"));
        return repositories.saveAndFlush(result);
    }

    private PullRequest pullRequest(Repository owner, int number, String head) {
        PullRequest result = new PullRequest();
        result.setProvider(owner.getProvider());
        result.setNativeId((long) number);
        result.setNumber(number);
        result.setRepository(owner);
        result.setTitle("Check event fixture");
        result.setState(PullRequest.State.OPEN);
        result.setHeadRefOid(head);
        result.observeHeadChecks(head, CheckState.SUCCESS, true);
        return pullRequests.saveAndFlush(result);
    }
}
