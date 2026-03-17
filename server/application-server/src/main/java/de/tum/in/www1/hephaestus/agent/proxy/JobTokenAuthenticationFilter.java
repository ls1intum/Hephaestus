package de.tum.in.www1.hephaestus.agent.proxy;

import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.AgentJobStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security filter that authenticates requests to the LLM proxy using job tokens.
 *
 * <p>Agent containers send their job token as the provider-specific API key header
 * ({@code x-api-key} for Anthropic, {@code Authorization: Bearer} for OpenAI).
 * This filter extracts the token from whichever header is present, validates it
 * against the database, and sets a {@link JobTokenAuthentication} on the security context.
 *
 * <p>Defense-in-depth: rejects requests from non-private IPs (only Docker-internal
 * traffic should reach these endpoints).
 */
class JobTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JobTokenAuthenticationFilter.class);

    /** Base64-URL characters (no padding). */
    private static final Pattern BASE64_URL_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private static final String BEARER_PREFIX = "Bearer ";

    private final AgentJobRepository agentJobRepository;

    JobTokenAuthenticationFilter(AgentJobRepository agentJobRepository) {
        this.agentJobRepository = agentJobRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        // Defense-in-depth: only accept requests from private IPs
        if (!isPrivateIp(request.getRemoteAddr())) {
            log.warn("LLM proxy request from non-private IP: {}", request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }

        // Extract token from whichever auth header is present
        String token = extractJobToken(request);
        if (token == null || token.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing job token");
            return;
        }

        // Validate format: must be valid Base64-URL
        if (!BASE64_URL_PATTERN.matcher(token).matches()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token format");
            return;
        }

        // Look up by hash (encrypted column cannot be queried directly)
        String hash = AgentJob.computeTokenHash(token);
        var optionalJob = agentJobRepository.findByJobTokenHashAndStatus(hash, AgentJobStatus.RUNNING);
        if (optionalJob.isEmpty()) {
            log.debug("No RUNNING job found for token hash");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        AgentJob job = optionalJob.get();

        // Constant-time comparison of actual token (belt-and-suspenders after hash match)
        if (!MessageDigest.isEqual(token.getBytes(), job.getJobToken().getBytes())) {
            log.warn("Token hash matched but constant-time comparison failed — possible collision");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new JobTokenAuthentication(job));
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Extract the job token from provider-specific auth headers.
     * Agents send their token as the standard API key for their provider.
     */
    private String extractJobToken(HttpServletRequest request) {
        // Anthropic-style: x-api-key header
        String xApiKey = request.getHeader("x-api-key");
        if (xApiKey != null && !xApiKey.isBlank()) {
            return xApiKey.trim();
        }

        // OpenAI-style: Authorization: Bearer <token>
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String bearer = auth.substring(BEARER_PREFIX.length()).trim();
            if (!bearer.isBlank()) {
                return bearer;
            }
        }

        // Azure OpenAI style: api-key header
        String azureApiKey = request.getHeader("api-key");
        if (azureApiKey != null && !azureApiKey.isBlank()) {
            return azureApiKey.trim();
        }

        return null;
    }

    static boolean isPrivateIp(String ip) {
        if (ip == null) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isSiteLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
