package de.tum.cit.aet.hephaestus.core;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application {@link Clock}. Deliberately not gated on a runtime role: every role injects it, and
 * gating it fails context refresh on worker and webhook.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
