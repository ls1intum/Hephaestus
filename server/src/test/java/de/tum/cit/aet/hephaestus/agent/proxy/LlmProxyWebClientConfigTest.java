package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import de.tum.cit.aet.hephaestus.agent.LlmProperties;
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
 * — i.e. {@code EgressPolicy.validate()} is not the only line of defense against a
 * DNS-rebind target. A hostname that resolves to loopback ({@code localhost}, offline via the hosts
 * file) is rejected AT RESOLUTION when the proxy is built with {@code allowLoopback=false}
 * (production default), and the rejection names the guard rather than surfacing as a plain connect
 * failure — so dropping the resolver from the bean fails here.
 *
 * <p>The {@code allowLoopback=true} (dev/e2e) exemption is a property of the resolver itself and is
 * owned by {@code SsrfGuardedResolverGroupTest}, which asserts it positively.
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
            WebClient client = config.llmProxyWebClient(
                provider,
                loop,
                // Loopback disallowed: this test asserts the resolver refuses a host that resolves to 127.0.0.1.
                new LlmProperties(
                    "",
                    new LlmProperties.Egress(false),
                    new LlmProperties.Fx(LlmProperties.ECB_DAILY_URL)
                )
            );

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

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
