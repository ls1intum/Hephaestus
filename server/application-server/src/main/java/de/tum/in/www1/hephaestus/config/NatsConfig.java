package de.tum.in.www1.hephaestus.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class NatsConfig {

    @Value("${nats.enabled}")
    private boolean isNatsEnabled;

    @Value("${nats.server}")
    private String natsServer;

    @Autowired
    private Environment environment;

    @Bean
    public Connection natsConnection() throws Exception {
        if (environment.matchesProfiles("specs") || !isNatsEnabled) {
            return null;
        }

        Options options = Options.builder().server(natsServer).build();
        return Nats.connect(options);
    }
}
