package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** What a public feedback history read copies from other reviews into this attempt's evidence. */
class ResultAdmissionPublicHistoryTest extends BaseUnitTest {

    @Test
    void shouldIndexTheFeedbackAndItsResultsWhenAPublicHistoryRowIncludesTheirWords() {
        UUID feedbackId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        ObjectNode read = new ObjectMapper().createObjectNode();
        ObjectNode row = read.putObject("history").putArray("feedback").addObject();
        row.put("id", feedbackId.toString());
        row.put("body", "Name the exported format when you close the issue.");
        // ReviewHistoryContentSource names each result a row rests on by its practiceResultId.
        row.putArray("basedOn").addObject().put("practiceResultId", resultId.toString());

        JobEvidenceFiles.LateCopies copies = ResultAdmissionService.publicHistoryCopies(read);

        assertThat(copies.feedbackIds()).containsExactly(feedbackId);
        assertThat(copies.practiceResultIds()).containsExactly(resultId);
    }
}
