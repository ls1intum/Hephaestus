package de.tum.cit.aet.hephaestus.agent.proxy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses an oversized sandbox request before anything buffers it. Tomcat's post-size attributes
 * bound only form and multipart parameter parsing, and every gateway capability is JSON, so this
 * filter is what {@code hephaestus.sandbox.gateway.max-request-bytes} enforces. A request that
 * declares no length is refused as well — its size is knowable only after it has been read, which is
 * the cost the limit exists to avoid.
 */
public final class SandboxGatewayPayloadSizeFilter extends OncePerRequestFilter {

    private final int maxRequestBytes;

    SandboxGatewayPayloadSizeFilter(int maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            response.setStatus(HttpServletResponse.SC_LENGTH_REQUIRED);
            return;
        }
        if (contentLength > maxRequestBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
