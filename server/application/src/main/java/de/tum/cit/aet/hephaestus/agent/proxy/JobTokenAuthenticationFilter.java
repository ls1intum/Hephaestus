package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtVerifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.apache.hc.core5.net.InetAddressUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

public final class JobTokenAuthenticationFilter extends AuthenticationFilter {
    static final String PRACTICE_SCRIPT_PATH = "/internal/llm/practice-script/";
    private static final String RUNTIME_PATH = "/internal/llm/runtime/";
    public static final String PRACTICE_SCRIPT_PRACTICE_HEADER = "x-hephaestus-practice";

    private static final BearerTokenAuthenticationConverter CONVERTER = converter();
    private static final List<IpAddressMatcher> PRIVATE_RANGES = List.of(
            new IpAddressMatcher("10.0.0.0/8"),
            new IpAddressMatcher("172.16.0.0/12"),
            new IpAddressMatcher("192.168.0.0/16"),
            new IpAddressMatcher("127.0.0.0/8"),
            new IpAddressMatcher("169.254.0.0/16"),
            new IpAddressMatcher("::1"),
            new IpAddressMatcher("fe80::/10"),
            new IpAddressMatcher("fec0::/10"));

    JobTokenAuthenticationFilter(
            ReviewJobRepository jobs,
            WorkerJwtVerifier verifier,
            MentorProxyCredentialRegistry mentors,
            String workerId) {
        super(
                new ProviderManager(new JobTokenAuthenticationProvider(jobs, verifier, mentors, workerId)),
                JobTokenAuthenticationFilter::convert);
        setSuccessHandler((request, response, authentication) -> {});
        setFailureHandler((request, response, failure) -> response.setStatus(
                failure instanceof JobTokenAuthenticationProvider.WorkerOwnershipConflict
                        ? HttpServletResponse.SC_CONFLICT
                        : HttpServletResponse.SC_UNAUTHORIZED));
    }

    private static BearerTokenAuthenticationConverter converter() {
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
        return converter;
    }

    private static @Nullable Authentication convert(HttpServletRequest request) {
        if (!isPrivateIp(request.getRemoteAddr())) throw new BadCredentialsException("Private gateway required");
        var authentication = CONVERTER.convert(request);
        if (authentication == null) throw new BadCredentialsException("Bearer credential required");
        return authentication;
    }

    record RequestIdentity(@Nullable String runtimeJobId, boolean resultUpload) {}

    static boolean isPrivateIp(@Nullable String ip) {
        if (ip == null || (!InetAddressUtils.isIPv4(ip) && !InetAddressUtils.isIPv6(ip))) return false;
        return PRIVATE_RANGES.stream().anyMatch(range -> range.matches(ip));
    }
}
