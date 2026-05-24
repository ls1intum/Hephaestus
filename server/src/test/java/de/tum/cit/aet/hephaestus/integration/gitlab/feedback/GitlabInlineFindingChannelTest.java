package de.tum.cit.aet.hephaestus.integration.gitlab.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabMrResolver.MrInfo;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.spi.FindingAnchor.DiffAnchor;
import de.tum.cit.aet.hephaestus.integration.spi.InlineFindingChannel.InlineFinding;
import de.tum.cit.aet.hephaestus.integration.spi.InlineFindingChannel.InlineResult;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

@DisplayName("GitlabInlineFindingChannel")
class GitlabInlineFindingChannelTest extends BaseUnitTest {

    @Mock
    private GitLabGraphQlClientProvider gitLabProvider;

    @Mock
    private GitlabMrResolver mrResolver;

    private GitlabInlineFindingChannel channel;

    @BeforeEach
    void setUp() {
        channel = new GitlabInlineFindingChannel(gitLabProvider, mrResolver);
    }

    @Test
    @DisplayName("kind() returns GITLAB")
    void kindReturnsGitlab() {
        assertThat(channel.kind()).isEqualTo(IntegrationKind.GITLAB);
    }

    @Test
    @DisplayName("empty findings returns (0, 0)")
    void emptyFindings() {
        assertThat(channel.postInlineFindings(gitlabTarget(), List.of()))
            .isEqualTo(new InlineResult(0, 0));
    }

    @Test
    @DisplayName("rate-limit critical short-circuits and counts all as failed")
    void rateLimitCriticalShortCircuits() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(true);
        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix", "marker"))
        );
        assertThat(result.posted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("missing diffRefs results in zero posted, all counted failed")
    void missingDiffRefsSkips() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        // diffRefs absent → headSha/startSha null
        when(mrResolver.resolve(1L, "group/project", 42))
            .thenReturn(new MrInfo("gid://gitlab/MR/42", null, null, null));

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix", "marker"))
        );
        assertThat(result.posted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("non-DiffAnchor counted as failed without posting")
    void nonDiffAnchorCountedFailed() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42))
            .thenReturn(new MrInfo("gid://gitlab/MR/42", "base", "head", "start"));

        // First call (GetMergeRequestNotes for dedup) — return null/no notes
        HttpGraphQlClient dedupClient = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec dedupSpec = mock(HttpGraphQlClient.RequestSpec.class);
        lenient().when(gitLabProvider.forScope(1L)).thenReturn(dedupClient);
        lenient().when(dedupClient.documentName(any())).thenReturn(dedupSpec);
        lenient().when(dedupSpec.variable(any(), any())).thenReturn(dedupSpec);
        ClientResponseField nodesField = mock(ClientResponseField.class);
        ClientGraphQlResponse dedupResponse = mock(ClientGraphQlResponse.class);
        lenient().when(dedupResponse.field("project.mergeRequest.notes.nodes")).thenReturn(nodesField);
        lenient().when(nodesField.getValue()).thenReturn(null);
        lenient().when(dedupSpec.execute()).thenReturn(Mono.just(dedupResponse));

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new FindingAnchor.IssueAnchor("ISSUE-1", null), "skip", "marker"))
        );

        assertThat(result.posted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("posts DiffAnchor findings successfully")
    void postsDiffNotesSuccessfully() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42))
            .thenReturn(new MrInfo("gid://gitlab/MR/42", "base", "head", "start"));

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        // First exec call: dedup (GetMergeRequestNotes) — empty notes list
        ClientGraphQlResponse dedupResponse = mock(ClientGraphQlResponse.class);
        ClientResponseField nodesField = mock(ClientResponseField.class);
        lenient().when(dedupResponse.field("project.mergeRequest.notes.nodes")).thenReturn(nodesField);
        lenient().when(nodesField.getValue()).thenReturn(List.of());

        // Second exec call: CreateDiffNote — success (no errors)
        ClientGraphQlResponse createResponse = mock(ClientGraphQlResponse.class);
        ClientResponseField errorsField = mock(ClientResponseField.class);
        when(createResponse.field("createDiffNote.errors")).thenReturn(errorsField);
        when(errorsField.getValue()).thenReturn(List.of());

        when(spec.execute()).thenReturn(Mono.just(dedupResponse), Mono.just(createResponse));

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix-this", "marker"))
        );

        assertThat(result.posted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    private static FeedbackTarget gitlabTarget() {
        return new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITLAB, 1L, null),
            "group/project!42",
            null
        );
    }
}
