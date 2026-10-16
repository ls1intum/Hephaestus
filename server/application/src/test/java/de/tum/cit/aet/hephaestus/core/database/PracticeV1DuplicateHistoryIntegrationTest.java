package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import de.tum.cit.aet.hephaestus.agent.adapter.EvidenceFolderPersonDataCatalog;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataContributor;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataSelection;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonScope;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemSourceAccess;
import de.tum.cit.aet.hephaestus.practices.adapter.PracticesPersonDataCatalog;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackHistoryQueryService;
import de.tum.cit.aet.hephaestus.practices.result.ResultHistoryQueryService;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultSourceAuthorization;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(PracticeV1DuplicateHistoryIntegrationTest.MigratedHistory.class)
@TestPropertySource(properties = "spring.liquibase.contexts=prod")
class PracticeV1DuplicateHistoryIntegrationTest extends BaseIntegrationTest {
    private static final UUID PRODUCER = UUID.fromString("00000000-0000-0000-0000-000000996111");
    private static final Instant SINCE = Instant.parse("2026-01-06T00:00:00Z");

    @Autowired
    ResultHistoryQueryService history;

    @Autowired
    FeedbackHistoryQueryService feedback;

    @Autowired
    ResultSourceAuthorization authorization;

    @Autowired
    ResultRepository results;

    @Autowired
    PracticesPersonDataCatalog products;

    @Autowired
    EvidenceFolderPersonDataCatalog copies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactions;

    @MockitoBean
    ScmItemSourceAccess source;

    @TestConfiguration(proxyBeanMethods = false)
    static class MigratedHistory {
        @Bean
        @ConfigurationProperties("spring.datasource.hikari")
        HikariDataSource dataSource() throws Exception {
            var database = PracticeMigrationFixture.releasedDatabase("prod");
            PracticeMigrationFixture.seedDuplicateHistory(database);
            PracticeMigrationFixture.upgrade(database, "prod");
            PracticeMigrationFixture.execute(database, """
                INSERT INTO account(id,display_name,status,ai_choice,ai_choice_at)
                VALUES(996901,'Copied contributor','ACTIVE','IN_HOUSE_ONLY',now());
                INSERT INTO identity(account_id,provider_id,provider_user_key,linked_via)
                VALUES(996901,992102,'copied-contributor','SIGN_IN');
                INSERT INTO person_evidence_copy(id,workspace_id,job_id,store_id,state,payload)
                VALUES(gen_random_uuid(),992101,'00000000-0000-0000-0000-000000996111',gen_random_uuid(),'READY',
                    '{"dependencies":"COMPLETE","repositories":[],"copiedJobs":[],"identities":[{"providerType":"GITHUB","providerOrigin":"https://practice-v1.example","subject":"copied-contributor"}]}');
                """);
            return DataSourceBuilder.create()
                    .type(HikariDataSource.class)
                    .url(database.jdbcUrl())
                    .username(database.username())
                    .password(database.password())
                    .build();
        }
    }

