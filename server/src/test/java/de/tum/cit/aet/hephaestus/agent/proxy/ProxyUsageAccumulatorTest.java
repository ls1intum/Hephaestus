package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ProxyUsageAccumulatorTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

    @Mock
    private AgentJobRepository jobRepository;

    @InjectMocks
    private ProxyUsageAccumulator accumulator;

    private JsonNode json(String body) {
        return MAPPER.readTree(body);
    }

    @Test
    void completionsUsageBillsNonCachedInputSeparatelyFromCacheReads() {
        // prompt_tokens is inclusive of cached; the input bucket must be the non-cached remainder.
        var body = json(
            "{\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":50," +
                "\"prompt_tokens_details\":{\"cached_tokens\":20}," +
                "\"completion_tokens_details\":{\"reasoning_tokens\":10}}}"
        );

        accumulator.accumulate(JOB, body, false);

        verify(jobRepository).accumulateLlmUsage(JOB, 80, 50, 10, 20, 0);
    }

    @Test
    void responsesUsageReadsInputAndOutputTokenShape() {
        var body = json(
            "{\"usage\":{\"input_tokens\":200,\"output_tokens\":70," +
                "\"input_tokens_details\":{\"cached_tokens\":50}," +
                "\"output_tokens_details\":{\"reasoning_tokens\":25}}}"
        );

        accumulator.accumulate(JOB, body, true);

        verify(jobRepository).accumulateLlmUsage(JOB, 150, 70, 25, 50, 0);
    }

    @Test
    void nullJobIdIsANoOp() {
        accumulator.accumulate(null, json("{\"usage\":{\"prompt_tokens\":10}}"), false);
        verifyNoInteractions(jobRepository);
    }

    @Test
    void missingUsageBlockRecordsNothing() {
        accumulator.accumulate(JOB, json("{\"choices\":[]}"), false);
        verifyNoInteractions(jobRepository);
    }
}
