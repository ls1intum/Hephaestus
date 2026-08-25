package de.tum.cit.aet.hephaestus.config;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

// `dsn` defaults to "" when SENTRY_DSN is unset; @ConditionalOnProperty(name = "dsn") would
// still match the empty string. Match only when the DSN is non-blank.
@Configuration
@ConditionalOnExpression("!'${hephaestus.sentry.dsn:}'.isBlank()")
public class SentryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SentryConfiguration.class);

    private final Environment environment;
    private final String hephaestusVersion;
    private final SentryProperties sentryProperties;

    public SentryConfiguration(
        Environment environment,
        @Value("${spring.application.version}") String hephaestusVersion,
        SentryProperties sentryProperties
    ) {
        this.environment = environment;
        this.hephaestusVersion = hephaestusVersion;
        this.sentryProperties = sentryProperties;
    }

    @PostConstruct
    public void init() {
        if (environment.matchesProfiles("specs")) {
            log.info("Skipped Sentry initialization: reason=specs_profile");
            return;
        }

        try {
            final String dsn = sentryProperties.dsn() + "?stacktrace.app.packages=de.tum.cit.aet.hephaestus";

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

    private double getTracesSampleRate() {
        return switch (getEnvironment()) {
            case "test" -> 1.0;
            case "prod" -> 0.2;
            default -> 0.0;
        };
    }
}
