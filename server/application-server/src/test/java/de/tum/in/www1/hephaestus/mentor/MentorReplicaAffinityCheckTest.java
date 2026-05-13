package de.tum.in.www1.hephaestus.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Asserts the multi-replica fail-fast guard. The constructor records properties but does NOT
 * fire — verification is in {@code @PostConstruct verifyTopology}; we invoke it directly via
 * reflection to keep the test free of Spring context bootstrapping (BaseUnitTest base).
 */
@DisplayName("MentorReplicaAffinityCheck")
class MentorReplicaAffinityCheckTest extends BaseUnitTest {

    @Test
    @DisplayName("Single replica: never throws")
    void singleReplicaPasses() throws Exception {
        MentorReplicaAffinityCheck check = new MentorReplicaAffinityCheck(1, false);
        verifyTopology(check); // does not throw
        assertThat(check.replicaCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Multi-replica WITHOUT sticky: fail-fast with #1077 reference")
    void multiReplicaNoStickyFailsFast() throws Exception {
        MentorReplicaAffinityCheck check = new MentorReplicaAffinityCheck(3, false);

        assertThatThrownBy(() -> verifyTopology(check))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("hephaestus.mentor.replica-count=3")
            .hasMessageContaining("replica-affinity.sticky=true")
            .hasMessageContaining("#1077");
    }

    @Test
    @DisplayName("Multi-replica WITH sticky: passes")
    void multiReplicaWithStickyPasses() throws Exception {
        MentorReplicaAffinityCheck check = new MentorReplicaAffinityCheck(3, true);
        verifyTopology(check);
        assertThat(check.stickyAffinity()).isTrue();
    }

    /** Reflectively invokes the package-private @PostConstruct hook so we can assert directly. */
    private static void verifyTopology(MentorReplicaAffinityCheck check) throws Exception {
        Method m = MentorReplicaAffinityCheck.class.getDeclaredMethod("verifyTopology");
        m.setAccessible(true);
        try {
            m.invoke(check);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}
