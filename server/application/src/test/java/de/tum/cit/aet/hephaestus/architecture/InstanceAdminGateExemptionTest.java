package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code MultiTenancyArchitectureTest} lets an endpoint skip the WorkspaceContext requirement when it
 * is gated by the instance-admin authority — cross-workspace is the whole point of an instance admin.
 * That exemption is only sound while it means <em>exactly</em> that gate.
 *
 * <p>Each fixture below is a SpEL expression a looser match would wrongly exempt — most of all a
 * substring test, under which a composite reachable by a workspace member still mentions "app_admin"
 * and so loses its tenancy scrutiny.
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

    static Stream<Arguments> gates() {
        return Stream.of(
            Arguments.of(InstanceAdminOnly.class, true, "the exact app_admin gate is cross-workspace by design"),
            Arguments.of(AppAdminOrMember.class, false, "reachable by a workspace member"),
            Arguments.of(AppAdminOrOwner.class, false, "reachable by a workspace owner"),
            Arguments.of(NotAppAdmin.class, false, "negated gate excludes instance admins entirely"),
            Arguments.of(Ungated.class, false, "no gate at all")
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("gates")
    void exemptsOnlyTheExactInstanceAdminGate(Class<?> type, boolean expected, String why) {
        JavaClass javaClass = new ClassFileImporter().importClasses(type).get(type);
        assertThat(MultiTenancyArchitectureTest.isInstanceAdminGated(javaClass)).as(why).isEqualTo(expected);
    }
}
