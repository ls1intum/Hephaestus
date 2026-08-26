package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
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
            .contains(
                "<sub>Practice review &middot; model&lt;&amp;&gt; &middot; AI-generated and can be inaccurate." +
                    " React with 👍 or 👎, or reply, to give feedback.</sub>"
            )
            .contains(
                "[Why you're seeing this and how to stop it](https://hephaestus.example.com/settings#practice-feedback)"
            );
    }

    @Test
    void preservesBasePathAndNormalizesTrailingSlash() {
        PracticeFeedbackCommentFormatter formatter = formatter("https://hephaestus.example/app/");

        String result = formatter.format("Body", job());

        assertThat(result).contains(
            "[Why you're seeing this and how to stop it](https://hephaestus.example/app/settings#practice-feedback)"
        );
    }

    @Test
    void appendsDisclosureAndSettingsLink() {
        String result = formatter("https://hephaestus.example").appendDisclosure("Approved feedback", job());

        assertThat(result)
            .startsWith("Approved feedback\n\n")
            .contains(
                "<sub>Practice review &middot; model&lt;&amp;&gt; &middot; AI-generated and can be inaccurate." +
                    " React with 👍 or 👎, or reply, to give feedback.</sub>"
            )
            .endsWith(
                "<sub>[Why you're seeing this and how to stop it](https://hephaestus.example/settings#practice-feedback)</sub>\n"
            );
    }

    @Test
    void appendsCompactDisclosureToInlineComment() {
        String result = formatter("https://hephaestus.example").appendInlineDisclosure("Inline feedback");

        assertThat(result).isEqualTo(
            "Inline feedback\n\n" +
                "<sub>AI-generated &middot; " +
                "[Why you're seeing this and how to stop it]" +
                "(https://hephaestus.example/settings#practice-feedback)</sub>\n"
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
        job.setConfigSnapshot(new ObjectMapper().createObjectNode().put("upstreamModelId", "model<&>"));
        return job;
    }
}
