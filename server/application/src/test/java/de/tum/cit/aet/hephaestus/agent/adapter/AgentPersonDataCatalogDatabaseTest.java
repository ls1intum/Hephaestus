package de.tum.cit.aet.hephaestus.agent.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJobLifecycleService;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataContributor;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataSelection;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonEvidenceErasure;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentityResolver;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonScope;
import de.tum.cit.aet.hephaestus.practices.adapter.PracticesPersonDataCatalog;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyCleanupCustody;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportService;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

@Tag("database")
class AgentPersonDataCatalogDatabaseTest {
    @Test
    void shouldExportAndEraseDeveloperJobsAndProductRowsAgainstTheCutoverSchema() throws Exception {
        var database = PostgreSQLTestContainer.createReleasedDatabase(
                "agent_erasure_" + UUID.randomUUID().toString().replace("-", ""), "prod");
        var jdbc = new NamedParameterJdbcTemplate(
                new DriverManagerDataSource(database.jdbcUrl(), database.username(), database.password()));
        var plain = jdbc.getJdbcTemplate();
        try (var seed =
                Objects.requireNonNull(getClass().getResourceAsStream("/db/practice-cutover/released-seed.sql"))) {
            plain.execute(new String(seed.readAllBytes(), StandardCharsets.UTF_8));
        }
        PostgreSQLTestContainer.migrateDatabase(database, "prod");
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var lifecycle = mock(ReviewJobLifecycleService.class);
        var noAcknowledgement = new AgentPersonDataCatalog(jdbc, mapper, List.of(), lifecycle);
        var person = new PersonScope(992106L, List.of(), List.of(992103L));
        var job = UUID.fromString("00000000-0000-0000-0000-000000992111");
        plain.update("UPDATE review_job SET capture='{}'::jsonb WHERE id=?", job);
        var unacknowledgedStore = noAcknowledgement.contributors().stream()
                .filter(store -> store.store().equals("review_job"))
                .findFirst()
                .orElseThrow();
        var selected = unacknowledgedStore.select(person);
        assertThat(selected.rows())
                .extracting(row -> row.columns().get("id"))
                .contains(job.toString())
                .doesNotContain("00000000-0000-0000-0000-000000992112");
        assertThat(unacknowledgedStore.export(selected))
                .anySatisfy(row -> assertThat(row.path("id").asString()).isEqualTo(job.toString()));
        assertThatThrownBy(() -> unacknowledgedStore.prepareErasure(selected))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Evidence-copy erasure acknowledgement is unavailable");
        assertThat(plain.queryForObject("SELECT id FROM review_job WHERE id=?", UUID.class, job))
                .isEqualTo(job);

        var runtime = new AgentPersonDataCatalog(jdbc, mapper, List.of(mock(PersonEvidenceErasure.class)), lifecycle);
        var products = new PracticesPersonDataCatalog(
                jdbc,
                mapper,
                mock(FeedbackCopyCleanupCustody.class),
                mock(PersonIdentityResolver.class),
                mock(ScanReportService.class));
        var stores = new ArrayList<PersonDataContributor>(runtime.contributors());
        stores.addAll(products.contributors());
        var frozen = stores.stream()
                .map(store -> new Selected(store, store.select(person)))
                .toList();
        frozen.forEach(entry -> entry.store().export(entry.selection()));
        frozen.forEach(entry -> entry.store().prepareErasure(entry.selection()));
        frozen.stream()
                .sorted(Comparator.comparingInt(entry -> entry.store().getOrder()))
                .forEach(entry -> entry.store().erase(entry.selection()));
        assertThat(plain.queryForList("SELECT id FROM review_job WHERE id=?", UUID.class, job))
                .isEmpty();
        assertThat(plain.queryForList(
                        "SELECT id FROM result WHERE id=?",
                        UUID.class,
                        UUID.fromString("00000000-0000-0000-0000-000000992141")))
                .isEmpty();
        assertThat(plain.queryForList(
                        "SELECT id FROM feedback WHERE id=?",
                        UUID.class,
                        UUID.fromString("00000000-0000-0000-0000-000000992161")))
                .isEmpty();
        assertThat(plain.queryForList(
                        "SELECT id FROM review_job WHERE id=?",
                        UUID.class,
                        UUID.fromString("00000000-0000-0000-0000-000000992112")))
                .containsExactly(UUID.fromString("00000000-0000-0000-0000-000000992112"));
        var copying = runtime.contributors().stream()
                .filter(store -> store.store().equals("review_job"))
                .findFirst()
                .orElseThrow()
                .select(new PersonScope(null, List.of(), List.of(), List.of(), List.of(), List.of(992109L)));
        assertThat(copying.rows())
                .extracting(row -> row.columns().get("id"))
                .contains("00000000-0000-0000-0000-000000992112");
    }

    private record Selected(PersonDataContributor store, PersonDataSelection selection) {}
}
