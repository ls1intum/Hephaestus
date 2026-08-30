package de.tum.cit.aet.hephaestus.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnBean({Tracer.class, Propagator.class})
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";

    private final Tracer tracer;
    private final Propagator propagator;

    public RequestCorrelationFilter(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Span current = tracer.currentSpan();
        if (current != null) {
            response.setHeader(HEADER_NAME, current.context().traceId());
            chain.doFilter(request, response);
            return;
        }

        Span span = propagator
                .extract(request, HttpServletRequest::getHeader)
                .name("http.request")
                .kind(Span.Kind.SERVER)
                .start();
        response.setHeader(HEADER_NAME, span.context().traceId());
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException error) {
            span.error(error);
            throw error;
        } finally {
            span.end();
        }
    }
}
