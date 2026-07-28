package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class PracticeReviewReadSurfaceTest extends HephaestusArchitectureTest {

    private static final Set<String> CONTROLLERS = Set.of(
        "PracticeReviewOutputController",
        "PracticeReviewSummaryController"
    );

    @Test
    void practiceReviewSurfaceIsReadOnly() {
        var controllers = classes
            .stream()
            .filter(type -> CONTROLLERS.contains(type.getSimpleName()))
            .toList();
        assertThat(controllers)
            .extracting(type -> type.getSimpleName())
            .containsExactlyInAnyOrderElementsOf(CONTROLLERS);

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
