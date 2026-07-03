package de.tum.cit.aet.hephaestus.integration.slack.events;

import com.slack.api.model.assistant.SuggestedPrompt;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Handles {@code assistant_thread_started}: when a member opens the mentor's assistant DM, seed the thread
 * with up to four suggested prompts so the first turn is a click, not a blank box.
 *
 * <p>The prompts are framed around the member's own practice history (needs-attention first), which is the mentor's
 * job to surface once the turn runs. Selecting the concrete needs-attention practices per developer from this
 * pre-turn, security-context-free path is a live-only refinement; the deterministic part is the routing +
 * prompt assembly + the {@code setSuggestedPrompts} seam. Best-effort — a Slack failure is swallowed.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackAssistantEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackAssistantEventHandler.class);

    private static final String PROMPT_TITLE = "How can I help with your practices?";

    /** ≤4 needs-attention-framed openers. The mentor turn resolves them against the member's real history. */
    private static final List<SuggestedPrompt> PROMPTS = List.of(
        new SuggestedPrompt(
            "Which practices need my attention?",
            "Which of my software practices need the most attention right now?"
        ),
        new SuggestedPrompt("How am I doing recently?", "How am I doing on my recent pull requests and reviews?"),
        new SuggestedPrompt("Feedback on my latest work", "Give me feedback on my most recent pull request."),
        new SuggestedPrompt("What should I improve next?", "What should I focus on improving next?")
    );

    private final SlackWorkspaceResolver workspaceResolver;
    private final SlackMessageService messageService;

    public SlackAssistantEventHandler(SlackWorkspaceResolver workspaceResolver, SlackMessageService messageService) {
        this.workspaceResolver = workspaceResolver;
        this.messageService = messageService;
    }

    /**
     * Set the suggested prompts for a freshly opened assistant thread.
     *
     * @param teamId the Slack {@code T…} workspace id from the verified envelope
     * @param event  the {@code assistant_thread_started} event node (carries {@code assistant_thread})
     */
    public void onThreadStarted(String teamId, JsonNode event) {
        JsonNode thread = event.path("assistant_thread");
        String channelId = thread.path("channel_id").asString("");
        String threadTs = thread.path("thread_ts").asString("");
        if (channelId.isEmpty() || threadTs.isEmpty()) {
            return;
        }
        Optional<Long> workspaceId = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceId.isEmpty()) {
            log.debug("slack.assistant: assistant_thread_started for team={} with no active connection", teamId);
            return;
        }
        messageService.setSuggestedPrompts(workspaceId.get(), channelId, threadTs, PROMPT_TITLE, PROMPTS);
    }
}
