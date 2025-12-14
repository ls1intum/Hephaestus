package de.tum.in.www1.hephaestus.testconfig;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.PostgreSQLContainer;

@Component
@Profile("github-integration")
public class GitHubIntegrationPostgresShutdown {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIntegrationPostgresShutdown.class);

    @PostConstruct
    public void logActivation() {
        logger.info("GitHub integration Postgres shutdown hook initialized.");
    }

    @PreDestroy
    public void stopPostgresContainer() {
        PostgreSQLContainer<?> container = PostgreSQLTestContainer.getInstance();
        if (container != null && container.isRunning()) {
            logger.info("Stopping PostgreSQL testcontainer before JVM shutdown.");
            container.stop();
        } else {
            logger.info("PostgreSQL testcontainer already stopped or not initialized.");
        }
    }
}
