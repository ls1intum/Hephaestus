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
 * Handles Slack's agent Messages tab lifecycle: when a member opens the mentor DM, seed the top of the
 * Messages tab with suggested prompts so the first turn is a click, not a blank box.
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

    private static final String PROMPT_TITLE = "Practice mentor";

    /** Context-safe openers: no stale "latest PR" claims before the mentor has loaded evidence. */
    static final List<SuggestedPrompt> PROMPTS = List.of(
        new SuggestedPrompt("What needs attention?", "What software project practice should I focus on next?"),
        new SuggestedPrompt("Review my recent work", "Review my recent pull requests, reviews, and issues."),
        new SuggestedPrompt("Check my reviews", "How are my code reviews and review comments trending?"),
        new SuggestedPrompt("Follow up", "What feedback or project-practice issue should I revisit?")
    );

    private final SlackWorkspaceResolver workspaceResolver;
    private final SlackMessageService messageService;

    public SlackAssistantEventHandler(SlackWorkspaceResolver workspaceResolver, SlackMessageService messageService) {
        this.workspaceResolver = workspaceResolver;
        this.messageService = messageService;
    }

    /**
     * Set the suggested prompts for the agent Messages tab.
     *
     * @param teamId the Slack {@code T…} workspace id from the verified envelope
     * @param event  the {@code app_home_opened} event node with {@code tab=messages}
     */
    public void onMessagesOpened(String teamId, JsonNode event) {
        String channelId = event.path("channel").asString("");
        if (channelId.isEmpty()) {
            return;
        }
        Optional<Long> workspaceId = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceId.isEmpty()) {
            log.debug("slack.agent: messages tab opened for team={} with no active connection", teamId);
            return;
        }
        messageService.setSuggestedPrompts(workspaceId.get(), channelId, PROMPT_TITLE, PROMPTS);
    }
}
