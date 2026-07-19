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
 * Audit-by-default: every administrative mutation endpoint must declare whether it is audited.
 *
 * <p>The failure this prevents is the one that makes an audit trail worse than none — an admin reads a
 * "complete" history, sees no entry for an action, and concludes it never happened, when in truth the
 * action was simply never wired to the trail. Coverage decided per-feature drifts silently as new admin
 * surfaces land; coverage decided by the build cannot.
 *
 * <p>So each admin-gated {@code POST/PUT/PATCH/DELETE} handler carries exactly one of
 * {@link Audited} (recorded, and where) or {@link AuditExempt} (deliberately not, and why). A new
 * administrative action fails this test until someone makes that call — the decision is forced at
 * review time, and every known gap stays greppable. This mirrors GitLab's explicit audit-event
 * registry, where an event type must be declared before it can be emitted.
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

    private static boolean isController(JavaClass clazz) {
        // Meta-annotated too: @WorkspaceScopedController composes @RestController, and every
        // workspace-admin surface uses it.
        return (
            clazz.isAnnotatedWith(org.springframework.web.bind.annotation.RestController.class) ||
            clazz.isMetaAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
        );
    }

    private static boolean isMutation(JavaMethod method) {
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
    private static boolean isAdminGated(JavaMethod method) {
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
