package de.tum.cit.aet.hephaestus.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
class RequestCorrelationFilterTest {

    @Test
    void shouldEchoExtractedTraceIdOnResponse() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Propagator propagator = mock(Propagator.class);
        Span.Builder builder = mock(Span.Builder.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(propagator.extract(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(builder);
        when(builder.name("http.request")).thenReturn(builder);
        when(builder.kind(Span.Kind.SERVER)).thenReturn(builder);
        when(builder.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(mock(Tracer.SpanInScope.class));
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("0123456789abcdef0123456789abcdef");

        MockHttpServletResponse response = new MockHttpServletResponse();
        new RequestCorrelationFilter(tracer, propagator)
                .doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER_NAME))
                .isEqualTo("0123456789abcdef0123456789abcdef");
    }
}
