package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto.GitLabPushEventDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Message;
import java.io.IOException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("GitLabPushMessageHandler")
class GitLabPushMessageHandlerTest extends BaseUnitTest {

    @Mock
    private GitLabProjectProcessor projectProcessor;

    @Mock
    private NatsMessageDeserializer deserializer;

    private TransactionTemplate transactionTemplate;
    private GitLabPushMessageHandler handler;

    @BeforeEach
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        // Lenient: not all tests trigger transactional execution (e.g., getEventType, nonPushSubject)
        lenient()
            .doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<TransactionStatus> callback = invocation.getArgument(0);
                callback.accept(null);
                return null;
            })
            .when(transactionTemplate)
            .executeWithoutResult(any());

        handler = new GitLabPushMessageHandler(projectProcessor, deserializer, transactionTemplate);
    }

    @Test
    @DisplayName("returns PUSH event type")
    void getEventType_returnsPush() {
        org.assertj.core.api.Assertions.assertThat(handler.getEventType()).isEqualTo(GitLabEventType.PUSH);
    }

    @Test
    @DisplayName("valid push event upserts project")
    void validPushEvent_upsertsProject() throws IOException {
        var projectInfo = new GitLabPushEventDTO.ProjectInfo(
            246765L,
            "demo-repository",
            "Demo repo",
            "https://gitlab.lrz.de/hephaestustest/demo-repository",
            "HephaestusTest",
            "hephaestustest/demo-repository",
            "main",
            0
        );
        var pushEvent = new GitLabPushEventDTO(
            "push",
            "refs/heads/main",
            "9c5dedd52046bb5213189afc25f75e608a98d462",
            "a4bf10d93a2d136f1db911b6f1c03d26d835a44f",
            "a4bf10d93a2d136f1db911b6f1c03d26d835a44f",
            246765L,
            projectInfo,
            3
        );

        Message msg = mockMessage("gitlab.hephaestustest.demo-repository.push", pushEvent);
        handler.onMessage(msg);

        verify(projectProcessor).processPushEvent(projectInfo);
    }

    @Test
    @DisplayName("branch deletion skips processing")
    void branchDeletion_skipsProcessing() throws IOException {
        var projectInfo = new GitLabPushEventDTO.ProjectInfo(
            1L,
            "proj",
            null,
            "https://gitlab.com/org/proj",
            null,
            "org/proj",
            "main",
            0
        );
        var pushEvent = new GitLabPushEventDTO(
            "push",
            "refs/heads/feature-branch",
            "abc123",
            "0000000000000000000000000000000000000000", // branch deletion
            null,
            1L,
            projectInfo,
            0
        );

        Message msg = mockMessage("gitlab.org.proj.push", pushEvent);
        handler.onMessage(msg);

        verify(projectProcessor, never()).processPushEvent(any());
    }

    @Test
    @DisplayName("push event with null project skips processing")
    void nullProject_skipsProcessing() throws IOException {
        var pushEvent = new GitLabPushEventDTO(
            "push",
            "refs/heads/main",
            "before",
            "after",
            "after",
            null,
            null, // null project
            0
        );

        Message msg = mockMessage("gitlab.org.proj.push", pushEvent);
        handler.onMessage(msg);

        verify(projectProcessor, never()).processPushEvent(any());
    }

    @Test
    @DisplayName("processor returning null logs warning")
    void processorReturnsNull_logsWarning() throws IOException {
        var projectInfo = new GitLabPushEventDTO.ProjectInfo(
            1L,
            "proj",
            null,
            "https://gitlab.com/org/proj",
            null,
            "org/proj",
            "main",
            0
        );
        var pushEvent = new GitLabPushEventDTO(
            "push",
            "refs/heads/main",
            "before",
            "after",
            "after",
            1L,
            projectInfo,
            1
        );

        when(projectProcessor.processPushEvent(projectInfo)).thenReturn(null);

        Message msg = mockMessage("gitlab.org.proj.push", pushEvent);
        handler.onMessage(msg);

        verify(projectProcessor).processPushEvent(projectInfo);
        // Processor was called but returned null — handler should still function
    }

    @Test
    @DisplayName("non-push subject is rejected by base class")
    void nonPushSubject_rejected() throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("gitlab.org.proj.merge_request");

        handler.onMessage(msg);

        verify(deserializer, never()).deserialize(any(), any());
        verify(projectProcessor, never()).processPushEvent(any());
    }

    private Message mockMessage(String subject, GitLabPushEventDTO event) throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn(subject);
        when(deserializer.deserialize(msg, GitLabPushEventDTO.class)).thenReturn(event);
        return msg;
    }
}
