package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.core.WebClientConnectors;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

/**
 * WebClient configuration for the LLM proxy.
 *
 * <p>Separate from the mentor WebClient — different timeout profile (LLM responses can
 * take minutes), no base URL (varies per provider per request), and independent connection pool.
 *
 * <p>Pool settings follow the pattern established by {@code GitHubGraphQlConfig} and
 * {@code GitLabGraphQlConfig} in this codebase.
 *
 * <p><b>Connect-time SSRF guard (#1368 fix wave):</b> {@code EgressPolicy} only validates the
 * upstream host at snapshot-write / call-entry time; without a guarded resolver here, a DNS-rebind
 * target (public answer during validation, private answer at connect time) would sail straight
 * through this WebClient. {@link WebClientConnectors#resolverGroup} pins the SAME check to the
 * actual connection, and threads the {@code allow-loopback} dev/e2e exemption through so it agrees
 * with {@code EgressPolicy}'s own literal-host allowance instead of re-blocking it.
 */
@Configuration
class LlmProxyWebClientConfig {

    @Bean(destroyMethod = "dispose")
    ConnectionProvider llmProxyConnectionProvider() {
        return ConnectionProvider.builder("llm-proxy-pool")
            .maxConnections(100)
            .pendingAcquireMaxCount(200)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofMinutes(3))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(30))
            .build();
    }

    @Bean(destroyMethod = "dispose")
    LoopResources llmProxyLoopResources() {
        // Dedicated event loop so LLM SSE streams don't compete with GitHub/GitLab sync.
        return LoopResources.create("llm-proxy", 2, true);
    }

    @Bean
    WebClient llmProxyWebClient(
        ConnectionProvider llmProxyConnectionProvider,
        LoopResources llmProxyLoopResources,
        @Value("${hephaestus.llm.egress.allow-loopback:false}") boolean allowLoopback
    ) {
        HttpClient httpClient = HttpClient.create(llmProxyConnectionProvider)
            .runOn(llmProxyLoopResources)
            .responseTimeout(Duration.ofSeconds(300))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .resolver(WebClientConnectors.resolverGroup(allowLoopback))
            .doOnConnected(conn ->
                // No ReadTimeoutHandler — LLM SSE streams go silent during model thinking.
                // Stream duration is bounded by ProxyStreamingUtils.DEFAULT_SSE_TIMEOUT.
                conn.addHandlerLast(new WriteTimeoutHandler(60))
            );

        // 1MB buffer — we stream SSE, not buffer entire responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(strategies)
            .build();
    }
}
