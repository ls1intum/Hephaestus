package de.tum.in.www1.hephaestus.workspace.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
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
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
@Profile("!specs")
public class WorkspaceContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceContextFilter.class);

    private static final Pattern WORKSPACE_PATH_PATTERN = Pattern.compile(
        "^/workspaces/([a-z0-9][a-z0-9-]{2,50})(/.*)?$"
    );

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final UserRepository userRepository;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final WorkspaceSlugHistoryRepository workspaceSlugHistoryRepository;
    private final ObjectMapper objectMapper;

    public WorkspaceContextFilter(
        WorkspaceRepository workspaceRepository,
        WorkspaceMembershipRepository workspaceMembershipRepository,
        UserRepository userRepository,
        WorkspaceMembershipService workspaceMembershipService,
        WorkspaceSlugHistoryRepository workspaceSlugHistoryRepository,
        ObjectMapper objectMapper
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.userRepository = userRepository;
        this.workspaceMembershipService = workspaceMembershipService;
        this.workspaceSlugHistoryRepository = workspaceSlugHistoryRepository;
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

        // Allow workspace registry endpoints that are not slugged
        if ("/workspaces".equals(path) || "/workspaces/".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (!path.startsWith("/workspaces/")) {
            // Not a workspace-scoped path, continue without context (allows /workspaces root endpoints)
            chain.doFilter(request, response);
            return;
        }

        var matcher = WORKSPACE_PATH_PATTERN.matcher(path);

        if (!matcher.matches()) {
            sendWorkspaceSlugValidationError(httpResponse, extractInvalidSlug(path));
            return;
        }

        String slug = matcher.group(1);
        String safeSlug = LoggingUtils.sanitizeForLog(slug);
        String method = httpRequest.getMethod();
        String remainingPath = matcher.group(2) != null ? matcher.group(2) : "";
        boolean isBasePath = remainingPath.isBlank() || "/".equals(remainingPath);
        boolean isStatusPath = remainingPath.startsWith("/status");

        try {
            // Look up workspace by slug
            var workspaceOpt = workspaceRepository.findByWorkspaceSlug(slug);

            if (workspaceOpt.isEmpty()) {
                if (handleSlugRedirect(httpRequest, httpResponse, slug, remainingPath)) {
                    return;
                }
                sendWorkspaceNotFoundError(httpResponse, slug);
                return;
            }

            var workspace = workspaceOpt.get();

            // Check workspace status - only ACTIVE workspaces are accessible
            boolean isReadRequest = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
            boolean allowLifecycleDelete = isBasePath && "DELETE".equalsIgnoreCase(method);
            boolean allowNonActive = isStatusPath || (isBasePath && isReadRequest) || allowLifecycleDelete;

            if (workspace.getStatus() != WorkspaceStatus.ACTIVE && !allowNonActive) {
                log.debug("Denied workspace access: reason=nonActiveStatus, workspaceSlug={}, status={}", safeSlug, workspace.getStatus());
                sendWorkspaceNotFoundError(httpResponse, slug);
                return;
            }

            // Fetch user roles
            var currentUser = userRepository.getCurrentUser();
            Set<WorkspaceRole> roles = fetchUserRoles(workspace, currentUser);

            boolean isPublicRead = Boolean.TRUE.equals(workspace.getIsPubliclyViewable()) && isReadRequest;

            if (roles.isEmpty() && !isPublicRead) {
                if (currentUser.isEmpty()) {
                    sendWorkspaceUnauthorizedError(httpResponse, slug);
                } else {
                    log.debug("Denied workspace access: reason=notMember, workspaceSlug={}", safeSlug);
                    sendWorkspaceMembershipForbiddenError(httpResponse, slug);
                }
                return;
            }

            // Create and set context
            WorkspaceContext context = WorkspaceContext.fromWorkspace(workspace, roles);

            // Overwrite detection: warn if context already set
            if (WorkspaceContextHolder.getContext() != null) {
                log.warn(
                    "Detected context leak: reason=contextAlreadySet, workspaceSlug={}",
                    safeSlug
                );
            }

            WorkspaceContextHolder.setContext(context);

            log.debug("Set workspace context: workspaceSlug={}, workspaceId={}, roles={}", safeSlug, context.id(), context.roles());

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
    private Set<WorkspaceRole> fetchUserRoles(
        de.tum.in.www1.hephaestus.workspace.Workspace workspace,
        Optional<User> userOpt
    ) {
        try {
            if (userOpt.isEmpty()) {
                log.debug("Skipped role fetch: reason=noAuthenticatedUser");
                return Set.of();
            }

            var membershipOpt = workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(
                workspace.getId(),
                userOpt.get().getId()
            );

            if (membershipOpt.isPresent()) {
                log.debug("Resolved user role: role={}", membershipOpt.get().getRole());
                return Set.of(membershipOpt.get().getRole());
            }

            // Auto-heal only when workspace has zero memberships (fresh dev DB)
            if (workspaceMembershipRepository.findByWorkspace_Id(workspace.getId()).isEmpty()) {
                try {
                    var created = workspaceMembershipService.createMembership(
                        workspace,
                        userOpt.get().getId(),
                        WorkspaceRole.ADMIN
                    );
                    log.info(
                        "Auto-added user to workspace: userLogin={}, workspaceSlug={}, role={}",
                        LoggingUtils.sanitizeForLog(userOpt.get().getLogin()),
                        LoggingUtils.sanitizeForLog(workspace.getWorkspaceSlug()),
                        created.getRole()
                    );
                    return Set.of(created.getRole());
                } catch (IllegalArgumentException ex) {
                    log.debug(
                        "Skipped membership auto-add: userLogin={}, workspaceSlug={}",
                        LoggingUtils.sanitizeForLog(userOpt.get().getLogin()),
                        LoggingUtils.sanitizeForLog(workspace.getWorkspaceSlug()),
                        ex
                    );
                }
            }

            log.debug("Returning empty roles: reason=noMembership, workspaceId={}", workspace.getId());
            return Set.of();
        } catch (Exception e) {
            log.warn("Failed to fetch user roles: workspaceId={}", workspace.getId(), e);
            return Set.of();
        }
    }

    private boolean handleSlugRedirect(
        HttpServletRequest request,
        HttpServletResponse response,
        String oldSlug,
        String remainingPath
    ) throws IOException {
        var historyOpt = workspaceSlugHistoryRepository.findFirstByOldSlugOrderByChangedAtDesc(oldSlug);
        if (historyOpt.isEmpty()) {
            return false;
        }

        var history = historyOpt.get();
        Instant now = Instant.now();
        if (history.getRedirectExpiresAt() != null && history.getRedirectExpiresAt().isBefore(now)) {
            log.debug(
                "Denied slug redirect: reason=expired, oldSlug={}, expiredAt={}",
                LoggingUtils.sanitizeForLog(oldSlug),
                history.getRedirectExpiresAt()
            );
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.GONE);
            problem.setTitle("Workspace slug expired");
            problem.setDetail("Redirect for this workspace slug has expired");
            problem.setProperty("oldSlug", oldSlug);
            problem.setProperty("expiredAt", history.getRedirectExpiresAt());
            response.setStatus(HttpStatus.GONE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(problem));
            return true;
        }
        var workspace = workspaceRepository.findById(history.getWorkspace().getId()).orElse(null);

        if (workspace == null) {
            log.warn(
                "Skipped slug redirect: reason=missingWorkspace, oldSlug={}",
                LoggingUtils.sanitizeForLog(oldSlug)
            );
            return false;
        }

        // Avoid leaking workspace existence for private workspaces when the user lacks membership.
        boolean isPublic = Boolean.TRUE.equals(workspace.getIsPubliclyViewable());
        boolean hasMembership = userRepository
            .getCurrentUser()
            .flatMap(user ->
                workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(workspace.getId(), user.getId())
            )
            .isPresent();

        if (!isPublic && !hasMembership) {
            return false;
        }

        String newSlug = history.getNewSlug();
        String suffix = remainingPath == null ? "" : remainingPath;
        String queryString = request.getQueryString();
        String location = request.getContextPath() + "/workspaces/" + newSlug + suffix;
        if (queryString != null && !queryString.isBlank()) {
            location += '?' + queryString;
        }

        log.debug(
            "Redirecting workspace slug: oldSlug={}, newSlug={}",
            LoggingUtils.sanitizeForLog(oldSlug),
            LoggingUtils.sanitizeForLog(newSlug)
        );

        response.setStatus(HttpStatus.PERMANENT_REDIRECT.value());
        response.setHeader(HttpHeaders.LOCATION, location);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentLength(0);
        response.flushBuffer();
        return true;
    }

    /**
     * Send a 404 JSON error response for workspace not found.
     *
     * @param response HTTP response
     * @param slug Workspace slug
     */
    private void sendWorkspaceNotFoundError(HttpServletResponse response, String slug) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Resource not found");
        problem.setDetail("Workspace not found: " + slug);
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private void sendWorkspaceSlugValidationError(HttpServletResponse response, String slug) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("Invalid workspace slug: " + slug);
        problem.setProperty(
            "errors",
            Map.of("workspaceSlug", "Slug must be 3-51 lowercase characters or digits and may include single hyphens")
        );
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private void sendWorkspaceMembershipForbiddenError(HttpServletResponse response, String slug) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Membership required");
        problem.setDetail("You must be a member of workspace " + slug + " to access this resource.");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private void sendWorkspaceUnauthorizedError(HttpServletResponse response, String slug) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Authentication required");
        problem.setDetail("You must sign in to access workspace " + slug + ".");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private String extractInvalidSlug(String path) {
        String remainder = path.substring("/workspaces/".length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex >= 0) {
            remainder = remainder.substring(0, slashIndex);
        }
        return remainder;
    }
}
