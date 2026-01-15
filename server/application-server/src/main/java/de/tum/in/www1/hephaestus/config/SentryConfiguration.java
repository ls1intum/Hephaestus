package de.tum.in.www1.hephaestus.config;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class SentryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SentryConfiguration.class);

    private final Environment environment;

    private final String hephaestusVersion;

    private final Optional<String> sentryDsn;

    public SentryConfiguration(
        Environment environment,
        @Value("${spring.application.version}") String hephaestusVersion,
        @Value("${sentry.dsn}") Optional<String> sentryDsn
    ) {
        this.environment = environment;
        this.hephaestusVersion = hephaestusVersion;
        this.sentryDsn = sentryDsn;
    }

    /**
     * Init sentry with the correct environment and version
     */
    @PostConstruct
    public void init() {
        if (environment.matchesProfiles("specs")) {
            log.info("Skipped Sentry initialization: reason=specs_profile");
            return;
        }

        if (sentryDsn.isEmpty() || sentryDsn.get().isEmpty()) {
            log.info("Skipped Sentry initialization: reason=missing_dsn");
            return;
        }

        try {
            final String dsn = sentryDsn.get() + "?stacktrace.app.packages=de.tum.in.www1.hephaestus";

            Sentry.init(options -> {
                options.setDsn(dsn);
                options.setSendDefaultPii(true);
                options.setEnvironment(getEnvironment());
                options.setRelease(hephaestusVersion);
                options.setTracesSampleRate(getTracesSampleRate());
            });

            log.info("Initialized Sentry");
        } catch (Exception ex) {
            log.error("Failed to initialize Sentry", ex);
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
