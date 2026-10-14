package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

public final class JobTokenAuthenticationFilter extends AuthenticationFilter {
    static final String PRACTICE_SCRIPT_PATH = "/internal/llm/practice-script/";
    private static final String RUNTIME_PATH = "/internal/llm/runtime/";
    public static final String PRACTICE_SCRIPT_PRACTICE_HEADER = "x-hephaestus-practice";

    JobTokenAuthenticationFilter(
            ReviewJobRepository jobs,
            WorkerJwtVerifier verifier,
            MentorProxyCredentialRegistry mentors,
            String workerId) {
        super(
                new ProviderManager(new JobTokenAuthenticationProvider(jobs, verifier, mentors, workerId)),
                JobTokenAuthenticationFilter::convert);
        setSuccessHandler(new AuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(
                    HttpServletRequest request, HttpServletResponse response, Authentication authentication) {}

            @Override
            public void onAuthenticationSuccess(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain,
                    Authentication authentication)
                    throws IOException, ServletException {
                chain.doFilter(request, response);
            }
        });
        setFailureHandler((request, response, failure) -> response.setStatus(
                failure instanceof JobTokenAuthenticationProvider.WorkerOwnershipConflict
                        ? HttpServletResponse.SC_CONFLICT
                        : HttpServletResponse.SC_UNAUTHORIZED));
    }

    private static @Nullable Authentication convert(HttpServletRequest request) {
        if (!isPrivateIp(request.getRemoteAddr())) throw new BadCredentialsException("Private gateway required");
        var converter = new BearerTokenAuthenticationConverter();
        converter.setAuthenticationDetailsSource(incoming -> {
            String path = incoming.getRequestURI();
            String runtimeJobId = null;
            if (path.startsWith(RUNTIME_PATH)) {
                int end = path.indexOf('/', RUNTIME_PATH.length());
                runtimeJobId = path.substring(RUNTIME_PATH.length(), end < 0 ? path.length() : end);
            }
            return new RequestIdentity(
                    runtimeJobId,
                    "POST".equals(incoming.getMethod()) && runtimeJobId != null && path.endsWith("/result"));
        });
        var authentication = converter.convert(request);
        if (authentication == null) throw new BadCredentialsException("Bearer credential required");
        return authentication;
    }

    record RequestIdentity(@Nullable String runtimeJobId, boolean resultUpload) {}

    private static final Pattern IP_LITERAL_PATTERN =
            Pattern.compile("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$|^[0-9a-fA-F]*:[0-9a-fA-F:]*$");

    static boolean isPrivateIp(@Nullable String ip) {
        if (ip == null || !IP_LITERAL_PATTERN.matcher(ip).matches()) return false;
        try {
            var address = InetAddress.getByName(ip);
            return address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress();
        } catch (UnknownHostException invalid) {
            return false;
        }
    }
}
