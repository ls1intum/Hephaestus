package de.tum.in.www1.hephaestus.workspace.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceSlugHistoryRepository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Servlet filter that extracts workspace context from request path and populates ThreadLocal.
 * Only processes requests matching /workspaces/{slug} pattern.
 * Returns 404 for missing or non-ACTIVE workspaces.
 * Filter order is set to -5 to ensure it runs after Spring Security filters (typically -10)
 * but before controller execution.
 */
@Component
@Order(-5)
public class WorkspaceContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceContextFilter.class);

    private static final Pattern WORKSPACE_PATH_PATTERN = Pattern.compile(
        "^/workspaces/([a-z0-9][a-z0-9-]{2,50})(/.*)?$"
    );

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final UserRepository userRepository;
    private final WorkspaceSlugHistoryRepository slugHistoryRepository;
    private final ObjectMapper objectMapper;

    public WorkspaceContextFilter(
        WorkspaceRepository workspaceRepository,
        WorkspaceMembershipRepository workspaceMembershipRepository,
        UserRepository userRepository,
        WorkspaceSlugHistoryRepository slugHistoryRepository,
        ObjectMapper objectMapper
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.userRepository = userRepository;
        this.slugHistoryRepository = slugHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (
            !(request instanceof HttpServletRequest httpRequest) ||
            !(response instanceof HttpServletResponse httpResponse)
        ) {
            chain.doFilter(request, response);
            return;
        }

        String path = httpRequest.getRequestURI();

        // Skip legacy workspace endpoints that don't use workspace context
        // These endpoints are at /workspace/** (singular) instead of /workspaces/{slug}/**
        if (path.startsWith("/workspace/")) {
            chain.doFilter(request, response);
            return;
        }

        var matcher = WORKSPACE_PATH_PATTERN.matcher(path);

        if (!matcher.matches()) {
            // Not a workspace-scoped path, continue without context
            chain.doFilter(request, response);
            return;
        }

        String slug = matcher.group(1);

        try {
            // Look up workspace by slug
            var workspaceOpt = workspaceRepository.findBySlug(slug);

            if (workspaceOpt.isEmpty()) {
                // Slug not found in active workspaces - check redirect history
                handleSlugRedirect(httpResponse, slug);
                return;
            }

            var workspace = workspaceOpt.get();

            // Check workspace status - only ACTIVE workspaces are accessible
            if (workspace.getStatus() != WorkspaceStatus.ACTIVE) {
                log.debug("Workspace {} has non-ACTIVE status: {}. Returning 404.", slug, workspace.getStatus());
                sendWorkspaceNotFoundError(httpResponse, slug);
                return;
            }

            // Fetch user roles
            Set<WorkspaceRole> roles = fetchUserRoles(workspace.getId());

            // Create and set context
            WorkspaceContext context = WorkspaceContext.fromWorkspace(workspace, roles);

            // Overwrite detection: warn if context already set
            if (WorkspaceContextHolder.getContext() != null) {
                log.warn(
                    "Context already set when entering filter for slug={}. This may indicate a filter ordering issue or context leak.",
                    slug
                );
            }

            WorkspaceContextHolder.setContext(context);

            log.debug("Workspace context set: slug={}, id={}, roles={}", context.slug(), context.id(), context.roles());

            // Continue filter chain
            chain.doFilter(request, response);
        } finally {
            // Always clear context to prevent leaks
            WorkspaceContextHolder.clearContext();
        }
    }

    /**
     * Fetch workspace roles for the current authenticated user.
     *
     * @param workspaceId Workspace ID
     * @return Set of workspace roles (empty if user has no membership or not authenticated)
     */
    private Set<WorkspaceRole> fetchUserRoles(Long workspaceId) {
        try {
            var userOpt = userRepository.getCurrentUser();
            if (userOpt.isEmpty()) {
                log.debug("No authenticated user found, returning empty roles");
                return Set.of();
            }

            var membershipOpt = workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(
                workspaceId,
                userOpt.get().getId()
            );

            if (membershipOpt.isPresent()) {
                log.debug("User has role: {}", membershipOpt.get().getRole());
                return Set.of(membershipOpt.get().getRole());
            }

            log.debug("User has no membership in workspace {}", workspaceId);
            return Set.of();
        } catch (Exception e) {
            log.warn("Failed to fetch user roles for workspace {}: {}", workspaceId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Send a 404 JSON error response for workspace not found.
     *
     * @param response HTTP response
     * @param slug Workspace slug
     */
    private void sendWorkspaceNotFoundError(HttpServletResponse response, String slug) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        var errorBody = Map.of("error", "WORKSPACE_NOT_FOUND", "message", "Workspace not found: " + slug);

        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    }

    /**
     * Handle slug redirect lookup. Checks history for redirects.
     * Returns 301 with new slug location for redirects, or 404 if no redirect found.
     *
     * @param response HTTP response
     * @param oldSlug The slug that was not found in active workspaces
     */
    private void handleSlugRedirect(HttpServletResponse response, String oldSlug) throws IOException {
        var historyOpt = slugHistoryRepository.findFirstByOldSlugOrderByChangedAtDesc(oldSlug);

        if (historyOpt.isPresent()) {
            // Redirect found - return 301 with new slug reference
            var history = historyOpt.get();
            String newSlug = history.getNewSlug();

            log.info("Workspace slug redirect: '{}' → '{}'", oldSlug, newSlug);

            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY); // 301
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            var redirectBody = Map.of(
                "error",
                "WORKSPACE_SLUG_MOVED",
                "message",
                "Workspace slug has been renamed",
                "oldSlug",
                oldSlug,
                "newSlug",
                newSlug
            );

            response.getWriter().write(objectMapper.writeValueAsString(redirectBody));
            return;
        }

        // No redirect found - standard 404
        sendWorkspaceNotFoundError(response, oldSlug);
    }
}
