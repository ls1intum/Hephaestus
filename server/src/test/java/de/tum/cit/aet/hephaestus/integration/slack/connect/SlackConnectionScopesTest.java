package de.tum.cit.aet.hephaestus.integration.slack.connect;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

/**
 * Guards the least-privilege OAuth scope set for the live Slack subsystem (DM mentor + assistant + App Home).
 *
 * <p>Two invariants that must not silently drift:
 * <ul>
 *   <li>the DM/assistant scopes the shipped code actually calls are present ({@code im:history} for DM event
 *       delivery, {@code assistant:write} for {@code assistant.threads.setStatus}/{@code setSuggestedPrompts},
 *       {@code chat:write} for the streamed reply);</li>
 *   <li>the channel-ingestion scopes stay OUT — {@code channels:history}/{@code groups:history} belong to the
 *       parked channel-ingest subsystem and must not be requested until channel activation is a deliberate,
 *       consent-designed decision.</li>
 * </ul>
 */
class SlackConnectionScopesTest extends BaseUnitTest {

    @Test
    void requestsExactlyTheLiveSubsystemScopes() {
        assertThat(SlackConnectionStrategy.DEFAULT_SCOPES).containsExactlyInAnyOrder(
            "chat:write",
            "chat:write.public",
            "assistant:write",
            "im:history",
            "team:read",
            "users:read",
            "users:read.email"
        );
    }

    @Test
    void includesTheDmAndAssistantScopesTheLiveCodeCalls() {
        assertThat(SlackConnectionStrategy.DEFAULT_SCOPES).contains("im:history", "assistant:write", "chat:write");
    }

    @Test
    void doesNotRequestChannelIngestionScopesUntilChannelActivationIsADeliberateDecision() {
        assertThat(SlackConnectionStrategy.DEFAULT_SCOPES).doesNotContain("channels:history", "groups:history");
    }
}
