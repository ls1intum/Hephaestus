package de.tum.in.www1.hephaestus.testconfig;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Provides a {@link WebTestClient} bean bound to the RANDOM_PORT Boot-test server.
 *
 * <h2>Why this exists</h2>
 * <p>Spring Boot 3 shipped {@code @AutoConfigureWebTestClient} which silently registered a
 * {@code WebTestClient} bean against the random server port. Spring Boot 4 removed that annotation
 * and stopped autoconfiguring {@code WebTestClient} entirely. Every integration test that
 * autowires {@code WebTestClient} would otherwise fail to start with
 * {@code No qualifying bean of type 'WebTestClient' available}.
 *
 * <p>Also explicitly registers {@link JacksonJsonEncoder} / {@link JacksonJsonDecoder}
 * (Spring 7 / Jackson 3) so {@code .expectBody(ProblemDetail.class)} and friends round-trip
 * through Jackson 3 instead of falling back to Jackson 2 codecs that aren't on the classpath.
 *
 * <p>Imported transitively from {@link BaseIntegrationTest} so every integration test gets it
 * for free with no per-test boilerplate.
 */
@TestConfiguration
@Profile("test")
public class WebTestClientTestConfig {

    @Bean
    @Lazy
    public WebTestClient webTestClient(WebServerApplicationContext context, JsonMapper jsonMapper) {
        int port = context.getWebServer().getPort();
        return WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(30))
            .codecs(config -> {
                config.defaultCodecs().maxInMemorySize(16 * 1024 * 1024);
                config.customCodecs().register(new JacksonJsonEncoder(jsonMapper));
                config.customCodecs().register(new JacksonJsonDecoder(jsonMapper));
            })
            .build();
    }
}
