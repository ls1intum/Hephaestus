package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PracticeFeedbackCommentFormatterTest extends BaseUnitTest {

    @Test
    void formatsSummaryMarkerBodyMetadataAndSettingsLink() {
        AgentJob job = job();
        PracticeFeedbackCommentFormatter formatter = formatter("https://hephaestus.example.com");

        String result = formatter.format("Test body content", job);

        assertThat(result)
            .contains(PullRequestCommentPoster.summaryMarkerFor(job))
            .contains("Test body content")
            .contains("Hephaestus Agent", "model&lt;&amp;&gt;", "1m 30s")
            .contains(
                "[Manage comments and Slack reminders](https://hephaestus.example.com/settings#practice-feedback)"
            );
    }

    @Test
    void preservesBasePathAndNormalizesTrailingSlash() {
        PracticeFeedbackCommentFormatter formatter = formatter("https://hephaestus.example/app/");

        String result = formatter.format("Body", job());

        assertThat(result).contains(
            "[Manage comments and Slack reminders](https://hephaestus.example/app/settings#practice-feedback)"
        );
    }

    @Test
    void appendsDeliverySettingsLinkToSupplementalComment() {
        String result = formatter("https://hephaestus.example").appendSettingsNotice("Inline feedback");

        assertThat(result).isEqualTo(
            "Inline feedback\n\n<sub>[Manage comments and Slack reminders](https://hephaestus.example/settings#practice-feedback)</sub>\n"
        );
    }

    private static PracticeFeedbackCommentFormatter formatter(String webappUrl) {
        return new PracticeFeedbackCommentFormatter(
            new ApplicationProperties(null, new ApplicationProperties.Webapp(webappUrl))
        );
    }

    private static AgentJob job() {
        AgentJob job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setStartedAt(Instant.parse("2024-01-01T00:00:00Z"));
        job.setCompletedAt(Instant.parse("2024-01-01T00:01:30Z"));
        job.setConfigSnapshot(new ObjectMapper().createObjectNode().put("upstreamModelId", "model<&>"));
        return job;
    }
}
