package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class LlmBudgetServiceTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;

    @Mock
    private LlmUsageEventRepository usageRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private SimpleMeterRegistry meterRegistry;
    private LlmBudgetService budgetService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        budgetService = new LlmBudgetService(usageRepository, workspaceRepository, meterRegistry);
    }

    private Workspace workspaceWithBudget(@Nullable BigDecimal budget) {
        return workspaceWithBudgets(budget, null);
    }

    /** A workspace carrying both caps: the instance admin's purse and the workspace admin's own. */
    private Workspace workspaceWithBudgets(@Nullable BigDecimal instanceBudget, @Nullable BigDecimal byoBudget) {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setMonthlyLlmBudgetUsd(instanceBudget);
        workspace.setMonthlyByoLlmBudgetUsd(byoBudget);
        return workspace;
    }

    private void stubMonthSpend(String spend) {
        when(usageRepository.sumCost(eq(WORKSPACE_ID), any(Instant.class), any(Instant.class))).thenReturn(
            new BigDecimal(spend)
        );
    }

    private void stubHasUnpriced(boolean hasUnpriced) {
        when(
            usageRepository.existsUnpricedInstanceFunded(eq(WORKSPACE_ID), any(Instant.class), any(Instant.class))
        ).thenReturn(hasUnpriced);
    }

    private void stubByoMonthSpend(String spend) {
        when(usageRepository.sumByoCost(eq(WORKSPACE_ID), any(Instant.class), any(Instant.class))).thenReturn(
            new BigDecimal(spend)
        );
    }

    private void stubHasUnpricedByo(boolean hasUnpriced) {
        when(
            usageRepository.existsUnpricedWorkspaceFunded(eq(WORKSPACE_ID), any(Instant.class), any(Instant.class))
        ).thenReturn(hasUnpriced);
    }

    /** Every ledger read stubbed leniently, so a single test can vary one purse at a time. */
    private void stubLedger(String instanceSpend, boolean instanceUnpriced, String byoSpend, boolean byoUnpriced) {
        lenient()
            .when(usageRepository.sumCost(eq(WORKSPACE_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(new BigDecimal(instanceSpend));
        lenient()
            .when(
                usageRepository.existsUnpricedInstanceFunded(eq(WORKSPACE_ID), any(Instant.class), any(Instant.class))
            )
            .thenReturn(instanceUnpriced);
        lenient()
            .when(usageRepository.sumByoCost(eq(WORKSPACE_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(new BigDecimal(byoSpend));
        lenient()
            .when(
                usageRepository.existsUnpricedWorkspaceFunded(eq(WORKSPACE_ID), any(Instant.class), any(Instant.class))
            )
            .thenReturn(byoUnpriced);
    }

    @Nested
    class BudgetEvaluation {

        @Test
        void uncappedWorkspaceIsNeverExhausted() {
            assertThat(budgetService.isBudgetExhausted(workspaceWithBudget(null))).isFalse();
        }

        @Test
        void spendBelowBudgetIsNotExhausted() {
            stubMonthSpend("9.99");
            assertThat(budgetService.isBudgetExhausted(workspaceWithBudget(new BigDecimal("10.00")))).isFalse();
        }

        @Test
        void spendAtBudgetIsExhausted() {
            stubMonthSpend("10.00");
            assertThat(budgetService.isBudgetExhausted(workspaceWithBudget(new BigDecimal("10.00")))).isTrue();
        }

        @Test
        void zeroBudgetActsAsImmediatePauseSwitch() {
            stubMonthSpend("0");
            assertThat(budgetService.isBudgetExhausted(workspaceWithBudget(BigDecimal.ZERO))).isTrue();
        }

        @Test
        void unknownWorkspaceIdIsNotExhausted() {
            when(workspaceRepository.findById(99L)).thenReturn(java.util.Optional.empty());
            assertThat(budgetService.isBudgetExhausted(99L)).isFalse();
        }
    }

    /**
     * #1368: the instance admin's purse — a cap over the shared models the host pays for. It is
     * blocked by EXHAUSTED and by an unverifiable month (a cap whose true spend can't be confirmed is
     * not a cap), and never blocked when uncapped.
     */
    @Nested
    @DisplayName("decide() — the instance-funded purse")
    class InstanceFundedPurse {

        @Test
        @DisplayName("an uncapped instance purse never blocks and never queries the ledger")
        void uncappedWorkspaceIsNeverBlocked() {
            assertThat(budgetService.decide(workspaceWithBudget(null)).instanceFunded()).isEqualTo(
                LlmBudgetBlockReason.NONE
            );
            // Never even asked the ledger — uncapped short-circuits first.
            verify(usageRepository, never()).sumCost(any(), any(), any());
        }

        @Test
        @DisplayName("priced instance spend at the cap is EXHAUSTED without probing for unpriced usage")
        void exhaustedBudgetBlocks() {
            stubMonthSpend("10.00");

            assertThat(budgetService.decide(workspaceWithBudget(new BigDecimal("10.00"))).instanceFunded()).isEqualTo(
                LlmBudgetBlockReason.EXHAUSTED
            );
            // EXHAUSTED is provable from the priced sum alone — never needs the unpriced-event query.
            verify(usageRepository, never()).existsUnpricedInstanceFunded(any(), any(), any());
        }

        @Test
        @DisplayName("within the cap with a fully priced month does not block")
        void withinBudgetAndNoUnpricedUsageIsNeverBlocked() {
            stubMonthSpend("1.00");
            stubHasUnpriced(false);

            assertThat(budgetService.decide(workspaceWithBudget(new BigDecimal("10.00"))).instanceFunded()).isEqualTo(
                LlmBudgetBlockReason.NONE
            );
        }

        @Test
        @DisplayName("an unpriced instance-funded event blocks a capped instance purse")
        void unverifiableMonthBlocksACappedWorkspace() {
            stubMonthSpend("1.00");
            stubHasUnpriced(true);

            assertThat(budgetService.decide(workspaceWithBudget(new BigDecimal("10.00"))).instanceFunded()).isEqualTo(
                LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED
            );
        }

        @Test
        @DisplayName("a workspace with neither cap set is allowed however the ledger looks")
        void anUncappedWorkspaceIsAllowedWhateverTheLedgerSays() {
            // Spend far past any plausible cap, unpriced usage on both purses: without a cap there is
            // nothing to enforce, so the payer who opted out of enforcement stays unblocked.
            stubLedger("999999.00", true, "999999.00", true);

            assertThat(budgetService.decide(workspaceWithBudgets(null, null))).isEqualTo(LlmBudgetDecision.ALLOWED);
            verify(usageRepository, never()).sumCost(any(), any(), any());
            verify(usageRepository, never()).existsUnpricedInstanceFunded(any(), any(), any());
        }

        @Test
        @DisplayName("an unknown workspace id is allowed on both purses")
        void unknownWorkspaceIdIsNeverBlocked() {
            when(workspaceRepository.findById(99L)).thenReturn(java.util.Optional.empty());

            assertThat(budgetService.decide(99L)).isEqualTo(LlmBudgetDecision.ALLOWED);
        }
    }

    /**
     * #1368: the workspace admin's own purse — a cap over spend on the workspace's own connected
     * provider. Same rules as the instance purse, but measured against {@code sumByoCost} and the BYO
     * unpriced probe, because that is the money the workspace itself pays.
     */
    @Nested
    @DisplayName("decide() — the workspace-funded (own-provider) purse")
    class WorkspaceFundedPurse {

        @Test
        @DisplayName("an uncapped BYO purse never blocks and never queries the BYO ledger")
        void uncappedByoPurseIsNeverBlocked() {
            assertThat(budgetService.decide(workspaceWithBudgets(null, null)).workspaceFunded()).isEqualTo(
                LlmBudgetBlockReason.NONE
            );
            verify(usageRepository, never()).sumByoCost(any(), any(), any());
            verify(usageRepository, never()).existsUnpricedWorkspaceFunded(any(), any(), any());
        }

        @Test
        @DisplayName("priced BYO spend at the cap is EXHAUSTED without probing for unpriced BYO usage")
        void byoSpendAtTheCapIsExhausted() {
            stubByoMonthSpend("25.00");

            assertThat(
                budgetService.decide(workspaceWithBudgets(null, new BigDecimal("25.00"))).workspaceFunded()
            ).isEqualTo(LlmBudgetBlockReason.EXHAUSTED);
            verify(usageRepository, never()).existsUnpricedWorkspaceFunded(any(), any(), any());
        }

        @Test
        @DisplayName("within the BYO cap with a fully priced month does not block")
        void byoSpendBelowTheCapDoesNotBlock() {
            stubByoMonthSpend("24.99");
            stubHasUnpricedByo(false);

            assertThat(
                budgetService.decide(workspaceWithBudgets(null, new BigDecimal("25.00"))).workspaceFunded()
            ).isEqualTo(LlmBudgetBlockReason.NONE);
        }

        @Test
        @DisplayName("an unpriced own-provider event blocks a capped BYO purse")
        void unpricedByoUsageBlocksACappedByoPurse() {
            stubByoMonthSpend("1.00");
            stubHasUnpricedByo(true);

            assertThat(
                budgetService.decide(workspaceWithBudgets(null, new BigDecimal("25.00"))).workspaceFunded()
            ).isEqualTo(LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED);
        }

        @Test
        @DisplayName("a zero BYO cap pauses own-provider work immediately")
        void zeroByoBudgetPausesImmediately() {
            stubByoMonthSpend("0");

            assertThat(budgetService.decide(workspaceWithBudgets(null, BigDecimal.ZERO)).workspaceFunded()).isEqualTo(
                LlmBudgetBlockReason.EXHAUSTED
            );
        }
    }

    /**
     * The rule the whole two-purse design exists for: instance-funded and own-provider spend are
     * different people's money, so neither purse may ever be blocked by the other's state. In
     * particular each cap is only blocked by a blind spot its OWN owner can clear — an unpriced
     * shared model is the host's to price, an unpriced BYO model the workspace's.
     */
    @Nested
    @DisplayName("decide() — the two purses never contaminate each other")
    class PurseIsolation {

        @Test
        @DisplayName("an unpriced INSTANCE event never blocks the BYO purse")
        void unpricedInstanceUsageDoesNotBlockTheByoPurse() {
            stubLedger("0.00", true, "0.00", false);

            LlmBudgetDecision decision = budgetService.decide(
                workspaceWithBudgets(new BigDecimal("10.00"), new BigDecimal("10.00"))
            );

            assertThat(decision.instanceFunded()).isEqualTo(LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED);
            assertThat(decision.workspaceFunded()).isEqualTo(LlmBudgetBlockReason.NONE);
        }

        @Test
        @DisplayName("an unpriced WORKSPACE event never blocks the instance purse")
        void unpricedByoUsageDoesNotBlockTheInstancePurse() {
            stubLedger("0.00", false, "0.00", true);

            LlmBudgetDecision decision = budgetService.decide(
                workspaceWithBudgets(new BigDecimal("10.00"), new BigDecimal("10.00"))
            );

            assertThat(decision.instanceFunded()).isEqualTo(LlmBudgetBlockReason.NONE);
            assertThat(decision.workspaceFunded()).isEqualTo(LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED);
        }

        @Test
        @DisplayName("an exhausted instance purse leaves the BYO purse spendable")
        void exhaustedInstancePurseLeavesTheByoPurseOpen() {
            stubLedger("10.00", false, "0.00", false);

            LlmBudgetDecision decision = budgetService.decide(
                workspaceWithBudgets(new BigDecimal("10.00"), new BigDecimal("10.00"))
            );

            assertThat(decision.instanceFunded()).isEqualTo(LlmBudgetBlockReason.EXHAUSTED);
            assertThat(decision.workspaceFunded()).isEqualTo(LlmBudgetBlockReason.NONE);
        }

        @Test
        @DisplayName("an exhausted BYO purse leaves the instance purse spendable")
        void exhaustedByoPurseLeavesTheInstancePurseOpen() {
            stubLedger("0.00", false, "10.00", false);

            LlmBudgetDecision decision = budgetService.decide(
                workspaceWithBudgets(new BigDecimal("10.00"), new BigDecimal("10.00"))
            );

            assertThat(decision.instanceFunded()).isEqualTo(LlmBudgetBlockReason.NONE);
            assertThat(decision.workspaceFunded()).isEqualTo(LlmBudgetBlockReason.EXHAUSTED);
        }

        /**
         * The ownership invariant, exhaustively: the instance-funded verdict is a pure function of
         * (instance cap, instance-funded ledger rows). Every combination of the BYO cap and the BYO
         * ledger is swept while the instance inputs are held fixed — none of them may move the
         * instance verdict. This is what "a workspace admin can only ever make things stricter"
         * means operationally: nothing they can write is an input to the host's protection.
         */
        @Test
        @DisplayName("the instance verdict is unmoved by every combination of BYO cap and BYO ledger")
        void instanceVerdictIsIndependentOfEveryByoInput() {
            BigDecimal[] byoCaps = { null, BigDecimal.ZERO, new BigDecimal("0.01"), new BigDecimal("1000000.00") };
            String[] byoSpends = { "0.00", "999999.00" };
            boolean[] byoUnpriced = { false, true };
            BigDecimal instanceCap = new BigDecimal("10.00");

            // Held fixed across the sweep: instance spend under the cap, one unpriced instance event.
            for (BigDecimal byoCap : byoCaps) {
                for (String byoSpend : byoSpends) {
                    for (boolean unpriced : byoUnpriced) {
                        stubLedger("1.00", true, byoSpend, unpriced);

                        LlmBudgetDecision decision = budgetService.decide(workspaceWithBudgets(instanceCap, byoCap));

                        assertThat(decision.instanceFunded())
                            .as("byoCap=%s byoSpend=%s byoUnpriced=%s", byoCap, byoSpend, unpriced)
                            .isEqualTo(LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED);
                    }
                }
            }
        }

        /** The mirror sweep: the BYO verdict is likewise blind to everything on the instance side. */
        @Test
        @DisplayName("the BYO verdict is unmoved by every combination of instance cap and instance ledger")
        void byoVerdictIsIndependentOfEveryInstanceInput() {
            BigDecimal[] instanceCaps = { null, BigDecimal.ZERO, new BigDecimal("0.01"), new BigDecimal("1000000.00") };
            String[] instanceSpends = { "0.00", "999999.00" };
            boolean[] instanceUnpriced = { false, true };
            BigDecimal byoCap = new BigDecimal("10.00");

            for (BigDecimal instanceCap : instanceCaps) {
                for (String instanceSpend : instanceSpends) {
                    for (boolean unpriced : instanceUnpriced) {
                        stubLedger(instanceSpend, unpriced, "10.00", false);

                        LlmBudgetDecision decision = budgetService.decide(workspaceWithBudgets(instanceCap, byoCap));

                        assertThat(decision.workspaceFunded())
                            .as(
                                "instanceCap=%s instanceSpend=%s instanceUnpriced=%s",
                                instanceCap,
                                instanceSpend,
                                unpriced
                            )
                            .isEqualTo(LlmBudgetBlockReason.EXHAUSTED);
                    }
                }
            }
        }
    }

    /**
     * {@link LlmBudgetDecision#forFunding} routes a call to its payer's verdict. An unattributable
     * call (null funding source) is judged against BOTH — fail-safe, never a way around a cap.
     */
    @Nested
    @DisplayName("LlmBudgetDecision.forFunding")
    class DecisionRouting {

        private final LlmBudgetDecision instanceBlocked = new LlmBudgetDecision(
            LlmBudgetBlockReason.EXHAUSTED,
            LlmBudgetBlockReason.NONE
        );
        private final LlmBudgetDecision byoBlocked = new LlmBudgetDecision(
            LlmBudgetBlockReason.NONE,
            LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED
        );

        @Test
        @DisplayName("each funding source reads only its own purse")
        void eachFundingSourceReadsItsOwnPurse() {
            assertThat(instanceBlocked.forFunding(FundingSource.INSTANCE)).isEqualTo(LlmBudgetBlockReason.EXHAUSTED);
            assertThat(instanceBlocked.forFunding(FundingSource.WORKSPACE)).isEqualTo(LlmBudgetBlockReason.NONE);
            assertThat(byoBlocked.forFunding(FundingSource.WORKSPACE)).isEqualTo(
                LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED
            );
            assertThat(byoBlocked.forFunding(FundingSource.INSTANCE)).isEqualTo(LlmBudgetBlockReason.NONE);
        }

        @Test
        @DisplayName("an unattributable call is blocked when EITHER purse is blocked")
        void unknownFundingSourceIsFailSafe() {
            assertThat(instanceBlocked.forFunding(null)).isEqualTo(LlmBudgetBlockReason.EXHAUSTED);
            assertThat(byoBlocked.forFunding(null)).isEqualTo(LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED);
            assertThat(instanceBlocked.blocks(null)).isTrue();
            assertThat(byoBlocked.blocks(null)).isTrue();
        }

        @Test
        @DisplayName("an unattributable call proceeds only when NEITHER purse is blocked")
        void unknownFundingSourceProceedsWhenBothPursesAreOpen() {
            assertThat(LlmBudgetDecision.ALLOWED.forFunding(null)).isEqualTo(LlmBudgetBlockReason.NONE);
            assertThat(LlmBudgetDecision.ALLOWED.blocks(null)).isFalse();
            assertThat(LlmBudgetDecision.ALLOWED.blocksAnything()).isFalse();
        }

        @Test
        @DisplayName("blocksAnything reports a pause on either purse")
        void blocksAnythingCoversEitherPurse() {
            assertThat(instanceBlocked.blocksAnything()).isTrue();
            assertThat(byoBlocked.blocksAnything()).isTrue();
        }
    }

    /**
     * The submission-side gate is funding-scoped: it asks only the purse that pays for the work, and
     * tags its metric with which cap refused it.
     */
    @Nested
    @DisplayName("blockSubmission is scoped to whoever pays")
    class BlockSubmission {

        @Test
        @DisplayName("an exhausted instance cap does not block workspace-funded work")
        void exhaustedInstanceCapDoesNotBlockWorkspaceFundedWork() {
            stubLedger("10.00", false, "0.00", false);
            Workspace workspace = workspaceWithBudgets(new BigDecimal("10.00"), new BigDecimal("10.00"));

            assertThat(
                budgetService.blockSubmission(workspace, "PULL_REQUEST_REVIEW", FundingSource.WORKSPACE)
            ).isFalse();
            assertThat(
                budgetService.blockSubmission(workspace, "PULL_REQUEST_REVIEW", FundingSource.INSTANCE)
            ).isTrue();
            assertThat(
                meterRegistry.counter("llm.budget.blocked", "surface", "agent_job", "cap", "instance").count()
            ).isEqualTo(1d);
        }

        @Test
        @DisplayName("an exhausted BYO cap does not block instance-funded work")
        void exhaustedByoCapDoesNotBlockInstanceFundedWork() {
            stubLedger("0.00", false, "10.00", false);
            Workspace workspace = workspaceWithBudgets(new BigDecimal("10.00"), new BigDecimal("10.00"));

            assertThat(
                budgetService.blockSubmission(workspace, "PULL_REQUEST_REVIEW", FundingSource.INSTANCE)
            ).isFalse();
            assertThat(
                budgetService.blockSubmission(workspace, "PULL_REQUEST_REVIEW", FundingSource.WORKSPACE)
            ).isTrue();
            assertThat(
                meterRegistry.counter("llm.budget.blocked", "surface", "agent_job", "cap", "byo").count()
            ).isEqualTo(1d);
        }

        @Test
        @DisplayName("an unattributable submission is blocked when either cap is reached")
        void unattributableSubmissionIsBlockedByEitherCap() {
            stubLedger("0.00", false, "10.00", false);
            Workspace workspace = workspaceWithBudgets(new BigDecimal("10.00"), new BigDecimal("10.00"));

            assertThat(budgetService.blockSubmission(workspace, "PULL_REQUEST_REVIEW", null)).isTrue();
        }

        @Test
        @DisplayName("an uncapped workspace never blocks a submission on either purse")
        void uncappedWorkspaceNeverBlocksASubmission() {
            Workspace workspace = workspaceWithBudgets(null, null);

            assertThat(
                budgetService.blockSubmission(workspace, "PULL_REQUEST_REVIEW", FundingSource.INSTANCE)
            ).isFalse();
            assertThat(
                budgetService.blockSubmission(workspace, "PULL_REQUEST_REVIEW", FundingSource.WORKSPACE)
            ).isFalse();
        }
    }

    @Nested
    class Verdict {

        @Test
        void withinBudgetWithNoUnpricedEventsIsWithin() {
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("5.00"), false, new BigDecimal("10.00"))).isEqualTo(
                LlmBudgetVerdict.WITHIN
            );
        }

        @Test
        void pricedSumAtOrAboveTheCapIsExhausted() {
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("10.00"), false, new BigDecimal("10.00"))).isEqualTo(
                LlmBudgetVerdict.EXHAUSTED
            );
        }

        @Test
        void withinBudgetButWithAnUnpricedEventIsUnverifiable() {
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("5.00"), true, new BigDecimal("10.00"))).isEqualTo(
                LlmBudgetVerdict.UNVERIFIABLE
            );
        }

        @Test
        void exhaustedTakesPriorityOverUnverifiable() {
            // Both conditions true at once: already-reached-the-cap is the more actionable signal.
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("10.00"), true, new BigDecimal("10.00"))).isEqualTo(
                LlmBudgetVerdict.EXHAUSTED
            );
        }

        @Test
        void uncappedWorkspaceCanNeverBeExhaustedButCanBeUnverifiable() {
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("999999.00"), false, null)).isEqualTo(
                LlmBudgetVerdict.WITHIN
            );
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("999999.00"), true, null)).isEqualTo(
                LlmBudgetVerdict.UNVERIFIABLE
            );
        }
    }

    @Nested
    class MonthWindow {

        @Test
        void windowIsHalfOpenUtcCalendarMonth() {
            LlmBudgetService.MonthWindow window = LlmBudgetService.MonthWindow.of(YearMonth.of(2026, 7));

            assertThat(window.from()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
            assertThat(window.to()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        }

        @Test
        void windowRollsAcrossYearBoundary() {
            LlmBudgetService.MonthWindow window = LlmBudgetService.MonthWindow.of(YearMonth.of(2026, 12));

            assertThat(window.from()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
            assertThat(window.to()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        }
    }
}
