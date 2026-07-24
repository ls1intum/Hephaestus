package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.util.stream.IntStream;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LlmConnectionProbeServiceTest extends BaseUnitTest {

    private MockWebServer upstream;
    private LlmConnectionProbeService service;

    @BeforeEach
    void setUp() throws IOException {
        upstream = new MockWebServer();
        upstream.start();
        service = new LlmConnectionProbeService(
            mock(LlmConnectionRepository.class),
            mock(EgressPolicy.class),
            new ObjectMapper(),
            true
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        upstream.close();
    }

    @Test
    void shouldFailSafelyWhenProviderResponseExceedsByteLimit() {
        upstream.enqueue(jsonResponse("{\"data\":[],\"padding\":\"" + "x".repeat(1024 * 1024) + "\"}"));

        LlmProbeResultDTO result = service.probeDraft(request());

        assertThat(result.reachable()).isFalse();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.message()).containsIgnoringCase("too large");
        assertThat(result.models()).isEmpty();
    }

    @Test
    void shouldLimitNumberOfReturnedModels() {
        String models = IntStream.range(0, 1_001)
            .mapToObj(i -> "{\"id\":\"model-" + i + "\"}")
            .collect(java.util.stream.Collectors.joining(","));
        upstream.enqueue(jsonResponse("{\"data\":[" + models + "]}"));

        LlmProbeResultDTO result = service.probeDraft(request());

        assertThat(result.reachable()).isTrue();
        assertThat(result.models()).hasSize(1_000);
    }

    @Test
    void shouldIgnoreModelIdsThatCannotFitCatalogColumn() {
        String tooLong = "x".repeat(257);
        upstream.enqueue(jsonResponse("{\"data\":[{\"id\":\"" + tooLong + "\"},{\"id\":\"valid-model\"}]}"));

        LlmProbeResultDTO result = service.probeDraft(request());

        assertThat(result.reachable()).isTrue();
        assertThat(result.models()).containsExactly("valid-model");
    }

    private ProbeLlmConnectionRequestDTO request() {
        return new ProbeLlmConnectionRequestDTO(
            upstream.url("/v1").toString(),
            "openai-completions",
            LlmAuthMode.BEARER,
            null
        );
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build();
    }
}
