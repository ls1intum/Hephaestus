package de.tum.cit.aet.hephaestus.gitprovider.webhook.web;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects oversized webhook POSTs before Spring buffers the body. Tomcat's
 * {@code max-http-post-size} only enforces on form bodies, so JSON webhooks would otherwise
 * be unconstrained. Missing {@code Content-Length} (chunked / unknown length) is rejected with
 * {@code 411 Length Required} — both GitHub and GitLab always send {@code Content-Length},
 * so chunked traffic on these endpoints is either a misconfigured client or an attempt to
 * bypass the size cap.
 */
public class WebhookPayloadSizeFilter extends OncePerRequestFilter {

    private static final Set<String> WEBHOOK_PATHS = Set.of("/gitlab", "/github");

    private final long maxPayloadBytes;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> rejectionCounters = new ConcurrentHashMap<>();

    public WebhookPayloadSizeFilter(WebhookProperties properties, MeterRegistry meterRegistry) {
        this.maxPayloadBytes = properties.http().maxPayloadBytes();
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && WEBHOOK_PATHS.contains(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        String provider = request.getRequestURI().substring(1);
        if (contentLength < 0) {
            rejected(provider, "length-required");
            response.setStatus(HttpServletResponse.SC_LENGTH_REQUIRED);
            return;
        }
        if (contentLength > maxPayloadBytes) {
            rejected(provider, "payload-too-large");
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }

    private void rejected(String provider, String reason) {
        rejectionCounters
            .computeIfAbsent(provider + ":" + reason, key ->
                Counter.builder("webhook.rejected")
                    .tag("provider", provider)
                    .tag("reason", reason)
                    .register(meterRegistry)
            )
            .increment();
    }
}
