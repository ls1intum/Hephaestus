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
 * Fixtures for {@link AuditByDefaultArchTest}'s endpoint detection, which can fail silently — an
 * endpoint the rule cannot see looks exactly like a compliant one. The class-gated case is the one
 * that matters: seven controllers declare the gate once at class level rather than per method.
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
        assertThat(isAdminMutation(ClassGatedController.class, "create")).isTrue();
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
