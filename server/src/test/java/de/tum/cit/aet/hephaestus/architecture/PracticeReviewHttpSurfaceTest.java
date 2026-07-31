package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class PracticeReviewHttpSurfaceTest extends HephaestusArchitectureTest {

    @Test
    void practiceReviewHttpSurfaceIsAdminGatedAndGetOnly() {
        var controllers = classes
            .stream()
            .filter(type -> type.isAnnotatedWith(RequestMapping.class))
            .filter(type ->
                java.util.Arrays.asList(type.getAnnotationOfType(RequestMapping.class).value()).contains(
                    "/practices/reviews"
                )
            )
            .toList();
        assertThat(controllers).isNotEmpty();
        assertThat(controllers).allMatch(type -> type.isAnnotatedWith(RequireAtLeastWorkspaceAdmin.class));

        var nonGetHandlers = controllers
            .stream()
            .flatMap(type -> type.getMethods().stream())
            .filter(
                method ->
                    method.isAnnotatedWith(RequestMapping.class) || method.isMetaAnnotatedWith(RequestMapping.class)
            )
            .filter(method -> !method.isAnnotatedWith(GetMapping.class))
            .map(method -> method.getFullName())
            .sorted()
            .toList();

        assertThat(nonGetHandlers).isEmpty();
    }
}
