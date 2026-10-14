package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(ReviewIntegrationData.class)
class ResultMarkFeedbackIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ReviewIntegrationData data;

    @Autowired
    ResultMarkService marks;

    @Autowired
    ResultRepository results;

    @Autowired
    JdbcClient jdbc;

    ReviewIntegrationData.Scenario scenario;

    @BeforeEach
    void setup() {
        databaseTestUtils.cleanDatabase();
        scenario = data.scenario();
    }

    UUID feedback(Result result) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO feedback(id,workspace_id,review_id,item_id,developer_id,destination,state,body)"
                        + " VALUES(:id,:ws,:review,:item,:developer,'PRACTICE_PAGE','READY','Use the supported"
                        + " branch.')")
                .param("id", id)
                .param("ws", result.getWorkspaceId())
                .param("review", result.getReviewId())
                .param("item", result.getItemId())
                .param("developer", result.getDeveloperId())
                .update();
        jdbc.sql("INSERT INTO feedback_basis(id,workspace_id,feedback_id,developer_id,result_id,ordinal)"
                        + " VALUES(:id,:ws,:feedback,:developer,:result,0)")
                .param("id", UUID.randomUUID())
                .param("ws", result.getWorkspaceId())
                .param("feedback", id)
                .param("developer", result.getDeveloperId())
                .param("result", result.getId())
                .update();
        return id;
    }

    @Test
    void incorrectWithholdsItsPendingFeedbackWithoutEndingTheResult() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        var id = feedback(result);
        marks.create(
                result.getWorkspaceId(),
                result.getId(),
                ResultMarkKind.INCORRECT,
                scenario.account().getId(),
                "The basis is incorrect");
        assertThat(jdbc.sql("SELECT state FROM feedback WHERE workspace_id=:ws AND id=:id")
                        .param("ws", result.getWorkspaceId())
                        .param("id", id)
                        .query(String.class)
                        .single())
                .isEqualTo("WITHHELD");
        assertThat(jdbc.sql("SELECT stop_reason FROM feedback WHERE workspace_id=:ws AND id=:id")
                        .param("ws", result.getWorkspaceId())
                        .param("id", id)
                        .query(String.class)
                        .single())
                .isEqualTo("RESULT_MARKED_INCORRECT");
        assertThat(results.findById(result.getId()).orElseThrow().getEndedAt()).isNull();
    }

    @Test
    void acceptedOnlyChangesOpenProblemSelectionAndRemovalReversesIt() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        var id = feedback(result);
        var mark = marks.create(
                result.getWorkspaceId(),
                result.getId(),
                ResultMarkKind.ACCEPTED,
                scenario.account().getId(),
                "This tradeoff is accepted");
        assertThat(jdbc.sql("SELECT state FROM feedback WHERE workspace_id=:ws AND id=:id")
                        .param("ws", result.getWorkspaceId())
                        .param("id", id)
                        .query(String.class)
                        .single())
                .isEqualTo("READY");
        marks.remove(
                result.getWorkspaceId(),
                result.getId(),
                mark.getId(),
                scenario.account().getId(),
                "The tradeoff is no longer accepted");
        assertThat(results.findById(result.getId()).orElseThrow().getEndedAt()).isNull();
    }

    @Test
    void removedIncorrectRechecksModeRatherThanRestoringOffFeedback() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        var id = feedback(result);
        var mark = marks.create(
                result.getWorkspaceId(),
                result.getId(),
                ResultMarkKind.INCORRECT,
                scenario.account().getId(),
                "The basis is incorrect");
        jdbc.sql("UPDATE practice SET feedback_mode='OFF' WHERE workspace_id=:ws AND id=:id")
                .param("ws", result.getWorkspaceId())
                .param("id", result.getPracticeId())
                .update();
        marks.remove(
                result.getWorkspaceId(),
                result.getId(),
                mark.getId(),
                scenario.account().getId(),
                "The basis is now verified");
        assertThat(jdbc.sql("SELECT state FROM feedback WHERE workspace_id=:ws AND id=:id")
                        .param("ws", result.getWorkspaceId())
                        .param("id", id)
                        .query(String.class)
                        .single())
                .isEqualTo("WITHHELD");
    }
}
