package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.PayloadParsingException;
import io.nats.client.Message;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("unit")
class GitLabMessageHandlerTest {

    private NatsMessageDeserializer deserializer;
    private TransactionTemplate transactionTemplate;
    private AtomicReference<String> capturedPayload;
    private TestHandler handler;

    @BeforeEach
    void setUp() {
        deserializer = mock(NatsMessageDeserializer.class);
        // Mock TransactionTemplate to execute the callback directly (no real transaction manager needed)
        transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        })
            .when(transactionTemplate)
            .executeWithoutResult(any());

        capturedPayload = new AtomicReference<>();
        handler = new TestHandler(deserializer, transactionTemplate, capturedPayload);
    }

    @Test
    void onMessage_matchingSubject_deserializesAndCallsHandleEvent() throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("gitlab.group.project.merge_request");
        when(deserializer.deserialize(msg, String.class)).thenReturn("{\"action\":\"open\"}");

        handler.onMessage(msg);

        assertThat(capturedPayload.get()).isEqualTo("{\"action\":\"open\"}");
    }

    @Test
    void onMessage_nonMatchingSubject_rejectsWithoutDeserialization() throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("gitlab.group.project.issue");

        handler.onMessage(msg);

        verify(deserializer, never()).deserialize(any(), any());
        assertThat(capturedPayload.get()).isNull();
    }

    @Test
    void onMessage_deserializationFailure_throwsPayloadParsingException() throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("gitlab.group.project.merge_request");
        when(deserializer.deserialize(msg, String.class)).thenThrow(new IOException("bad json"));

        assertThatThrownBy(() -> handler.onMessage(msg))
            .isInstanceOf(PayloadParsingException.class)
            .hasMessageContaining("Payload parsing failed");
    }

    @Test
    void onMessage_handleEventIsCalledWithinTransactionTemplate() throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("gitlab.group.project.merge_request");
        when(deserializer.deserialize(msg, String.class)).thenReturn("payload");

        handler.onMessage(msg);

        verify(transactionTemplate).executeWithoutResult(any());
    }

    private static class TestHandler extends GitLabMessageHandler<String> {

        private final AtomicReference<String> capturedPayload;

        TestHandler(
            NatsMessageDeserializer deserializer,
            TransactionTemplate transactionTemplate,
            AtomicReference<String> capturedPayload
        ) {
            super(String.class, deserializer, transactionTemplate);
            this.capturedPayload = capturedPayload;
        }

        @Override
        protected void handleEvent(String eventPayload) {
            capturedPayload.set(eventPayload);
        }

        @Override
        public GitLabEventType getEventType() {
            return GitLabEventType.MERGE_REQUEST;
        }
    }
}
