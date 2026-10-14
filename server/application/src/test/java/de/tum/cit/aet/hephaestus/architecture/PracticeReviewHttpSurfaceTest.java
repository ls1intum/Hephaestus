package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class PracticeReviewHttpSurfaceTest extends HephaestusArchitectureTest {
    @Test
    void shouldExposeOnlyFrozenReviewMutationsToAdmins() {
        var controllers = classes.stream()
                .filter(type -> type.isAnnotatedWith(RequestMapping.class))
                .filter(type -> Arrays.stream(type.getAnnotationOfType(RequestMapping.class).value())
                        .anyMatch(path -> path.startsWith("/practices/reviews")))
                .toList();
        assertThat(controllers).isNotEmpty();
        assertThat(controllers).allMatch(type -> type.isAnnotatedWith(RequireAtLeastWorkspaceAdmin.class));
        Set<String> creates = Set.of("createResultMark", "createFeedbackDecision", "retryFeedback");
        var unsupported = controllers.stream().flatMap(type -> type.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(RequestMapping.class)
                        || method.isMetaAnnotatedWith(RequestMapping.class))
                .filter(method -> {
                    if (method.isAnnotatedWith(GetMapping.class)) return false;
                    if (!method.isAnnotatedWith(Operation.class)) return true;
                    String operation = method.getAnnotationOfType(Operation.class).operationId();
                    return !(method.isAnnotatedWith(PostMapping.class) && creates.contains(operation))
                            && !(method.isAnnotatedWith(DeleteMapping.class) && operation.equals("removeResultMark"));
                })
                .map(method -> method.getFullName()).sorted().toList();
        assertThat(unsupported).isEmpty();
    }
}
