package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(PracticeV1ReviewJobsIntegrationTest.MigratedHistory.class)
@TestPropertySource(properties = "spring.liquibase.contexts=prod")
class PracticeV1ReviewJobsIntegrationTest extends BaseIntegrationTest {
    private static final String ROOT = "/workspaces/practice-v1-cutover/practices/reviews";

    @Autowired
    WebTestClient http;

    @Autowired
    JdbcClient jdbc;

    @TestConfiguration(proxyBeanMethods = false)
    static class MigratedHistory {
        @Bean
        @ConfigurationProperties("spring.datasource.hikari")
        HikariDataSource dataSource() throws Exception {
            var database = PracticeMigrationFixture.releasedDatabase("prod");
            PracticeMigrationFixture.execute(database, """
            INSERT INTO agent_job(id,workspace_id,job_type,purpose,status,config_snapshot,job_token,retry_count,
                created_at,completed_at,available_at,trace_id,metadata)
            SELECT ('00000000-0000-0000-0000-000000' || fixture.id)::uuid,
                j.workspace_id,j.job_type,'PRACTICE_REVIEW',fixture.status,j.config_snapshot,'synthetic-upgrade-job-' || fixture.id,0,
                j.created_at,j.completed_at,j.available_at,md5(fixture.id::text),j.metadata
            FROM agent_job j CROSS JOIN (VALUES
                (992901,'QUEUED'),(992902,'RUNNING'),(992903,'CANCELLED'),
                (992904,'FAILED'),(992905,'TIMED_OUT')) AS fixture(id,status)
            WHERE j.id='00000000-0000-0000-0000-000000992111'
            """);
            PracticeMigrationFixture.upgrade(database, "prod");
            return DataSourceBuilder.create()
                    .type(HikariDataSource.class)
                    .url(database.jdbcUrl())
                    .username(database.username())
                    .password(database.password())
                    .build();
        }
    }

    @ParameterizedTest
    @CsvSource({"992901,CANCELLED", "992902,CANCELLED", "992903,CANCELLED", "992904,FAILED", "992905,TIMED_OUT"})
    void shouldReadMigratedJobsThroughReviewListWhenNoAssignmentsWereRetained(String suffix, String status) {
        String id = "00000000-0000-0000-0000-000000" + suffix;
        assertThat(jdbc.sql("SELECT input->'assignments' FROM review_job WHERE id=CAST(:id AS uuid)")
                        .param("id", id)
                        .query(String.class)
                        .single())
                .isEqualTo("[]");
        http.get()
                .uri(ROOT + "?size=100")
                .headers(headers -> headers.setBearerAuth("mock-jwt-member-992106"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content[?(@.id == '" + id + "')].jobCount")
                .value(value -> assertThat(value).isEqualTo(List.of(1)));

        http.get()
                .uri(ROOT + "/" + id)
                .headers(headers -> headers.setBearerAuth("mock-jwt-member-992106"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(id)
                .jsonPath("$.runtime.jobs[0].id")
                .isEqualTo(id)
                .jsonPath("$.runtime.jobs[0].status")
                .isEqualTo(status)
                .jsonPath("$.practices.length()")
                .isEqualTo(0);
    }
}
