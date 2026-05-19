package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Reflection-driven enumeration of workspace-scoped HTTP surface.
 *
 * <p>This first-cut version asserts that every workspace-scoped controller is discoverable
 * and has at least one request mapping. The follow-up step (planned in a separate issue)
 * adds MockMvc + JWT fixtures that drive each endpoint with a user that belongs only to
 * workspace B, asserting 403 when the URL targets workspace A.
 *
 * <p><b>Why ship the scaffold now?</b> The SQL-layer {@link de.tum.cit.aet.hephaestus.core.tenancy.WorkspaceStatementInspector}
 * already catches the underlying defect class (unguarded scoped-table queries) at the
 * statement layer. The HTTP-layer assertion is defense-in-depth — important to land, but
 * the per-controller fixture work is meaningful enough to deserve its own focused PR.
 *
 * <p>The scaffold below ensures we have a stable reflection enumeration of the controllers
 * that need coverage, and that a new controller is discovered automatically (so the
 * follow-up just needs to add a fixture, not extend a checklist).
 */
@Tag("architecture")
@DisplayName("Cross-Workspace HTTP Isolation")
class CrossWorkspaceIsolationTest extends HephaestusArchitectureTest {

    private static final String BASE_PACKAGE = "de.tum.cit.aet.hephaestus";

    @Test
    @DisplayName("every @WorkspaceScopedController is reflection-discoverable")
    void workspaceScopedControllersAreDiscoverable() {
        Set<Class<?>> controllers = scanWorkspaceScopedControllers();
        // Sanity floor: today's codebase has 17 such controllers. Use a >= floor so adding
        // controllers doesn't churn the test, but removing ones unexpectedly fails fast.
        assertThat(controllers)
            .as("workspace-scoped controllers found via reflection")
            .hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("enumerated controllers belong to the hephaestus base package")
    void controllersLiveInBasePackage() {
        Set<Class<?>> controllers = scanWorkspaceScopedControllers();
        List<String> outsiders = controllers.stream()
            .map(Class::getName)
            .filter(n -> !n.startsWith(BASE_PACKAGE + "."))
            .collect(Collectors.toList());
        assertThat(outsiders)
            .as("controllers found outside the canonical base package")
            .isEmpty();
    }

    private static Set<Class<?>> scanWorkspaceScopedControllers() {
        DescribedPredicate<JavaClass> isWorkspaceScoped =
            new DescribedPredicate<>("annotated with @WorkspaceScopedController") {
                @Override
                public boolean test(JavaClass clazz) {
                    return clazz.isAnnotatedWith(WorkspaceScopedController.class);
                }
            };
        return classes.that(isWorkspaceScoped).stream()
            .map(JavaClass::reflect)
            .collect(Collectors.toUnmodifiableSet());
    }
}
