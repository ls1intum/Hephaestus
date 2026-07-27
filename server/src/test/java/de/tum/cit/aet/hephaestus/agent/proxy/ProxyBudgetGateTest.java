package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetHeadroom;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;

/**
 * The bound the gate promises: <b>an attempt is refused as soon as its OWN completed calls have
 * consumed the headroom the ledger last showed.</b>
 *
 * <p>Every test below fixes a ledger that says the workspace has spent nothing — because that is what
 * the ledger DOES say for the whole length of a run, no matter how much that run has spent. A ledger
 * row is only appended when an agent job or mentor turn ends, so a gate that consults the ledger alone
 * is blind to the one execution most worth stopping. The mutation these tests kill is exactly that
 * blindness: pass {@code BigDecimal.ZERO} instead of {@code routing.inFlightSpendUsd()} and the
 * runaway cases below all go green with a forwarded call.
 */
class ProxyBudgetGateTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;
    private static final BigDecimal ONE_DOLLAR = new BigDecimal("1.00");

    @Mock
    private LlmBudgetService budgetService;

    private ProxyBudgetGate gate;

    @BeforeEach
    void setUp() {
        gate = new ProxyBudgetGate(budgetService);
    }

    /** A $1 instance cap with nothing recorded against it — a run in progress always looks like this. */
    private static LlmBudgetHeadroom instanceCapOfOneDollar() {
        return new LlmBudgetHeadroom(BigDecimal.ZERO, ONE_DOLLAR, false, null, null, false);
    }

    private static LlmBudgetHeadroom workspaceCapOfOneDollar() {
        return new LlmBudgetHeadroom(null, null, false, BigDecimal.ZERO, ONE_DOLLAR, false);
    }

    private static ProxyRouting routing(@Nullable FundingSource scope, @Nullable String spentSoFarUsd) {
        return new ProxyRouting(
            "job:test",
            "openai-completions",
            "https://frozen.example.com/v1",
            scope,
            7L,
            8L,
            WORKSPACE_ID,
            spentSoFarUsd == null
                ? null
                : new ProxyRouting.BilledAttempt(
                      LlmUsageSourceType.AGENT_JOB,
                      UUID.randomUUID(),
                      0,
                      new BigDecimal(spentSoFarUsd)
                  )
        );
    }

    @Nested
    @DisplayName("the bound: one attempt cannot outspend the cap by more than a call")
    class TheBound {

        /**
         * The ledger on its own says ALLOWED for this workspace at both spends below; what refuses the
         * dollar call is the attempt's own spend counted on top of it. Both halves are asserted
         * together, so a gate that consulted only the ledger fails here. The cent of headroom is the
         * other edge of the same boundary: the gate stops AT the cap, not before it.
         */
        @ParameterizedTest(name = "an attempt that has spent ${0} of a $1.00 cap is blocked={1}")
        @CsvSource({ "0.99, false", "1.00, true" })
        void anAttemptIsJudgedOnItsOwnUnrecordedSpend(String ownSpendUsd, boolean blocked) {
            LlmBudgetHeadroom headroom = instanceCapOfOneDollar();
            when(budgetService.headroom(WORKSPACE_ID)).thenReturn(headroom);

            assertThat(headroom.decide().blocks(FundingSource.INSTANCE))
                .as("the ledger alone allows both of these calls")
                .isFalse();
            assertThat(gate.isBlocked(routing(FundingSource.INSTANCE, ownSpendUsd))).isEqualTo(blocked);
        }

        /**
         * What makes the bound hold call after call rather than once per TTL: the expensive ledger read
         * is cached per workspace, but the attempt's spend is re-read on every request. A gate that
         * cached the finished verdict instead would forward the second call here.
         */
        @Test
        @DisplayName("a second call is judged on fresh attempt spend even though the ledger read is cached")
        void theAttemptSpendIsFreshEvenWhenTheLedgerVerdictIsCached() {
            when(budgetService.headroom(WORKSPACE_ID)).thenReturn(instanceCapOfOneDollar());

            assertThat(gate.isBlocked(routing(FundingSource.INSTANCE, "0.60"))).isFalse();
            assertThat(gate.isBlocked(routing(FundingSource.INSTANCE, "1.20"))).isTrue();

            verify(budgetService, times(1)).headroom(WORKSPACE_ID);
        }
    }

    @Nested
    @DisplayName("the two purses stay separate, and an unattributable attempt is judged against both")
    class WhichPursePays {

        @Test
        @DisplayName("an exhausted host budget does not stop a call the workspace pays for itself")
        void anExhaustedInstancePurseDoesNotBlockAWorkspaceFundedAttempt() {
            when(budgetService.headroom(WORKSPACE_ID)).thenReturn(
                new LlmBudgetHeadroom(new BigDecimal("500.00"), ONE_DOLLAR, false, BigDecimal.ZERO, null, false)
            );

            assertThat(gate.isBlocked(routing(FundingSource.WORKSPACE, "9.99"))).isFalse();
        }

        @Test
        @DisplayName("a workspace-funded attempt's spend is charged to the workspace's own cap")
        void aWorkspaceFundedAttemptIsJudgedAgainstItsOwnCap() {
            when(budgetService.headroom(WORKSPACE_ID)).thenReturn(workspaceCapOfOneDollar());

            assertThat(gate.isBlocked(routing(FundingSource.WORKSPACE, "1.00"))).isTrue();
        }

        /**
         * Fail-safe, not fail-open: a legacy snapshot that cannot say who paid has its spend charged to
         * both caps, exactly as {@code LlmBudgetDecision} judges such a call against both.
         */
        @Test
        @DisplayName("an attempt with no known funding source is charged to both purses")
        void anUnattributableAttemptIsChargedToBothPurses() {
            when(budgetService.headroom(WORKSPACE_ID)).thenReturn(instanceCapOfOneDollar());

            assertThat(gate.isBlocked(routing(null, "1.00"))).isTrue();
        }
    }

    /**
     * The same bound, reached the way a mentor turn reaches it: through the credential its sandbox
     * presents. A mentor turn has no {@code agent_job} row, so until it was given a meter of its own it
     * could spend without limit inside one turn — the gate saw a route with nothing to charge and let
     * every call through.
     */
    @Nested
    @DisplayName("a mentor turn is bounded by its own completed calls, exactly like a job attempt")
    class AMentorTurn {

        private final MentorProxyCredentialRegistry credentials = new MentorProxyCredentialRegistry();

        private static final LlmPriceSnapshot TEN_DOLLARS_PER_MILLION_INPUT = new LlmPriceSnapshot(
            FundingSource.INSTANCE,
            PricingState.PRICED,
            1L,
            null,
            new BigDecimal("10"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );

        /** Mint a session credential, start a turn on it, and burn {@code inputTokens} across one call. */
        private ProxyRouting turnThatHasSpent(int inputTokens) {
            UUID sessionId = UUID.randomUUID();
            String token = credentials.mint(
                sessionId,
                new MentorProxyCredentialRegistry.Route(
                    "openai-responses",
                    "https://frozen.example.com/v1",
                    FundingSource.INSTANCE,
                    7L,
                    8L,
                    WORKSPACE_ID
                )
            );
            MentorTurnMeter meter = new MentorTurnMeter(UUID.randomUUID(), TEN_DOLLARS_PER_MILLION_INPUT);
            credentials.bindTurn(sessionId, meter);
            if (inputTokens > 0) {
                credentials.accumulate(meter.turnId(), new ProxyTokenUsage(inputTokens, 0, 0, 0));
            }
            return credentials.validate(token).orElseThrow();
        }

        /**
         * At $10 per million input tokens, 100k tokens is $1.00 — the whole cap, none of it in the
         * ledger yet; 90k is still under it. The ledger says ALLOWED for this workspace at both, so
         * only the turn's own completed calls can stop the refused one.
         *
         * <p>Kills "give the mentor route a null attempt again": the turn would then spend the whole
         * cap inside one turn and still be waved through for as long as it kept calling.
         */
        @ParameterizedTest(name = "a turn that has burned {0} input tokens of a $1.00 cap is blocked={1}")
        @CsvSource({ "90000, false", "100000, true" })
        void aTurnIsJudgedOnItsOwnCompletedCalls(int inputTokens, boolean blocked) {
            LlmBudgetHeadroom headroom = instanceCapOfOneDollar();
            when(budgetService.headroom(WORKSPACE_ID)).thenReturn(headroom);

            assertThat(headroom.decide().blocks(FundingSource.INSTANCE))
                .as("the ledger alone allows both of these turns")
                .isFalse();
            assertThat(gate.isBlocked(turnThatHasSpent(inputTokens))).isEqualTo(blocked);
        }
    }

    @Nested
    @DisplayName("routes with nothing to charge")
    class NothingToCharge {

        /**
         * A mentor session between turns names no execution, so there is no in-flight term to add —
         * the residual the registry documents. The ledger still decides.
         */
        @Test
        @DisplayName("a route with no live execution contributes no in-flight spend")
        void aRouteWithNoLiveExecutionIsJudgedOnTheLedgerAlone() {
            when(budgetService.headroom(WORKSPACE_ID)).thenReturn(instanceCapOfOneDollar());

            assertThat(gate.isBlocked(routing(FundingSource.INSTANCE, null))).isFalse();
        }

        @Test
        @DisplayName("an unattributable route with no workspace never blocks, and never queries the ledger")
        void aRouteWithNoWorkspaceFailsOpen() {
            ProxyRouting noWorkspace = new ProxyRouting(
                "job:legacy",
                "openai-completions",
                "https://frozen.example.com/v1",
                null,
                null,
                null,
                null,
                null
            );

            assertThat(gate.isBlocked(noWorkspace)).isFalse();
        }
    }
}
