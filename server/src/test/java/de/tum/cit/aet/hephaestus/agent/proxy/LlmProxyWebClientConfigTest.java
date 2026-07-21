package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

/**
 * Proves the LLM proxy's {@link WebClient} bean actually carries the connect-time SSRF guard
 * (#1368 fix wave) — i.e. {@code EgressPolicy.validate()} is not the only line of defense against a
 * DNS-rebind target. A hostname that resolves to loopback ({@code localhost}, offline via the hosts
 * file) must be rejected AT RESOLUTION when the proxy is built with {@code allowLoopback=false}
 * (production default), and must pass resolution (though not actually connect, since nothing listens
 * on the probe port) when built with {@code allowLoopback=true} (dev/e2e).
 */
@Timeout(15) // safety net: the unguarded control's connect must not hang the suite
class LlmProxyWebClientConfigTest extends BaseUnitTest {

    private static final String LOOPBACK_URL = "http://localhost:9/";
    private static final Duration BLOCK = Duration.ofSeconds(5);

    @Test
    void proxyWebClientRejectsLoopbackResolvingHostWhenLoopbackNotAllowed() {
        LlmProxyWebClientConfig config = new LlmProxyWebClientConfig();
        ConnectionProvider provider = config.llmProxyConnectionProvider();
        LoopResources loop = config.llmProxyLoopResources();
        try {
            WebClient client = config.llmProxyWebClient(provider, loop, false);

            Throwable thrown = catchThrowable(() ->
                client.get().uri(LOOPBACK_URL).retrieve().bodyToMono(String.class).block(BLOCK)
            );

            Throwable root = rootCause(thrown);
            assertThat(root)
                .as("guard must reject at DNS resolution, not surface a plain connect failure")
                .isInstanceOf(UnknownHostException.class)
                .isNotInstanceOf(ConnectException.class);
            assertThat(root.getMessage()).contains("SSRF guard");
        } finally {
            provider.dispose();
            loop.disposeLater().block(BLOCK);
        }
    }

    @Test
    void proxyWebClientAllowsLoopbackResolutionWhenLoopbackAllowed() {
        LlmProxyWebClientConfig config = new LlmProxyWebClientConfig();
        ConnectionProvider provider = config.llmProxyConnectionProvider();
        LoopResources loop = config.llmProxyLoopResources();
        try {
            WebClient client = config.llmProxyWebClient(provider, loop, true);

            Throwable thrown = catchThrowable(() ->
                client.get().uri(LOOPBACK_URL).retrieve().bodyToMono(String.class).block(BLOCK)
            );

            // Resolution succeeds (loopback exempted); the failure — if any — is a plain connect
            // failure (nothing listens on port 9), never our guard's UnknownHostException.
            Throwable root = rootCause(thrown);
            assertThat(String.valueOf(root.getMessage())).doesNotContain("SSRF guard");
        } finally {
            provider.dispose();
            loop.disposeLater().block(BLOCK);
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
