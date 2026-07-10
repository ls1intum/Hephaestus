package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slack.api.model.assistant.SuggestedPrompt;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;

class SlackAssistantEventHandlerTest extends BaseUnitTest {

    @Mock
    private SlackWorkspaceResolver workspaceResolver;

    @Mock
    private SlackMessageService messageService;

    private SlackAssistantEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SlackAssistantEventHandler(workspaceResolver, messageService);
    }

    @Test
    void messagesTabSetsContextSafeSuggestedPrompts() throws Exception {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(42L));

        handler.onMessagesOpened(
            "T1",
            JsonMapper.builder().build().readTree("{\"tab\":\"messages\",\"channel\":\"D1\"}")
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SuggestedPrompt>> prompts = ArgumentCaptor.forClass(List.class);
        verify(messageService).setSuggestedPrompts(
            ArgumentMatchers.eq(42L),
            ArgumentMatchers.eq("D1"),
            ArgumentMatchers.eq("Practice mentor"),
            prompts.capture()
        );
        assertThat(prompts.getValue()).hasSize(4);
        assertThat(prompts.getValue())
            .extracting(SuggestedPrompt::getTitle)
            .containsExactly("What needs attention?", "Review my recent work", "Check my reviews", "Follow up");
        assertThat(prompts.getValue())
            .extracting(SuggestedPrompt::getMessage)
            .allSatisfy(message -> assertThat(message).doesNotContain("latest", "most recent"));
    }

    @Test
    void missingChannelDoesNotSetPrompts() throws Exception {
        handler.onMessagesOpened("T1", JsonMapper.builder().build().readTree("{\"tab\":\"messages\"}"));

        verify(messageService, never()).setSuggestedPrompts(
            ArgumentMatchers.anyLong(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyList()
        );
    }
}
