package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code MultiTenancyArchitectureTest} lets an endpoint skip the WorkspaceContext requirement when it
 * is gated by the instance-admin authority — cross-workspace is the whole point of an instance admin.
 * That exemption is only sound while it means <em>exactly</em> that gate.
 *
 * <p>The tempting loosening is a substring test on the SpEL. It is wrong:
 * {@code hasAnyAuthority('app_admin','workspace_member')} contains "app_admin" yet is reachable by a
 * workspace member, so a substring match would silently drop tenancy scrutiny from a member-facing
 * data endpoint. These fixtures fail the build if anyone widens it back.
 */
@Tag("architecture")
class InstanceAdminGateExemptionTest {

    @RestController
    @PreAuthorize("hasAuthority('app_admin')")
    static class InstanceAdminOnly {}

    @RestController
    @PreAuthorize("hasAnyAuthority('app_admin','workspace_member')")
    static class AppAdminOrMember {}

    @RestController
    @PreAuthorize("hasAuthority('app_admin') or hasAuthority('workspace_owner')")
    static class AppAdminOrOwner {}

    @RestController
    @PreAuthorize("!hasAuthority('app_admin')")
    static class NotAppAdmin {}

    @RestController
    static class Ungated {}

    @Test
    void exemptsOnlyTheExactInstanceAdminGate() {
        assertThat(isExempt(InstanceAdminOnly.class))
            .as("the exact app_admin gate is cross-workspace by design")
            .isTrue();
    }

    @Test
    void doesNotExemptCompositeExpressionsThatMerelyMentionAppAdmin() {
        assertThat(isExempt(AppAdminOrMember.class)).as("reachable by a workspace member").isFalse();
        assertThat(isExempt(AppAdminOrOwner.class)).as("reachable by a workspace owner").isFalse();
        assertThat(isExempt(NotAppAdmin.class)).as("negated gate excludes instance admins entirely").isFalse();
    }

    @Test
    void doesNotExemptUngatedControllers() {
        assertThat(isExempt(Ungated.class)).isFalse();
    }

    private static boolean isExempt(Class<?> type) {
        JavaClass javaClass = new ClassFileImporter().importClasses(type).get(type);
        return MultiTenancyArchitectureTest.isInstanceAdminGated(javaClass);
    }
}
