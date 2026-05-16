package de.tum.in.www1.hephaestus.agent.mentor.chat.health;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@DisplayName("MentorReplicaAffinityCheck")
class MentorReplicaAffinityCheckTest extends BaseUnitTest {

    @Test
    @DisplayName("single replica → UP (no affinity hazard)")
    void singleReplicaIsUp() {
        Health h = new MentorReplicaAffinityCheck("pod-abc", 1, false).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails())
            .containsEntry("hostname", "pod-abc")
            .containsEntry("expectedReplicas", 1)
            .containsEntry("stickyRoutingConfirmed", false);
    }

    @Test
    @DisplayName("multi-replica without sticky-routing-confirmed → DOWN with diagnostic reason")
    void multiReplicaUnconfirmedIsDown() {
        Health h = new MentorReplicaAffinityCheck("pod-xyz", 3, false).health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails())
            .containsEntry("reason", "multi-replica without operator-asserted sticky routing")
            .containsEntry("expectedReplicas", 3)
            .containsEntry("stickyRoutingConfirmed", false)
            .containsEntry("hostname", "pod-xyz");
    }

    @Test
    @DisplayName("multi-replica with sticky-routing-confirmed → UP")
    void multiReplicaConfirmedIsUp() {
        Health h = new MentorReplicaAffinityCheck("pod-7", 4, true).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails())
            .containsEntry("hostname", "pod-7")
            .containsEntry("expectedReplicas", 4)
            .containsEntry("stickyRoutingConfirmed", true);
    }

    @Test
    @DisplayName("blank HOSTNAME degrades to \"unknown\" detail without flipping status")
    void blankHostnameDegradesToUnknown() {
        Health h = new MentorReplicaAffinityCheck("", 1, false).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsEntry("hostname", "unknown");
    }
}
