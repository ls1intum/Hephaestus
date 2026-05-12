package de.tum.in.www1.hephaestus.agent.task;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardrail for the sealed {@link Task} hierarchy. Forces a deliberate diff when a new permitted
 * subtype is added — the test fails until the maintainer expands the expected set, which prompts
 * updating every sealed switch over {@code Task}.
 *
 * <p>Reflection-based: Java 21 sealed-switch bytecode is {@code invokedynamic} against
 * {@code SwitchBootstraps.typeSwitch} and is opaque to ArchUnit 1.3.x. {@code javac} already
 * enforces exhaustiveness — this test is a structural guardrail, not a dispatch-site inspector.
 */
@DisplayName("Task sealed hierarchy")
class TaskSealedHierarchyTest extends BaseUnitTest {

    @Test
    @DisplayName("Task is sealed and permits exactly {Task.PracticeReview}")
    void permittedSubtypes() {
        assertThat(Task.class.isSealed()).isTrue();
        Set<Class<?>> permitted = Set.of(Task.class.getPermittedSubclasses());
        assertThat(permitted).containsExactlyInAnyOrder(Task.PracticeReview.class);
    }
}
