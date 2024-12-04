package de.tum.in.www1.hephaestus.config;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class SentryConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SentryConfiguration.class);

    @Autowired
    private Environment environment;

    @Value("${spring.application.version}")
    private String hephaestusVersion;

    @Value("${sentry.dsn}")
    private Optional<String> sentryDsn;

    /**
     * Init sentry with the correct environment and version
     */
    @PostConstruct
    public void init() {
        if (sentryDsn.isEmpty() || sentryDsn.get().isEmpty()) {
            logger.info("Sentry is disabled: Provide a DSN to enable Sentry.");
            return;
        }

        try {
            final String dsn = sentryDsn.get() + "?stacktrace.app.packages=de.tum.in.www1.hephaestus";
            logger.info("Sentry DSN: {}", dsn);
            logger.info("Current environment: {}", getEnvironment());

            Sentry.init(options -> {
                options.setDsn(dsn);
                options.setSendDefaultPii(true);
                options.setEnvironment(getEnvironment());
                options.setRelease(hephaestusVersion);
                options.setTracesSampleRate(getTracesSampleRate());
            });
        } catch (Exception ex) {
            logger.error("Sentry configuration was not successful due to exception!", ex);
        }
    }

    private String getEnvironment() {
        if (environment.matchesProfiles("test")) {
            return "test";
        } else if (environment.matchesProfiles("prod")) {
            return "prod";
        } else {
            return "local";
        }
    }

    /**
     * Get the traces sample rate based on the environment.
     *
     * @return 0% for local, 100% for test, 20% for production environments
     */
    private double getTracesSampleRate() {
        return switch (getEnvironment()) {
            case "test" -> 1.0;
            case "prod" -> 0.2;
            default -> 0.0;
        };
    }
}
