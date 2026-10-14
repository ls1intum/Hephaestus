package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.gateway.SandboxGatewayProperties;
import de.tum.cit.aet.hephaestus.agent.handler.RuntimeReviewTestData;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.scan.ScanBudget;
import de.tum.cit.aet.hephaestus.core.llm.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsagePricingState;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageSourceType;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.practices.spi.BoundModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.FrozenModel;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewMethod;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class ScanCallGuardTest extends BaseUnitTest {
    private final ReviewJobRepository jobs = mock(ReviewJobRepository.class);
    private final ReviewSnapshotQuery reviews = mock(ReviewSnapshotQuery.class);
    private final ScanBudget budget = mock(ScanBudget.class);
    private final JsonMapper mapper = new JsonMapper();
    private final ReviewJob job = RuntimeReviewTestData.job();
    private final ScanCallGuard guard = new ScanCallGuard(
            jobs,
            reviews,
            budget,
            new PracticeReviewProperties(false, 15, 5, null, 12, 16000, 200000, 1000),
            new SandboxGatewayProperties(8081, 10000, 120),
            mapper);

    private ProxyRouting route() {
        return new ProxyRouting(
                "job",
                "openai-completions",
                "https://model.example",
                UsageFundingSource.INSTANCE,
                1L,
                1L,
                1L,
                new ProxyRouting.BilledAttempt(UsageSourceType.REVIEW_JOB, job.getId(), 0, BigDecimal.ZERO, "worker"),
                null,
                null);
    }

    @BeforeEach
    void setup() {
        job.setStatus(ReviewJobStatus.RUNNING);
        job.setWorkerId("worker");
        var price = new LlmPriceSnapshot(
                UsageFundingSource.INSTANCE,
                UsagePricingState.PRICED,
                1L,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                null);
        job.setConfigSnapshot(mapper.valueToTree(new ConfigSnapshot(
                7,
                "openai-completions",
                "https://model.example",
                "test",
                1000,
                100,
                null,
                UsageFundingSource.INSTANCE,
                1L,
                1L,
                null,
                60,
                false,
                price,
                DataHandlingTier.IN_HOUSE,
                null)));
        when(jobs.findByIdAndWorkspaceId(job.getId(), 1L)).thenReturn(Optional.of(job));
        var old = RuntimeReviewTestData.assignment(null, null).practice();
        var model = new FrozenModel(
                "instance:1",
                "openai-completions",
                "test",
                null,
                Map.of("contextWindow", mapper.valueToTree(1000), "maxOutputTokens", mapper.valueToTree(100)));
        var pinned = new PinnedPractice(
                old.practiceId(),
                old.practiceVersionId(),
                old.definition(),
                old.reviewDigest(),
                new ReviewMethod(1, "b".repeat(64), List.of(new BoundModelSlot("review", ModelKind.CHAT, model))));
        when(reviews.find(1, job.getReviewId()))
                .thenReturn(Optional.of(new ReviewSnapshot(
                        job.getReviewId(),
                        1,
                        RuntimeReviewTestData.ITEM,
                        ReviewKind.SCAN,
                        new ItemVersion("test:1", null),
                        ReviewOrigin.REQUEST,
                        1,
                        List.of(pinned))));
        lenient().when(reviews.budgetUsd(1, job.getReviewId())).thenReturn(Optional.of(BigDecimal.ONE));
        lenient().when(budget.spent(1, job.getReviewId())).thenReturn(new ScanBudget.Spend(BigDecimal.ZERO, true));
        lenient().when(budget.inFlight(any(), any())).thenReturn(BigDecimal.ZERO);
    }

    private void refused(String json) {
        assertThatThrownBy(() -> guard.acquire(route(), json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED));
    }

    @Test
    void permitsBoundedTextAndReleasesItsLease() {
        try (var lease = guard.acquire(route(), "{\"max_tokens\":10}".getBytes(StandardCharsets.UTF_8))
                .orElseThrow()) {
            assertThat(lease).isNotNull();
        }
        try (var lease = guard.acquire(route(), "{\"max_tokens\":10}".getBytes(StandardCharsets.UTF_8))
                .orElseThrow()) {
            assertThat(lease).isNotNull();
        }
    }

    @Test
    void refusesTheNextCallAfterTheCapIsSpent() {
        when(budget.spent(1, job.getReviewId())).thenReturn(new ScanBudget.Spend(BigDecimal.ONE, true));
        refused("{\"max_tokens\":10}");
    }

    @Test
    void refusesUnverifiableSpend() {
        when(budget.spent(1, job.getReviewId())).thenReturn(new ScanBudget.Spend(BigDecimal.ZERO, false));
        refused("{}");
    }

    @Test
    void refusesAnOutputAboveTheFrozenLimit() {
        refused("{\"max_tokens\":101}");
        refused("{\"generationConfig\":{\"maxOutputTokens\":101}}");
    }

    @Test
    void refusesMultimediaWhoseCostCannotBeBoundedByTextBytes() {
        refused(
                "{\"messages\":[{\"content\":[{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.org/image\"}}]}]}");
    }

    @Test
    void refusesAReplacedAttempt() {
        job.setAttempt(1);
        refused("{}");
    }
}
