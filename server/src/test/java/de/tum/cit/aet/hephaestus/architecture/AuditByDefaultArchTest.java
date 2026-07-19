package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.Audited;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Every admin mutation endpoint must declare whether it is audited: {@link Audited} names the ledger
 * the change lands in, {@link AuditExempt} says why it deliberately lands in none. An endpoint that
 * declares neither is how a trail develops a hole nobody chose. The reasoning that matters is in the
 * failure message below, where a reader actually meets it.
 */
class AuditByDefaultArchTest extends HephaestusArchitectureTest {

    private static final String INSTANCE_ADMIN_AUTHORITY = "app_admin";

    @Test
    void everyAdminMutationEndpointDeclaresItsAuditStatus() {
        List<String> undeclared = classes
            .stream()
            .filter(AuditByDefaultArchTest::isController)
            .flatMap(c -> c.getMethods().stream())
            .filter(AuditByDefaultArchTest::isMutation)
            .filter(AuditByDefaultArchTest::isAdminGated)
            .filter(m -> !m.isAnnotatedWith(Audited.class) && !m.isAnnotatedWith(AuditExempt.class))
            .map(m -> m.getOwner().getSimpleName() + "." + m.getName())
            .sorted()
            .toList();

        assertThat(undeclared)
            .as(
                """
                These admin mutation endpoints declare neither @Audited nor @AuditExempt. Decide: if the \
                action changes configuration or access, record it on the audit trail and mark it \
                @Audited("<entity type or ledger>"); if it genuinely should not be recorded, mark it \
                @AuditExempt(reason="…"). An undeclared admin action is how an audit trail silently \
                develops holes."""
            )
            .isEmpty();
    }

    static boolean isController(JavaClass clazz) {
        // Meta-annotated too: @WorkspaceScopedController composes @RestController, and every
        // workspace-admin surface uses it.
        return (
            clazz.isAnnotatedWith(org.springframework.web.bind.annotation.RestController.class) ||
            clazz.isMetaAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
        );
    }

    static boolean isMutation(JavaMethod method) {
        return (
            method.isAnnotatedWith(PostMapping.class) ||
            method.isAnnotatedWith(PutMapping.class) ||
            method.isAnnotatedWith(PatchMapping.class) ||
            method.isAnnotatedWith(DeleteMapping.class)
        );
    }

    /**
     * Admin-gated = a workspace admin/owner gate or the instance-admin authority, on the method OR its
     * controller. Checking only the method misses the seven controllers that declare the gate once at
     * class level, which silently exempted ten endpoints.
     */
    static boolean isAdminGated(JavaMethod method) {
        return (
            hasWorkspaceAdminGate(method) ||
            hasWorkspaceAdminGate(method.getOwner()) ||
            isInstanceAdminGated(method) ||
            isInstanceAdminGated(method.getOwner())
        );
    }

    private static boolean hasWorkspaceAdminGate(
        com.tngtech.archunit.core.domain.properties.HasAnnotations<?> element
    ) {
        return element
            .getAnnotations()
            .stream()
            .map(a -> a.getRawType().getSimpleName())
            .anyMatch(n -> n.equals("RequireAtLeastWorkspaceAdmin") || n.equals("RequireWorkspaceOwner"));
    }

    private static boolean isInstanceAdminGated(com.tngtech.archunit.core.domain.properties.HasAnnotations<?> element) {
        return element
            .tryGetAnnotationOfType(PreAuthorize.class)
            .map(a -> a.value().contains(INSTANCE_ADMIN_AUTHORITY))
            .orElse(false);
    }
}
