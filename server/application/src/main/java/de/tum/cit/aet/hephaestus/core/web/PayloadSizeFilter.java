package de.tum.cit.aet.hephaestus.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses an oversized request before anything buffers its body. Tomcat's post-size attributes
 * bound only form and multipart parameter parsing, so a JSON body is otherwise unconstrained. A
 * request that declares no {@code Content-Length} is refused as well — its size is knowable only
 * after it has been read, which is the cost the limit exists to avoid.
 */
public class PayloadSizeFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public PayloadSizeFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            rejected(request, "length-required");
            response.setStatus(HttpServletResponse.SC_LENGTH_REQUIRED);
            return;
        }
        if (contentLength > maxBytes) {
            rejected(request, "payload-too-large");
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Called before a refused request is answered; counts nothing unless a subclass says otherwise. */
    protected void rejected(HttpServletRequest request, String reason) {}
}
