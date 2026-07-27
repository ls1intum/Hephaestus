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
 * Proves the LLM proxy's {@link WebClient} bean carries the connect-time SSRF guard, not just
 * {@code EgressPolicy.validate()}: a hostname resolving to loopback is rejected AT RESOLUTION
 * with {@code allowLoopback=false} (production default), naming the guard rather than surfacing a
 * plain connect failure.
 *
 * <p>The {@code allowLoopback=true} (dev/e2e) exemption is owned by {@code SsrfGuardedResolverGroupTest}.
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
