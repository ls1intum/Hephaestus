package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackContent;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.SummaryHandle;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrInfo;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

@DisplayName("GitlabFeedbackChannel")
class GitlabFeedbackChannelTest extends BaseUnitTest {

    @Mock
    private GitLabGraphQlClientProvider gitLabProvider;

    @Mock
    private GitlabMrResolver mrResolver;

    private GitlabFeedbackChannel channel;

    @BeforeEach
    void setUp() {
        channel = new GitlabFeedbackChannel(gitLabProvider, mrResolver);
    }

    @Test
    @DisplayName("postSummary returns SummaryHandle with note id from mutation")
    void postSummaryReturnsNoteId() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", "base", "head", "start")
        );

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mockGitlabResponse("gid://gitlab/Note/789");
        when(spec.execute()).thenReturn(Mono.just(response));

        SummaryHandle handle = channel.postSummary(target, new FeedbackContent("hello", "marker"));

        assertThat(handle).isNotNull();
        assertThat(handle.externalId()).isEqualTo("gid://gitlab/Note/789");
    }

    @Test
    @DisplayName("postSummary escapes /approve slash command before posting")
    void postSummaryEscapesSlashCommands() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", "base", "head", "start")
        );

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mockGitlabResponse("gid://gitlab/Note/789");
        when(spec.execute()).thenReturn(Mono.just(response));

        channel.postSummary(target, new FeedbackContent("/approve please\nsome body", "marker"));

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(spec).variable(eq("body"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().toString()).contains("`/approve`");
    }

    @Test
    @DisplayName("postSummary throws when GitLab rate limit critical")
    void throwsOnRateLimit() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(true);
        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("rate limit critical");
    }

    @Test
    @DisplayName("postSummary throws on createNote mutation errors")
    void throwsOnMutationErrors() {
        FeedbackTarget target = gitlabTarget();
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", "base", "head", "start")
        );

        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(client.documentName(any())).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField errorsField = mock(ClientResponseField.class);
        when(response.field("createNote.errors")).thenReturn(errorsField);
        when(errorsField.getValue()).thenReturn(List.of("not allowed"));
        when(spec.execute()).thenReturn(Mono.just(response));

        assertThatThrownBy(() -> channel.postSummary(target, new FeedbackContent("body", "marker")))
            .isInstanceOf(FeedbackDeliveryException.class)
            .hasMessageContaining("createNote failed");
    }

    private static FeedbackTarget gitlabTarget() {
        return new FeedbackTarget(new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/project!42", null);
    }

    @SuppressWarnings("unchecked")
    private ClientGraphQlResponse mockGitlabResponse(String noteId) {
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField idField = mock(ClientResponseField.class);
        when(response.field("createNote.note.id")).thenReturn(idField);
        when(idField.getValue()).thenReturn(noteId);
        ClientResponseField errorsField = mock(ClientResponseField.class);
        lenient().when(response.field("createNote.errors")).thenReturn(errorsField);
        lenient().when(errorsField.getValue()).thenReturn(List.of());
        return response;
    }
}
