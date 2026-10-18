package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.FeedbackCompositionService;
import de.tum.cit.aet.hephaestus.practices.spi.ClaimedCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationDecision;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationEligibility;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCompositionContext;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.PracticePageDecision;
import de.tum.cit.aet.hephaestus.practices.spi.PracticePageSupport;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class AdmissionResponseDTOTest {
    @Test
    void shouldSerializeTheAdmissionAndItsRoutingForTheSharedRunnerFixture() throws Exception {
        var mapper = JsonMapper.builder().build();
        var resultId = UUID.fromString("30000000-0000-4000-8000-000000000001");
        var readAt = Instant.parse("2026-10-14T00:00:00Z");
        var read = new FeedbackCompositionService.Read(
                new FeedbackCompositionContext(
                        new PracticePageSupport(
                                PracticePageSupport.State.COMPLETE,
                                readAt,
                                List.of(new PracticePageSupport.PracticeSupport(
                                        "handles-errors",
                                        PracticePageDecision.GATE_REFUSED,
                                        FeedbackStopReason.NO_AI,
                                        List.of())),
                                null),
                        new ConversationEligibility(
                                ConversationEligibility.State.COMPLETE,
                                readAt,
                                List.of(new ConversationEligibility.ResultDecision(
                                        resultId, ConversationDecision.GATE_REFUSED, FeedbackStopReason.NO_AI)))),
                List.of(),
                List.of());
        var problem = new AdmittedProblemDTO(
                UUID.fromString("40000000-0000-4000-8000-000000000001"),
                Severity.MAJOR,
                "The failure is dropped",
                resultId,
                0,
                null,
                "The catch returns without recording the error.",
                List.of(),
                null,
                "Record the failure.",
                true);
        var result = new AdmittedResultDTO(
                "handles-errors",
                "42",
                resultId,
                ResultOutcome.NOT_MET,
                "The failure is dropped",
                "The catch returns without recording the error.",
                mapper.readTree("{\"citations\":[]}"),
                null,
                new ClaimedCoverage(true, null, 1, 1, 1),
                List.of(problem),
                true,
                true);
        var response = new AdmissionResponseDTO(
                3,
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                UUID.fromString("20000000-0000-4000-8000-000000000001"),
                0,
                "a".repeat(64),
                List.of(result),
                List.of(new AdmissionResponseDTO.Withheld("names-things", "42", "NO_EVIDENCE")),
                FeedbackCompositionService.page(read),
                FeedbackCompositionService.conversation(read),
                false,
                false);
        try (var fixture = Objects.requireNonNull(getClass().getResourceAsStream("/agent/admission-response.json"))) {
            assertThat(mapper.readTree(mapper.writeValueAsString(response))).isEqualTo(mapper.readTree(fixture));
        }
    }
}
