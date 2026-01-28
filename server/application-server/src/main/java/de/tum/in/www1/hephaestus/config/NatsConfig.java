package de.tum.in.www1.hephaestus.config;

import de.tum.in.www1.hephaestus.gitprovider.sync.NatsProperties;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(name = "hephaestus.sync.nats.enabled", havingValue = "true")
public class NatsConfig {

    private final NatsProperties natsProperties;

    private final Environment environment;

    public NatsConfig(NatsProperties natsProperties, Environment environment) {
        this.natsProperties = natsProperties;
        this.environment = environment;
    }

    @Bean
    public Connection natsConnection() throws Exception {
        if (environment.matchesProfiles("specs")) {
            return null;
        }

        if (natsProperties.server() == null || natsProperties.server().isBlank()) {
            throw new IllegalStateException("NATS server configuration is missing.");
        }

        Options options = Options.builder().server(natsProperties.server()).build();
        return Nats.connect(options);
    }
}
