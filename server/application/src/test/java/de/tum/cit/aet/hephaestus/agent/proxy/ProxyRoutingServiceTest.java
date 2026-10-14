package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmApiProtocol;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.job.PracticeScriptKindTotal;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.RuntimeJobTestData;
import de.tum.cit.aet.hephaestus.core.llm.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsagePricingState;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageSourceType;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.JobJwt;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class ProxyRoutingServiceTest extends BaseUnitTest {
    private final ReviewJobRepository jobs = mock(ReviewJobRepository.class);
    private final JsonMapper mapper = new JsonMapper();
    private final ProxyRoutingService routing =
            new ProxyRoutingService(jobs, mapper, new MentorProxyCredentialRegistry());
    private final ReviewJob job = new ReviewJob();
    private ProxyIdentity identity;

    @BeforeEach
    void setup() {
        job.setId(UUID.randomUUID());
        RuntimeJobTestData.initialize(job, 7L);
        job.setStatus(ReviewJobStatus.RUNNING);
        job.setAttempt(2);
        job.setWorkerId("worker");
        job.setLlmInputTokens(100);
        var snapshot = new ConfigSnapshot(
                ConfigSnapshot.SCHEMA_VERSION,
                model(LlmApiProtocol.OPENAI_RESPONSES, UsageFundingSource.INSTANCE),
                16000,
                1000,
                600,
                false,
                Map.of(ModelKind.EMBEDDING, model(LlmApiProtocol.OPENAI_EMBEDDINGS, UsageFundingSource.WORKSPACE)));
        job.setConfigSnapshot(snapshot.toJson(mapper));
        var now = Instant.now();
        identity = new ProxyIdentity(
                new JobJwt(job.getId(), 7L, 2, Set.of("llm_proxy"), "id", now, now.plusSeconds(60)), "worker", null);
        lenient().when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
    }

    @Test
    void reviewRoutingPricesOnlyItsOwnModelWithoutScriptUsageQueries() {
        var route = routing.resolve(identity, request("/internal/llm/responses"));
        assertThat(route.apiProtocol()).isEqualTo("openai-responses");
        assertThat(route.sourceId()).isEqualTo(job.getId());
        assertThat(route.inFlightSpendUsd()).isEqualByComparingTo("0.000100");
        verify(jobs).findById(job.getId());
        verifyNoMoreInteractions(jobs);
    }

    @Test
    void scriptRoutingPricesItsPurseWithoutChargingTheReviewPurseAndCountsAllSlotTokens() {
        when(jobs.sumPracticeScriptUsageByKind(7L, job.getId(), 2))
                .thenReturn(List.of(
                        new PracticeScriptKindTotal(ModelKind.EMBEDDING, 1, 200, 0, 0, 0, 0, 1),
                        new PracticeScriptKindTotal(ModelKind.CHAT, 1, 900, 0, 0, 0, 0, 1)));
        var route = routing.resolve(identity, request("/internal/llm/practice-script/embedding/embeddings"));
        assertThat(route.apiProtocol()).isEqualTo("openai-embeddings");
        assertThat(route.connectionScope()).isEqualTo(UsageFundingSource.WORKSPACE);
        var attempt = Objects.requireNonNull(route.attempt());
        assertThat(attempt.sourceType()).isEqualTo(UsageSourceType.PRACTICE_SCRIPT_EMBEDDING);
        assertThat(attempt.spentUsd()).isEqualByComparingTo("0.000200");
        assertThat(attempt.directModelTokens()).isEqualTo(1100);
    }

    @Test
    void refusesAnAttemptReplacedSinceAuthentication() {
        job.setAttempt(3);
        assertThatThrownBy(() -> routing.resolve(identity, request("/internal/llm/responses")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void missingSlotIsA404AndDoesNotQueryUsage() {
        assertThatThrownBy(() -> routing.resolve(identity, request("/internal/llm/practice-script/reranking/rerank")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(jobs).findById(job.getId());
        verifyNoMoreInteractions(jobs);
    }

    @Test
    void mentorCredentialsCannotAcquireScriptModelRouting() {
        var mentor = new ProxyIdentity(
                null, null, new MentorProxyCredentialRegistry.AuthenticatedSession(UUID.randomUUID(), "test-hash"));
        assertThatThrownBy(() -> routing.resolve(mentor, request("/internal/llm/practice-script/chat/responses")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(jobs);
    }

    private static FrozenModel model(LlmApiProtocol protocol, UsageFundingSource purse) {
        var price = new LlmPriceSnapshot(
                purse, UsagePricingState.PRICED, 1L, null, BigDecimal.ONE, BigDecimal.ONE, null, null);
        return new FrozenModel(
                protocol,
                "https://models.example/v1",
                "model-1",
                purse,
                1L,
                2L,
                7L,
                DataHandlingTier.IN_HOUSE,
                null,
                price);
    }

    private static MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("POST", path);
    }
}
