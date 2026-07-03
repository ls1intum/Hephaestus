package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

/**
 * Locks the S11 cooldown contract: a conversation-review idempotency key's cooldown prefix scopes on the thread
 * + subject ALONE, never on the freshness ({@code lastTs}). Two enqueues of the same thread/subject with
 * different {@code lastTs} (a late reply) therefore share a cooldown prefix and do not re-fire immediately —
 * only genuine growth past the watermark does.
 */
class ConversationCooldownKeyTest extends BaseUnitTest {

    @Test
    void cooldownPrefixIsThreadAndSubjectScopedNotFreshnessScoped() {
        String early = "conversation_review:C0ABC:1700000000.100000:42:1700000900.500000";
        String late = "conversation_review:C0ABC:1700000000.100000:42:1700009999.900000";

        String earlyPrefix = AgentJobService.extractCooldownKeyPrefix(early);
        String latePrefix = AgentJobService.extractCooldownKeyPrefix(late);

        assertThat(earlyPrefix).isEqualTo("conversation_review:C0ABC:1700000000.100000:42:");
        // A late reply (new lastTs) collapses onto the SAME cooldown prefix — it will be blocked by cooldown,
        // not treated as a fresh review.
        assertThat(latePrefix).isEqualTo(earlyPrefix);
    }
}
