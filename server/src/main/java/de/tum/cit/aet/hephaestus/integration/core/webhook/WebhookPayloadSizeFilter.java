package de.tum.cit.aet.hephaestus.integration.core.webhook;

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
 * Rejects oversized inbound POSTs before Spring buffers the body. Tomcat's
 * {@code max-http-post-size} only enforces on form bodies, so JSON webhooks would
 * otherwise be unconstrained. Missing {@code Content-Length} is rejected with 411.
 *
 * <p>Prefix-aware over the public, unauthenticated ingest surfaces: {@code /webhooks/<kind>}
 * (GitHub/GitLab HMAC receiver) and {@code /slack/*} (the Slack Events and Interactivity endpoints,
 * which read an unauthenticated {@code @RequestBody byte[]} before their v0 HMAC check). The Slack
 * tunnel is on the same {@code HIGHEST_PRECEDENCE} guard so it cannot buffer an unbounded body before
 * verification. Any other URI falls through untouched. Registration of the {@code /webhooks/*} and
 * {@code /slack/*} patterns is split across configs so the Slack guard survives even the NATS-off
 * topology (see {@code SlackWebSecurityConfiguration}).
 */
public class WebhookPayloadSizeFilter extends OncePerRequestFilter {

    private static final String UNIFIED_WEBHOOK_PREFIX = "/webhooks/";
    private static final String SLACK_PREFIX = "/slack/";
    private static final String[] GUARDED_PREFIXES = { UNIFIED_WEBHOOK_PREFIX, SLACK_PREFIX };

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
        return !isGuardedUri(request.getRequestURI());
    }

    private static boolean isGuardedUri(String uri) {
        for (String prefix : GUARDED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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
        // The filter only runs for guarded prefixes (see shouldNotFilter). /webhooks/<kind> tags on <kind>;
        // /slack/<...> tags "slack" so the rejection counter stays a stable, low-cardinality provider label.
        if (uri.startsWith(SLACK_PREFIX)) {
            return "slack";
        }
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
