package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.mentor.ThreadSurface;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MentorTurnPromptFactory} in isolation from the sandbox pipeline (that
 * end-to-end wiring — the directive actually reaching the runner's prompt call — is covered by
 * {@code MentorChatServiceTest#runTurn_slackPromptTellsMentorToInspectRecentAuthoredWork},
 * {@code #runTurn_slackPromptIncludesVisibleThreadHistory}, and
 * {@code #runTurn_webPromptIsVerbatimUserMessage_noSurfaceDirective}).
 */
class MentorTurnPromptFactoryTest extends BaseUnitTest {

    private MentorTurnRequest request(String message, ThreadSurface surface) {
        return new MentorTurnRequest(1L, UUID.randomUUID(), message, null, surface);
    }

    @Test
    void web_returnsUserMessageVerbatim_noDirective() {
        String prompt = MentorTurnPromptFactory.forRunner(request("hello mentor", ThreadSurface.WEB), Map.of());
        assertThat(prompt).isEqualTo("hello mentor");
    }

    @Test
    void slackDm_wrapsMessageInStyleDirective() {
        String prompt = MentorTurnPromptFactory.forRunner(request("what's up", ThreadSurface.SLACK_DM), Map.of());
        assertThat(prompt)
            .contains("[Surface: Slack DM")
            .contains("what's up")
            .contains("Visible recent mentor-thread history");
    }

    @Test
    void slackDm_defaultsThreadHistoryToEmptyObject_whenContextMissing() {
        String prompt = MentorTurnPromptFactory.forRunner(request("hi", ThreadSurface.SLACK_DM), Map.of());
        assertThat(prompt).endsWith("{}");
    }

    @Test
    void slackDm_includesVisibleThreadHistoryFromContext() {
        Map<String, byte[]> context = Map.of(
            "inputs/context/current_thread_history.json",
            "{\"messages\":[{\"role\":\"USER\",\"text\":\"hi\"}]}".getBytes(StandardCharsets.UTF_8)
        );
        String prompt = MentorTurnPromptFactory.forRunner(request("go on", ThreadSurface.SLACK_DM), context);
        assertThat(prompt).contains("{\"messages\":[{\"role\":\"USER\",\"text\":\"hi\"}]}");
    }

    @Test
    void slackDm_doesNotReSubstitutePlaceholderLookingTextInsideTheUserMessage() {
        // The user's own message can legitimately contain a "{{THREAD_HISTORY}}"-shaped substring
        // (e.g. quoting a bug report). Prefix/middle/suffix concatenation must not re-scan it.
        String tricky = "can you explain what {{THREAD_HISTORY}} means in your code?";
        String prompt = MentorTurnPromptFactory.forRunner(request(tricky, ThreadSurface.SLACK_DM), Map.of());
        assertThat(prompt).contains(tricky);
    }
}
