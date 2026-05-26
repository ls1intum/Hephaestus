package de.tum.cit.aet.hephaestus.integration.webhook.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebhookPayloadSizeFilterTest extends BaseUnitTest {

    private static final long MAX = 1024;

    private final WebhookProperties properties = new WebhookProperties(
        null,
        null,
        new WebhookProperties.TokenRotation(7, 90),
        new WebhookProperties.Publish(Duration.ofSeconds(9), 5, Duration.ofMillis(200)),
        new WebhookProperties.Stream(Duration.ofMinutes(2), Duration.ofDays(180), 2_000_000L),
        new WebhookProperties.Shutdown(Duration.ofSeconds(15)),
        new WebhookProperties.Http(MAX)
    );

    private final MeterRegistry meters = new SimpleMeterRegistry();
    private final WebhookPayloadSizeFilter filter = new WebhookPayloadSizeFilter(properties, meters);

    @Test
    void rejectsOversizedGitlabPostWith413() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/gitlab");
        request.setContentType("application/json");
        request.setContent(new byte[2048]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        verify(chain, never()).doFilter(request, response);
        assertCounter("gitlab", "payload-too-large", 1);
        assertCounter("gitlab", "length-required", 0); // no swapped tags
    }

    @Test
    void allowsBodyExactlyAtMaxSize() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/github");
        request.setContentType("application/json");
        request.setContent(new byte[(int) MAX]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void rejectsBodyOneByteOverMaxSize() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/github");
        request.setContentType("application/json");
        request.setContent(new byte[(int) MAX + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsMissingContentLengthWith411() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/gitlab") {
            @Override
            public long getContentLengthLong() {
                return -1; // chunked transfer encoding or no Content-Length
            }
        };
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_LENGTH_REQUIRED);
        verify(chain, never()).doFilter(request, response);
        assertCounter("gitlab", "length-required", 1);
        assertCounter("gitlab", "payload-too-large", 0); // no swapped tags
    }

    private void assertCounter(String provider, String reason, double expected) {
        io.micrometer.core.instrument.Counter counter = meters
            .find("webhook.rejected")
            .tag("provider", provider)
            .tag("reason", reason)
            .counter();
        double actual = counter == null ? 0.0 : counter.count();
        assertThat(actual).as("webhook.rejected{provider=%s, reason=%s}", provider, reason).isEqualTo(expected);
    }

    @Test
    void bypassesNonWebhookPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/some-other-endpoint");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void bypassesGetRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/webhooks/gitlab");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void bypassesLegacyWebhookPaths() throws Exception {
        // Legacy /gitlab and /github URLs were retired in #1198 stage 2. The filter must NOT
        // match them — they should fall through to a normal 404 from Spring rather than be
        // accepted by the size filter (which would imply the route still exists).
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/gitlab");
        request.setContentType("application/json");
        request.setContent(new byte[2048]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
