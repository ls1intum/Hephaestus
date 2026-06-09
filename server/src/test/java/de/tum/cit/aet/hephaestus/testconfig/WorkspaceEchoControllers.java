package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shared echo controllers for the workspace-scoping + context-filter integration tests. Both
 * controllers are {@code @Import}-ed once by {@link BaseIntegrationTest} instead of each test
 * declaring its own — a per-class {@code @Import} gave each a distinct Spring context cache key
 * (a fresh Testcontainers boot). The routes are inert unless a test calls them.
 */
public final class WorkspaceEchoControllers {

    private WorkspaceEchoControllers() {}

    /** Echoes the resolved {@link WorkspaceContext} from a workspace-scoped controller. */
    public record ScopedEcho(String requestPath, String contextSlug, Long contextId, List<String> roles) {}

    /** Echoes the resolved {@link WorkspaceContext} from a plain controller under a workspace path. */
    public record WorkspaceContextSnapshot(String pathSlug, String contextSlug, Long contextId, List<String> roles) {}

    @WorkspaceScopedController
    @RequestMapping("/scoped-test")
    public static class ScopedEchoController {

        @GetMapping("/echo")
        ResponseEntity<ScopedEcho> echo(HttpServletRequest request, WorkspaceContext context) {
            ScopedEcho payload = new ScopedEcho(
                request.getRequestURI(),
                context != null ? context.slug() : null,
                context != null ? context.id() : null,
                context != null ? context.roles().stream().map(Enum::name).toList() : List.of()
            );
            return ResponseEntity.ok(payload);
        }
    }

    @RestController
    @RequestMapping("/workspaces/{workspaceSlug}/context-echo")
    public static class WorkspaceContextEchoController {

        @GetMapping
        ResponseEntity<WorkspaceContextSnapshot> echo(
            @PathVariable String workspaceSlug,
            WorkspaceContext workspaceContext
        ) {
            WorkspaceContextSnapshot snapshot;
            if (workspaceContext == null) {
                snapshot = new WorkspaceContextSnapshot(workspaceSlug, null, null, List.of());
            } else {
                snapshot = new WorkspaceContextSnapshot(
                    workspaceSlug,
                    workspaceContext.slug(),
                    workspaceContext.id(),
                    workspaceContext.roles().stream().map(Enum::name).toList()
                );
            }
            return ResponseEntity.ok(snapshot);
        }
    }
}
