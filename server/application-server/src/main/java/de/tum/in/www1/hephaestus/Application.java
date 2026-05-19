package de.tum.in.www1.hephaestus;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableScheduling
@EnableResilientMethods
public class Application {

    /**
     * Capacity of the in-memory startup-event buffer.
     * 2048 covers every step a Hephaestus boot currently emits with room to grow; events
     * beyond the buffer are silently dropped (the {@code /actuator/startup} endpoint reports
     * which ones if it happens).
     */
    private static final int STARTUP_BUFFER_CAPACITY = 2048;

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        SpringApplication application = new SpringApplication(Application.class);
        // BufferingApplicationStartup feeds /actuator/startup (exposed in local profile only)
        // and powers StartupBudgetIT's regression guard. Documented in Spring Boot reference:
        // https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.startup-tracking
        application.setApplicationStartup(new BufferingApplicationStartup(STARTUP_BUFFER_CAPACITY));
        application.run(args);
    }
}