    @Test
    void shouldReadEverySiblingWithRealCustodyThenEraseEveryCopiedContributorDependency() {
        when(source.kinds()).thenReturn(Set.of(new ItemKind("scm.pull_request"), new ItemKind("scm.repository")));
        when(source.permits(any(), any())).thenReturn(true);
        var siblings = results.findByWorkspaceIdAndReviewIdOrderByRecordedAtAscIdAsc(992101, PRODUCER);
        var siblingIds = siblings.stream().map(r -> r.getId()).toList();
        assertThat(siblings).hasSize(3);
        assertThat(history.forDeveloper(992101, 992106, SINCE, 100).stream()
                        .filter(r -> r.reviewId().equals(PRODUCER))
                        .toList())
                .hasSize(3)
                .allSatisfy(r -> {
                    assertThat(r.summary()).startsWith("Retained summary ");
                    assertThat(r.explanation()).startsWith("Retained explanation ");
                    assertThat(r.evidence()).isNotNull();
                    assertThat(r.evidenceVersion()).isZero();
                    assertThat(r.copyAuthorityComplete()).isTrue();
                    assertThat(r.sourceJobIds()).contains(PRODUCER);
                });
        assertThat(feedback.forDeveloper(992101, 992106, SINCE, 100).stream()
                        .filter(f -> f.reviewId().equals(PRODUCER))
                        .toList())
                .hasSize(3)
                .allSatisfy(f -> {
                    assertThat(f.body()).startsWith("Retained advice ");
                    assertThat(f.resultIds()).hasSize(1).allMatch(siblingIds::contains);
                    assertThat(f.copyAuthorityComplete()).isTrue();
                });
        when(source.permits(any(), any())).thenReturn(false);
        assertHidden();
        when(source.permits(any(), any())).thenReturn(true);
        var request = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO person_data_request(id,state,counts_json,completed_json,created_at,expires_at,version,selections_json)
            VALUES(?,'ERASING','{}','{}',now(),now()+interval '1 day',0,?)
            """, request, "{\"review_job\":{\"rows\":[{\"columns\":{\"id\":\"" + PRODUCER + "\"}}]}}");
        assertHidden();
        siblings.forEach(r -> assertThat(
                        authorization.permitsHistory(992101, r.getId(), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse());
        jdbc.update("DELETE FROM person_data_request WHERE id=?", request);
        var person =
                new PersonScope(996901L, List.of(new PersonIdentity(992102, "copied-contributor", null)), List.of());
        var jobs = copies.jobsContaining(person);
        assertThat(jobs).contains(PRODUCER);
        var scope = new PersonScope(
                person.accountId(),
                person.identities(),
                person.userIds(),
                List.of(),
                List.of(),
                List.of(),
                List.copyOf(jobs));
        jdbc.update("UPDATE review_item SET job_id=NULL WHERE review_id=?", PRODUCER);
        var selected = products.contributors().stream()
                .map(store -> new Selected(store, store.select(scope)))
                .toList();
        assertThat(selected.stream()
                        .filter(s -> s.store().store().equals("result"))
                        .flatMap(s -> s.selection().rows().stream())
                        .map(r -> r.columns().get("id"))
                        .toList())
                .containsExactlyInAnyOrderElementsOf(
                        siblingIds.stream().map(UUID::toString).toList());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            selected.forEach(s -> s.store().prepareErasure(s.selection()));
            selected.stream()
                    .sorted(Comparator.comparingInt(s -> s.store().getOrder()))
                    .forEach(s -> s.store().erase(s.selection(), scope));
        });
        assertThat(results.findByWorkspaceIdAndIdIn(992101, siblingIds)).isEmpty();
        assertThat(jdbc.queryForList("SELECT id FROM feedback WHERE review_id=?", UUID.class, PRODUCER))
                .isEmpty();
        assertThat(jdbc.queryForList(
                        "SELECT id FROM problem WHERE result_id=ANY(?)",
                        UUID.class,
                        new SqlArrayValue("uuid", siblingIds.toArray())))
                .isEmpty();
        assertThat(results.findByIdAndWorkspaceId(UUID.fromString("00000000-0000-0000-0000-000000996144"), 992101))
                .isPresent();
    }

    private void assertHidden() {
        assertThat(history.forDeveloper(992101, 992106, SINCE, 100).stream()
                        .filter(r -> r.reviewId().equals(PRODUCER))
                        .toList())
                .hasSize(3)
                .allSatisfy(r -> {
                    assertThat(r.summary()).isNull();
                    assertThat(r.evidence()).isNull();
                    assertThat(r.copyAuthorityComplete()).isFalse();
                });
        assertThat(feedback.forDeveloper(992101, 992106, SINCE, 100).stream()
                        .filter(f -> f.reviewId().equals(PRODUCER))
                        .toList())
                .hasSize(3)
                .allSatisfy(f -> assertThat(f.body()).isNull());
    }

    private record Selected(PersonDataContributor store, PersonDataSelection selection) {}
}
