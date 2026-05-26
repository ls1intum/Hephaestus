package de.tum.cit.aet.hephaestus.integration.webhook.web;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects oversized webhook POSTs before Spring buffers the body. Tomcat's
 * {@code max-http-post-size} only enforces on form bodies, so JSON webhooks would
 * otherwise be unconstrained. Missing {@code Content-Length} is rejected with 411.
 * Bound to {@code /webhooks/<kind>}.
 */
public class WebhookPayloadSizeFilter extends OncePerRequestFilter {

    private static final String UNIFIED_WEBHOOK_PREFIX = "/webhooks/";

    private final long maxPayloadBytes;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> rejectionCounters = new ConcurrentHashMap<>();

    public WebhookPayloadSizeFilter(WebhookProperties properties, MeterRegistry meterRegistry) {
        this.maxPayloadBytes = properties.http().maxPayloadBytes();
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !request.getRequestURI().startsWith(UNIFIED_WEBHOOK_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        String provider = providerTag(request.getRequestURI());
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

    private static String providerTag(String uri) {
        // Filter only binds to /webhooks/*, so the prefix is guaranteed here.
        String tail = uri.substring(UNIFIED_WEBHOOK_PREFIX.length());
        int slash = tail.indexOf('/');
        return slash >= 0 ? tail.substring(0, slash) : tail;
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
