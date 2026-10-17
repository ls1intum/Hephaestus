package de.tum.cit.aet.hephaestus.integration.scm.github.discussion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.handler.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.ProcessingContextFactory;
import de.tum.cit.aet.hephaestus.integration.scm.github.discussion.dto.GitHubDiscussionEventDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionTemplate;

// Transfers and unknown actions must not upsert a discussion that has left the source repository.
class GitHubDiscussionMessageHandlerRoutingTest extends BaseUnitTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private ProcessingContextFactory contextFactory;

    @Mock
    private GitHubDiscussionProcessor discussionProcessor;

    @Mock
    private SyncSchedulerProperties syncSchedulerProperties;

    @Mock
    private NatsMessageDeserializer deserializer;

    @Mock
    private ProcessingContext context;

    private GitHubDiscussionMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GitHubDiscussionMessageHandler(
                contextFactory, discussionProcessor, syncSchedulerProperties, deserializer, new TransactionTemplate());
    }

    @Test
    void shouldRouteTransferredToRemovalNotUpsert() throws Exception {
        GitHubDiscussionEventDTO event = event("transferred");
        when(contextFactory.forWebhookEvent(event)).thenReturn(Optional.of(context));

        handler.handleEvent(event);

        verify(discussionProcessor).processDeleted(event.discussion(), context);
        verify(discussionProcessor, never()).process(any(), any());
    }

    @Test
    void shouldSkipUnknownActionWithoutUpsert() throws Exception {
        GitHubDiscussionEventDTO event = event("frobnicated_new_action");
        when(contextFactory.forWebhookEvent(event)).thenReturn(Optional.of(context));

        handler.handleEvent(event);

        verify(discussionProcessor, never()).process(any(), any());
        verify(discussionProcessor, never()).processDeleted(any(), any());
    }

    @Test
    void shouldRouteCreatedToUpsert() throws Exception {
        GitHubDiscussionEventDTO event = event("created");
        when(contextFactory.forWebhookEvent(event)).thenReturn(Optional.of(context));

        handler.handleEvent(event);

        verify(discussionProcessor).process(event.discussion(), context);
        verify(discussionProcessor, never()).processDeleted(any(), any());
    }

    @Test
    void shouldNotTouchProcessorWhenScopeUnresolved() throws Exception {
        GitHubDiscussionEventDTO event = event("transferred");
        when(contextFactory.forWebhookEvent(event)).thenReturn(Optional.empty());

        handler.handleEvent(event);

        verifyNoInteractions(discussionProcessor);
    }

    private static GitHubDiscussionEventDTO event(String action) throws Exception {
        String json = "{\"action\":\""
                + action
                + "\",\"discussion\":{\"number\":7,\"database_id\":123},"
                + "\"repository\":{\"full_name\":\"acme/widgets\"}}";
        return JSON.readValue(json, GitHubDiscussionEventDTO.class);
    }
}
