package de.tum.cit.aet.hephaestus.integration.scm.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.CopyDisposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyForm;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyUpdateKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNoteWriter;
import de.tum.cit.aet.hephaestus.integration.scm.github.feedback.GitHubCorrectionTestFixture;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitLabCorrectionTestFixture;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

class ScmCopyCorrectionTest extends BaseUnitTest {
    @ParameterizedTest
    @CsvSource({"GITHUB,LINE_NOTE", "GITHUB,FILE_NOTE", "GITLAB,LINE_NOTE", "GITLAB,FILE_NOTE"})
    void shouldEditTheExistingCopyAndPreserveItsPhysicalMarker(IntegrationKind kind, CopyForm form) {
        var client = mock(HttpGraphQlClient.class);
        var writer = writer(kind, client);
        var read = mock(GraphQlClient.RequestSpec.class);
        var write = mock(GraphQlClient.RequestSpec.class);
        when(client.document(anyString()))
                .thenAnswer(call -> ((String) call.getArgument(0)).startsWith("query") ? read : write);
        when(read.variable(anyString(), any())).thenReturn(read);
        when(write.variable(anyString(), any())).thenReturn(write);
        String marker = "<!-- hephaestus-feedback-copy:" + UUID.randomUUID() + " -->";
        var lookedUp = mock(ClientGraphQlResponse.class);
        when(lookedUp.getErrors()).thenReturn(List.of());
        field(
                lookedUp,
                "node",
                kind == IntegrationKind.GITHUB
                        ? Map.of(
                                "__typename",
                                form == CopyForm.FILE_NOTE ? "IssueComment" : "PullRequestReviewComment",
                                "id",
                                "copy-1",
                                "body",
                                "Original words\n" + marker,
                                "viewerDidAuthor",
                                true)
                        : Map.of("id", "copy-1", "body", "Original words\n" + marker, "author", Map.of("id", "bot")));
        if (kind == IntegrationKind.GITLAB) field(lookedUp, "currentUser.id", "bot");
        when(read.execute()).thenReturn(Mono.just(lookedUp));
        var updated = mock(ClientGraphQlResponse.class);
        when(updated.getErrors()).thenReturn(List.of());
        String result = kind == IntegrationKind.GITHUB
                ? (form == CopyForm.FILE_NOTE
                        ? "updateIssueComment.issueComment"
                        : "updatePullRequestReviewComment.pullRequestReviewComment")
                : "updateNote.note";
        field(updated, result + ".id", "copy-1");
        field(updated, result + ".url", "https://example.test/copy-1");
        if (kind == IntegrationKind.GITLAB) field(updated, "updateNote.errors", List.of());
        when(write.execute()).thenReturn(Mono.just(updated));
        var receipt = new CopyReceipt(
                UUID.randomUUID(), CopyDisposition.POSTED, "copy-1", "https://example.test/copy-1", true, form);
        var target = ScmFeedbackFixtures.target(
                new IntegrationRef(kind, 1, null),
                kind == IntegrationKind.GITHUB ? "owner/repo#42" : "owner/repo!42",
                "a".repeat(40));
        assertThat(writer.retract(target, List.of(receipt))).singleElement().satisfies(update -> {
            assertThat(update.kind()).isEqualTo(CopyUpdateKind.EDITED);
            assertThat(update.externalRef()).isEqualTo(receipt.externalRef());
        });
        var body = ArgumentCaptor.forClass(Object.class);
        verify(write).variable(eq("body"), body.capture());
        assertThat(body.getValue()).isEqualTo("This feedback was withdrawn.\n" + marker);
        var documents = ArgumentCaptor.forClass(String.class);
        Mockito.verify(client, Mockito.times(2)).document(documents.capture());
        assertThat(documents.getAllValues().get(1))
                .contains(
                        kind == IntegrationKind.GITHUB
                                ? (form == CopyForm.FILE_NOTE ? "updateIssueComment" : "updatePullRequestReviewComment")
                                : "updateNote")
                .doesNotContain("create", "addComment", "addPullRequestReview");
    }

    @ParameterizedTest
    @EnumSource(
            value = IntegrationKind.class,
            names = {"GITHUB", "GITLAB"})
    void shouldKeepTheCopyUnsettledWhenExistingContentCannotBeRead(IntegrationKind kind) {
        var client = mock(HttpGraphQlClient.class);
        var writer = writer(kind, client);
        var read = mock(GraphQlClient.RequestSpec.class);
        when(client.document(anyString())).thenReturn(read);
        when(read.variable(anyString(), any())).thenReturn(read);
        when(read.execute()).thenReturn(Mono.empty());
        var receipt =
                new CopyReceipt(UUID.randomUUID(), CopyDisposition.POSTED, "copy-1", null, true, CopyForm.LINE_NOTE);
        var target = ScmFeedbackFixtures.target(
                new IntegrationRef(kind, 1, null),
                kind == IntegrationKind.GITHUB ? "owner/repo#42" : "owner/repo!42",
                "a".repeat(40));
        assertThat(writer.retract(target, List.of(receipt)))
                .singleElement()
                .satisfies(update -> assertThat(update.kind()).isEqualTo(CopyUpdateKind.TRANSIENT));
    }

    private LineNoteWriter writer(IntegrationKind kind, HttpGraphQlClient client) {
        return kind == IntegrationKind.GITHUB
                ? GitHubCorrectionTestFixture.writer(client)
                : GitLabCorrectionTestFixture.writer(client);
    }

    private static void field(ClientGraphQlResponse response, String path, Object value) {
        var field = mock(ClientResponseField.class);
        when(response.field(path)).thenReturn(field);
        when(field.getValue()).thenReturn(value);
    }
}
