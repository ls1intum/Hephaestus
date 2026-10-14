package de.tum.cit.aet.hephaestus.testconfig;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

/** Table-dependent functions follow Hibernate DDL; migrated schemas need no test initializer. */
@Configuration(proxyBeanMethods = false)
@Profile("test")
@ConditionalOnProperty(name = "spring.liquibase.enabled", havingValue = "false")
public class TestReviewSchemaConfiguration {
    @Bean
    @DependsOn("entityManagerFactory")
    InitializingBean feedbackDigestSchema(DataSource dataSource) {
        return () -> {
            try (var connection = dataSource.getConnection()) {
                TestReviewSchemaFunctions.install(connection, "feedback_content_digest", "feedback_package_digest");
            }
        };
    }
}
