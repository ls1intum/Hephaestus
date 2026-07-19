package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fixtures for {@link AuditByDefaultArchTest}'s endpoint detection.
 *
 * <p>The rule is only worth its cost while it sees every admin mutation, and it can fail to see one
 * silently — a missed endpoint looks exactly like a compliant one. It has already happened twice:
 * once when the controller check ignored meta-annotations ({@code @WorkspaceScopedController} composes
 * {@code @RestController}), and once when the gate check looked at the method but not its class, which
 * exempted ten endpoints across seven class-gated controllers while the build stayed green.
 */
@Tag("architecture")
class AuditByDefaultGateDetectionTest {

    @RestController
    @RequireAtLeastWorkspaceAdmin
    static class ClassGatedController {

        @PostMapping
        public void create() {}
    }

    @RestController
    static class MethodGatedController {

        @RequireAtLeastWorkspaceAdmin
        @PatchMapping
        public void update() {}
    }

    @RestController
    @PreAuthorize("hasAuthority('app_admin')")
    static class InstanceAdminGatedController {

        @PostMapping
        public void create() {}
    }

    @RestController
    static class UngatedController {

        @PostMapping
        public void create() {}
    }

    @RestController
    @RequireAtLeastWorkspaceAdmin
    static class ReadOnlyController {

        public void list() {}
    }

    private static final JavaClasses FIXTURES = new ClassFileImporter().importClasses(
        ClassGatedController.class,
        MethodGatedController.class,
        InstanceAdminGatedController.class,
        UngatedController.class,
        ReadOnlyController.class
    );

    @Test
    void seesAGateDeclaredOnTheControllerRatherThanTheMethod() {
        assertThat(isAdminMutation(ClassGatedController.class, "create"))
            .as("seven real controllers declare the gate once at class level")
            .isTrue();
    }

    @Test
    void seesAGateDeclaredOnTheMethod() {
        assertThat(isAdminMutation(MethodGatedController.class, "update")).isTrue();
    }

    @Test
    void seesTheInstanceAdminAuthorityGate() {
        assertThat(isAdminMutation(InstanceAdminGatedController.class, "create")).isTrue();
    }

    @Test
    void ignoresAnUngatedEndpoint() {
        assertThat(isAdminMutation(UngatedController.class, "create"))
            .as("a public endpoint is not an admin action and must not be forced to declare one")
            .isFalse();
    }

    @Test
    void ignoresAReadUnderAnAdminGate() {
        assertThat(isAdminMutation(ReadOnlyController.class, "list"))
            .as("reading changes nothing, so there is nothing to record")
            .isFalse();
    }

    private static boolean isAdminMutation(Class<?> controller, String method) {
        var javaClass = FIXTURES.get(controller);
        if (!AuditByDefaultArchTest.isController(javaClass)) {
            return false;
        }
        var javaMethod = javaClass.getMethod(method);
        return AuditByDefaultArchTest.isMutation(javaMethod) && AuditByDefaultArchTest.isAdminGated(javaMethod);
    }
}
