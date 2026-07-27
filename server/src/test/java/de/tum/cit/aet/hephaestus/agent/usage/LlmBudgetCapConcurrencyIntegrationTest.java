package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The two LLM caps — the host's cap on shared-model spend and the workspace's cap on its own-provider
 * spend — are two columns of ONE {@code workspace} row, written by two different people through two
 * different endpoints. Hibernate's UPDATE covers every column of that row, so two writers that both
 * read before either commits each write the other's column back to what they saw: whoever commits
 * second silently reverts the first, and both audit trails assert a transition that no longer holds.
 * Nothing in the write path fails, so the first admin only finds out when the cap they set stops
 * having any effect.
 *
 * <p>The fix is a pessimistic read that serialises the snapshot with the write, and this is the only
 * place it can be shown working: the interleaving needs two real connections against real Postgres
 * row locks.
 */
@Tag("integration")
class LlmBudgetCapConcurrencyIntegrationTest extends AbstractWorkspaceIntegrationTest {

    /** Comfortably above the 5s lock timeout the locking reads declare, so a hang fails as a hang. */
    private static final int JOIN_TIMEOUT_SECONDS = 30;

    /** Long enough for the follower to reach its statement and block on the row, short enough to stay quick. */
    private static final long FOLLOWER_BLOCK_MILLIS = 500;

    @Autowired
    private LlmUsageAdminService adminService;

    @Autowired
    private LlmUsageService llmUsageService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final BigDecimal INSTANCE_CAP = new BigDecimal("250.00");
    private static final BigDecimal OWN_PROVIDER_CAP = new BigDecimal("40.00");

    static Stream<Arguments> writeOrder() {
        return Stream.of(
            Arguments.of("the instance admin gets there first"),
            Arguments.of("the workspace admin gets there first")
        );
    }

    /**
     * Kills {@code findByIdForUpdate} → {@code findById} in
     * {@link LlmUsageService#updateOwnProviderBudget} and {@code findByWorkspaceSlugForUpdate} →
     * {@code findByWorkspaceSlug} in {@link LlmUsageAdminService#updateBudget}: with either read
     * unlocked, the follower snapshots the row before the holder commits and writes the holder's cap
     * back to null.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("writeOrder")
    @DisplayName("neither cap write reverts the other when both land on the same row at once")
    void neitherCapWriteRevertsTheOtherWhenBothLandOnTheSameRowAtOnce(String scenario) throws Exception {
        boolean instanceAdminHoldsTheRow = scenario.startsWith("the instance admin");
        User owner = persistUser("cap-race-owner-" + (instanceAdminHoldsTheRow ? "instance" : "workspace"));
        Workspace workspace = createWorkspace(
            "cap-race-" + (instanceAdminHoldsTheRow ? "instance" : "workspace"),
            "Cap Race",
            "cap-race-org-" + (instanceAdminHoldsTheRow ? "instance" : "workspace"),
            AccountType.ORG,
            owner
        );
        Consumer<Workspace> setInstanceCap = ws -> adminService.updateBudget(ws.getWorkspaceSlug(), INSTANCE_CAP);
        Consumer<Workspace> setOwnProviderCap = ws ->
            llmUsageService.updateOwnProviderBudget(ws.getId(), OWN_PROVIDER_CAP);

        race(
            workspace,
            instanceAdminHoldsTheRow ? setInstanceCap : setOwnProviderCap,
            instanceAdminHoldsTheRow ? setOwnProviderCap : setInstanceCap
        );

        Workspace reloaded = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(reloaded.getMonthlyLlmBudgetUsd()).isEqualByComparingTo(INSTANCE_CAP);
        assertThat(reloaded.getMonthlyByoLlmBudgetUsd()).isEqualByComparingTo(OWN_PROVIDER_CAP);
    }

    /**
     * Runs {@code holder} in a transaction that stays open until {@code follower} has been dispatched
     * against the same row, so the follower is genuinely contending for it rather than arriving after
     * the fact.
     */
    private void race(Workspace workspace, Consumer<Workspace> holder, Consumer<Workspace> follower) throws Exception {
        CountDownLatch holderHasTheRow = new CountDownLatch(1);
        CountDownLatch followerDispatched = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holding = pool.submit(() ->
                transactionTemplate.executeWithoutResult(tx -> {
                    holder.accept(workspace);
                    holderHasTheRow.countDown();
                    await(followerDispatched);
                    sleep(FOLLOWER_BLOCK_MILLIS);
                })
            );
            Future<?> following = pool.submit(() -> {
                await(holderHasTheRow);
                followerDispatched.countDown();
                follower.accept(workspace);
            });
            holding.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            following.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("handoff timed out").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
