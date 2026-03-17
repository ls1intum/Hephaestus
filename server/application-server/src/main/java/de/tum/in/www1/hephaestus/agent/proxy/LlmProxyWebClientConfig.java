package de.tum.in.www1.hephaestus.agent.proxy;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * WebClient configuration for the LLM proxy.
 *
 * <p>Separate from the mentor WebClient — different timeout profile (LLM responses can
 * take minutes), no base URL (varies per provider per request), and independent connection pool.
 *
 * <p>Pool settings follow the pattern established by {@code GitHubGraphQlConfig} and
 * {@code GitLabGraphQlConfig} in this codebase.
 */
@Configuration
class LlmProxyWebClientConfig {

    @Bean(destroyMethod = "dispose")
    ConnectionProvider llmProxyConnectionProvider() {
        return ConnectionProvider.builder("llm-proxy-pool")
            .maxConnections(100)
            .pendingAcquireMaxCount(200)
            .maxIdleTime(Duration.ofSeconds(30))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(60))
            .build();
    }

    @Bean
    WebClient llmProxyWebClient(ConnectionProvider llmProxyConnectionProvider) {
        HttpClient httpClient = HttpClient.create(llmProxyConnectionProvider)
            // Guards against upstream never starting a response (e.g. DNS failure, firewall)
            .responseTimeout(Duration.ofSeconds(300))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .doOnConnected(conn ->
                // ReadTimeout: guards against upstream going silent mid-SSE-stream
                // WriteTimeout: guards against network issues sending the request body
                conn.addHandlerLast(new ReadTimeoutHandler(300)).addHandlerLast(new WriteTimeoutHandler(60))
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
