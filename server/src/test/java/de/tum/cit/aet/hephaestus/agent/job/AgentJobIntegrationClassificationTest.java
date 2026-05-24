package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectClass;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-level guard rails for the integration-classification fields added to {@link AgentJob}
 * in #1198 (changeset {@code 1779700100000_agent_job_integration_classification}).
 *
 * <p>Boots no Spring context — the fields are simple Lombok-generated bean properties and
 * the JPA mapping shape (column name, length, enum-string discriminator) is asserted
 * reflectively. A full schema-DDL parity check happens in the Liquibase + Hibernate
 * integration suite; this keeps the contract green in the fast unit lane.
 */
@DisplayName("AgentJob integration classification — schema + accessors")
class AgentJobIntegrationClassificationTest extends BaseUnitTest {

    @Test
    @DisplayName("integrationKind getter/setter round-trips and starts null")
    void integrationKindGetterSetterRoundtrips() {
        AgentJob job = new AgentJob();
        assertThat(job.getIntegrationKind()).isNull();

        job.setIntegrationKind(IntegrationKind.GITHUB);
        assertThat(job.getIntegrationKind()).isEqualTo(IntegrationKind.GITHUB);

        job.setIntegrationKind(IntegrationKind.GITLAB);
        assertThat(job.getIntegrationKind()).isEqualTo(IntegrationKind.GITLAB);

        job.setIntegrationKind(null);
        assertThat(job.getIntegrationKind()).isNull();
    }

    @Test
    @DisplayName("subjectClass getter/setter round-trips and starts null")
    void subjectClassGetterSetterRoundtrips() {
        AgentJob job = new AgentJob();
        assertThat(job.getSubjectClass()).isNull();

        job.setSubjectClass(SubjectClass.PULL_REQUEST);
        assertThat(job.getSubjectClass()).isEqualTo(SubjectClass.PULL_REQUEST);

        job.setSubjectClass(SubjectClass.ISSUE);
        assertThat(job.getSubjectClass()).isEqualTo(SubjectClass.ISSUE);

        job.setSubjectClass(null);
        assertThat(job.getSubjectClass()).isNull();
    }

    @Test
    @DisplayName("integrationKind is mapped as @Enumerated STRING with column name + length")
    void integrationKindHasCorrectJpaMapping() throws NoSuchFieldException {
        Field field = AgentJob.class.getDeclaredField("integrationKind");

        Column column = field.getAnnotation(Column.class);
        assertThat(column).as("@Column on integrationKind").isNotNull();
        assertThat(column.name()).isEqualTo("integration_kind");
        assertThat(column.length()).isEqualTo(48);

        Enumerated enumerated = field.getAnnotation(Enumerated.class);
        assertThat(enumerated).as("@Enumerated on integrationKind").isNotNull();
        assertThat(enumerated.value().name()).isEqualTo("STRING");
    }

    @Test
    @DisplayName("subjectClass is mapped as @Enumerated STRING with column name + length")
    void subjectClassHasCorrectJpaMapping() throws NoSuchFieldException {
        Field field = AgentJob.class.getDeclaredField("subjectClass");

        Column column = field.getAnnotation(Column.class);
        assertThat(column).as("@Column on subjectClass").isNotNull();
        assertThat(column.name()).isEqualTo("subject_class");
        assertThat(column.length()).isEqualTo(48);

        Enumerated enumerated = field.getAnnotation(Enumerated.class);
        assertThat(enumerated).as("@Enumerated on subjectClass").isNotNull();
        assertThat(enumerated.value().name()).isEqualTo("STRING");
    }

    @Test
    @DisplayName("classification fields accept independent (kind, subject) combinations")
    void classificationFieldsAreIndependent() {
        AgentJob job = new AgentJob();
        job.setIntegrationKind(IntegrationKind.SLACK);
        job.setSubjectClass(SubjectClass.SLACK_MESSAGE_THREAD);

        assertThat(job.getIntegrationKind()).isEqualTo(IntegrationKind.SLACK);
        assertThat(job.getSubjectClass()).isEqualTo(SubjectClass.SLACK_MESSAGE_THREAD);

        // Cross-axis values are allowed at the entity level — validation is the caller's job.
        job.setSubjectClass(SubjectClass.PULL_REQUEST);
        assertThat(job.getSubjectClass()).isEqualTo(SubjectClass.PULL_REQUEST);
        assertThat(job.getIntegrationKind()).isEqualTo(IntegrationKind.SLACK);
    }
}
