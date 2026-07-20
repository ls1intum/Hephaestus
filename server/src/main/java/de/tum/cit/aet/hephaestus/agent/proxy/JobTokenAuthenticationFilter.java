package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Security filter that authenticates requests to the LLM proxy using proxy-scoped bearer
 * tokens: an {@code AgentJob}'s job token, or a mentor session's registry-minted token (#1368
 * slice 5 — the mentor's interactive sandbox is not an {@code AgentJob} row).
 *
 * <p>Agent containers send their token as the standard {@code Authorization: Bearer} header (Pi's
 * custom-provider convention — see {@code pi-provider.mjs}). This filter validates it against the
 * job table first, then the mentor registry, and sets a {@link JobTokenAuthentication} carrying the
 * resolved {@link ProxyRouting} on the security context.
 *
 * <p>Defense-in-depth: rejects requests from non-private IPs (only Docker-internal
 * traffic should reach these endpoints).
 */
public class JobTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JobTokenAuthenticationFilter.class);

    /** Base64-URL characters (no padding). */
    private static final Pattern BASE64_URL_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private static final String BEARER_PREFIX = "Bearer ";

    private final AgentJobRepository agentJobRepository;
    private final MentorProxyCredentialRegistry mentorRegistry;
    private final ObjectMapper objectMapper;

    JobTokenAuthenticationFilter(
        AgentJobRepository agentJobRepository,
        MentorProxyCredentialRegistry mentorRegistry,
        ObjectMapper objectMapper
    ) {
        this.agentJobRepository = agentJobRepository;
        this.mentorRegistry = mentorRegistry;
        this.objectMapper = objectMapper;
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

        String token = extractProxyToken(request);
        if (token == null || token.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
            return;
        }

        // Validate format: must be valid Base64-URL
        if (!BASE64_URL_PATTERN.matcher(token).matches()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token format");
            return;
        }

        Optional<ProxyRouting> routing = resolveJobRouting(token).or(() -> mentorRegistry.validate(token));
        if (routing.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new JobTokenAuthentication(routing.get()));
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Look up an {@code AgentJob} by token and translate its frozen {@link ConfigSnapshot} into routing. */
    private Optional<ProxyRouting> resolveJobRouting(String token) {
        String hash = AgentJob.computeTokenHash(token);
        Optional<AgentJob> optionalJob = agentJobRepository.findByJobTokenHashAndStatus(hash, AgentJobStatus.RUNNING);
        if (optionalJob.isEmpty()) {
            return Optional.empty();
        }
        AgentJob job = optionalJob.get();
        // Constant-time comparison of actual token (belt-and-suspenders after hash match)
        if (!MessageDigest.isEqual(token.getBytes(), job.getJobToken().getBytes())) {
            log.warn("Token hash matched but constant-time comparison failed — possible collision");
            return Optional.empty();
        }
        if (job.getConfigSnapshot() == null) {
            log.warn("RUNNING job {} has no config snapshot — cannot route proxy request", job.getId());
            return Optional.empty();
        }
        ConfigSnapshot snapshot;
        try {
            snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
        } catch (RuntimeException e) {
            log.warn("Failed to parse config snapshot for job {}: {}", job.getId(), e.getMessage());
            return Optional.empty();
        }
        return Optional.of(
            new ProxyRouting(
                "job:" + job.getId(),
                snapshot.apiProtocol(),
                snapshot.baseUrl(),
                snapshot.connectionScope(),
                snapshot.connectionId(),
                snapshot.configId()
            )
        );
    }

    /**
     * Extract the proxy token from whichever auth header the sandbox's outbound request used. The
     * wire shape depends on the connection's {@code apiProtocol} (Pi crafts the request natively for
     * that protocol — anthropic-messages sends {@code x-api-key}, azure-openai-responses sends
     * {@code api-key}, openai-completions sends {@code Authorization: Bearer}), which the proxy does
     * not know until AFTER it has resolved the token — so all three shapes are checked here,
     * independent of the token's value.
     */
    private String extractProxyToken(HttpServletRequest request) {
        String xApiKey = request.getHeader("x-api-key");
        if (xApiKey != null && !xApiKey.isBlank()) {
            return xApiKey.trim();
        }

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String bearer = auth.substring(BEARER_PREFIX.length()).trim();
            if (!bearer.isBlank()) {
                return bearer;
            }
        }

        String azureApiKey = request.getHeader("api-key");
        if (azureApiKey != null && !azureApiKey.isBlank()) {
            return azureApiKey.trim();
        }

        return null;
    }

    /** Matches numeric IPv4 or IPv6 address literals (rejects hostnames to avoid DNS resolution). */
    private static final Pattern IP_LITERAL_PATTERN = Pattern.compile("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$|^[0-9a-fA-F:]+$");

    static boolean isPrivateIp(String ip) {
        if (ip == null || !IP_LITERAL_PATTERN.matcher(ip).matches()) {
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
