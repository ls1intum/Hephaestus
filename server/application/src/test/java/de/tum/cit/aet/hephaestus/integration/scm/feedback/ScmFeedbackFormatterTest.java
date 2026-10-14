package de.tum.cit.aet.hephaestus.integration.scm.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
import de.tum.cit.aet.hephaestus.core.WorkspaceSubdomainProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspaceSummaryQuery;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScmFeedbackFormatterTest extends BaseUnitTest {

    @Test
    void shouldUseTheFinalAppendedKeyWhenFeedbackQuotesAnotherTag() {
        String body =
                "Quoted <!-- hephaestus-diff-note-ck=wrong -->\n" + ScmFeedbackFormatter.deliveryTag("actual-copy");
        assertThat(body).endsWith("<!-- hephaestus-diff-note-ck=actual-copy -->");
        assertThat(ScmFeedbackFormatter.lastDeliveryKey(body)).isEqualTo("actual-copy");
        assertThat(ScmFeedbackFormatter.lastDeliveryKey("no delivery tag")).isNull();
    }

    @Test
    void formatsSummaryMarkerBodyMetadataAndSettingsLink() {
        ItemRef item = item();
        ScmFeedbackFormatter formatter = formatter("https://hephaestus.example.com");

        String result = formatter.format("Test body content", item, "model<&>");

        assertThat(result)
                .contains("Test body content")
                .contains("<sub>Practice review &middot; model&lt;&amp;&gt;. This feedback is"
                        + " AI-generated and can be inaccurate. Answer or disagree in"
                        + " [Hephaestus](https://hephaestus.example.com/w/team/feedback/42).</sub>")
                .doesNotContain("React with")
                .contains("[Why you see this and how to stop"
                        + " it](https://hephaestus.example.com/settings#practice-feedback)");
    }

    @Test
    void preservesBasePathAndNormalizesTrailingSlash() {
        ScmFeedbackFormatter formatter = formatter("https://hephaestus.example/app/");

        String result = formatter.format("Body", item(), "model<&>");

        assertThat(result)
                .contains("[Why you see this and how to stop"
                        + " it](https://hephaestus.example/app/settings#practice-feedback)");
    }

    @Test
    void appendsDisclosureAndSettingsLink() {
        String result =
                formatter("https://hephaestus.example").appendDisclosure("Approved feedback", item(), "model<&>");

        assertThat(result)
                .startsWith("Approved feedback\n\n")
                .contains("<sub>Practice review &middot; model&lt;&amp;&gt;. This feedback is"
                        + " AI-generated and can be inaccurate. Answer or disagree in"
                        + " [Hephaestus](https://hephaestus.example/w/team/feedback/42).</sub>")
                .endsWith("<sub>[Why you see this and how to stop"
                        + " it](https://hephaestus.example/settings#practice-feedback)</sub>\n");
    }

    @Test
    void appendsFeedbackPromptToInlineComment() {
        String result = formatter("https://hephaestus.example").appendLineNotePrompt("Inline feedback", item());

        assertThat(result)
                .isEqualTo("Inline feedback\n\n"
                        + "<sub>AI-generated feedback. Answer or disagree in"
                        + " [Hephaestus](https://hephaestus.example/w/team/feedback/42).</sub>\n");
    }

    /**
     * An issue comment links to the reader's reviews of the issue, the same place for every review
     * of it.
     */
    @Test
    void linksAnIssueCommentToTheIssue() {
        ItemRef item = new ItemRef(1L, 7L, new ItemKind("scm.issue"), 99L, null, null);
        String result = formatter("https://hephaestus.example").format("Body", item, null);

        assertThat(result).contains("[Hephaestus](https://hephaestus.example/w/team/feedback/7)");
    }

    @Test
    void linksProviderCommentsToTheWorkspaceAddressButKeepsPreferencesOnTheApex() {
        var formatter = new ScmFeedbackFormatter(
                new ApplicationProperties(null, new ApplicationProperties.Webapp("https://hephaestus.example")),
                teamWorkspace(),
                new WorkspaceSubdomainProperties(true, "hephaestus.example"));
        assertThat(formatter.format("Body", item(), "model<&>"))
                .contains("https://team.hephaestus.example/feedback/42")
                .contains("https://hephaestus.example/settings#practice-feedback")
                .doesNotContain("/w/team");
    }

    private static ScmFeedbackFormatter formatter(String webappUrl) {
        return new ScmFeedbackFormatter(
                new ApplicationProperties(null, new ApplicationProperties.Webapp(webappUrl)),
                teamWorkspace(),
                new WorkspaceSubdomainProperties(false, ""));
    }

    private static ItemRef item() {
        return new ItemRef(1L, 42L, new ItemKind("scm.pull_request"), 99L, null, null);
    }

    /** Every workspace is "team": what the footer's link needs of the workspace. */
    private static WorkspaceSummaryQuery teamWorkspace() {
        WorkspaceSummaryQuery workspaces = mock(WorkspaceSummaryQuery.class);
        when(workspaces.findById(anyLong()))
                .thenReturn(Optional.of(new WorkspaceSummaryQuery.WorkspaceSummary(1L, "team", "Team")));
        return workspaces;
    }
}
