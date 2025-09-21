package de.tum.in.www1.hephaestus.proxy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;

@Configuration
public class ProxyConfig {

    @Bean
    public WebClient mentorWebClient(@Value("${hephaestus.intelligence-service.url}") String intelligenceServiceUrl) {
        // Configure a pooled, timeout-enabled WebClient
        var provider = ConnectionProvider.builder("mentor-proxy-pool")
            .maxConnections(200)
            .pendingAcquireMaxCount(500)
            .build();

        HttpClient httpClient = HttpClient.create(provider)
            .responseTimeout(Duration.ofSeconds(60))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(60))
                .addHandlerLast(new WriteTimeoutHandler(60)));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB buffers
            .build();

        return WebClient.builder()
            .baseUrl(intelligenceServiceUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(strategies)
            .build();
    }
}
