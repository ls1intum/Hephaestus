package de.tum.cit.aet.hephaestus.integration.scm.github.discussion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.ProcessingContextFactory;
import de.tum.cit.aet.hephaestus.integration.scm.github.discussion.dto.GitHubDiscussionEventDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Action-level routing guard for {@link GitHubDiscussionMessageHandler}. The dispatcher round-trip test
 * proves discussion events reach this handler; this proves the handler routes each action to the right
 * processor method. Two regressions are locked down here:
 *
 * <ul>
 *   <li><b>transferred → removal, not upsert.</b> A transfer moves the discussion out of the source
 *       repository. The old code shared the upsert branch, re-creating the very phantom the transfer
 *       should retire — permanently, since discussions have no reconciliation sweep.</li>
 *   <li><b>unknown action → skip, not upsert.</b> The old {@code default} branch fell through to an
 *       upsert, so a future action meaning "removed" would silently re-create a phantom.</li>
 * </ul>
 */
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
            contextFactory,
            discussionProcessor,
            syncSchedulerProperties,
            deserializer,
            new TransactionTemplate()
        );
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
        String json =
            "{\"action\":\"" +
            action +
            "\",\"discussion\":{\"number\":7,\"database_id\":123}," +
            "\"repository\":{\"full_name\":\"acme/widgets\"}}";
        return JSON.readValue(json, GitHubDiscussionEventDTO.class);
    }
}
