package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationFamily;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-level guard rails for the capability-gating fields on {@link Practice}. Focuses on:
 *
 * <ul>
 *   <li>Default-collection initialization (so JSONB NOT NULL is satisfied on fresh inserts)
 *   <li>JPA mapping shape (column names, JSON type code, enum-string for family)
 *   <li>{@link Practice#getRequiredCapabilitySet()} forward-compat semantics — unknown
 *       capability names are skipped instead of crashing the load.
 * </ul>
 */
@DisplayName("Practice capability gating fields")
class PracticeCapabilityGatingTest extends BaseUnitTest {

    @Test
    @DisplayName("requiredCapabilities and requiredAspects default to empty mutable sets")
    void capabilityCollectionsDefaultEmpty() {
        Practice practice = new Practice();

        assertThat(practice.getRequiredCapabilities()).isNotNull().isEmpty();
        assertThat(practice.getRequiredAspects()).isNotNull().isEmpty();
        // Defensive — the field is mutable so callers can add to it in place.
        practice.getRequiredCapabilities().add("WEBHOOK_INGEST");
        assertThat(practice.getRequiredCapabilities()).containsExactly("WEBHOOK_INGEST");
    }

    @Test
    @DisplayName("requiredFamily defaults null and round-trips")
    void requiredFamilyRoundTrips() {
        Practice practice = new Practice();
        assertThat(practice.getRequiredFamily()).isNull();

        practice.setRequiredFamily(IntegrationFamily.SCM);
        assertThat(practice.getRequiredFamily()).isEqualTo(IntegrationFamily.SCM);

        practice.setRequiredFamily(null);
        assertThat(practice.getRequiredFamily()).isNull();
    }

    @Test
    @DisplayName("getRequiredCapabilitySet resolves known names")
    void requiredCapabilitySetResolvesKnownNames() {
        Practice practice = new Practice();
        practice.setRequiredCapabilities(new LinkedHashSet<>(Set.of("INLINE_FINDINGS", "FEEDBACK_DELIVERY")));

        Set<Capability> resolved = practice.getRequiredCapabilitySet();
        assertThat(resolved).containsExactlyInAnyOrder(Capability.INLINE_FINDINGS, Capability.FEEDBACK_DELIVERY);
    }

    @Test
    @DisplayName("getRequiredCapabilitySet drops unknown names without throwing")
    void requiredCapabilitySetDropsUnknownNames() {
        Practice practice = new Practice();
        practice.setRequiredCapabilities(
            new LinkedHashSet<>(Set.of("INLINE_FINDINGS", "CAPABILITY_THAT_WAS_REMOVED", "FEEDBACK_DELIVERY"))
        );

        Set<Capability> resolved = practice.getRequiredCapabilitySet();
        assertThat(resolved).containsExactlyInAnyOrder(Capability.INLINE_FINDINGS, Capability.FEEDBACK_DELIVERY);
    }

    @Test
    @DisplayName("getRequiredCapabilitySet tolerates null / blank entries")
    void requiredCapabilitySetTolerantOfBlanks() {
        Practice practice = new Practice();
        LinkedHashSet<String> raw = new LinkedHashSet<>();
        raw.add("INLINE_FINDINGS");
        raw.add("");
        raw.add("  ");
        practice.setRequiredCapabilities(raw);

        assertThat(practice.getRequiredCapabilitySet()).containsExactly(Capability.INLINE_FINDINGS);
    }

    @Test
    @DisplayName("getRequiredCapabilitySet on empty / null collection returns empty")
    void requiredCapabilitySetEmptyWhenNoData() {
        Practice empty = new Practice();
        assertThat(empty.getRequiredCapabilitySet()).isEmpty();

        Practice nulled = new Practice();
        nulled.setRequiredCapabilities(null);
        assertThat(nulled.getRequiredCapabilitySet()).isEmpty();
    }

    @Test
    @DisplayName("required_capabilities is mapped as jsonb NOT NULL via @JdbcTypeCode JSON")
    void requiredCapabilitiesJpaMapping() throws NoSuchFieldException {
        Field field = Practice.class.getDeclaredField("requiredCapabilities");

        Column column = field.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("required_capabilities");
        assertThat(column.columnDefinition()).isEqualTo("jsonb");
        assertThat(column.nullable()).isFalse();

        assertThat(field.getAnnotation(JdbcTypeCode.class)).isNotNull();
    }

    @Test
    @DisplayName("required_aspects is mapped as jsonb NOT NULL via @JdbcTypeCode JSON")
    void requiredAspectsJpaMapping() throws NoSuchFieldException {
        Field field = Practice.class.getDeclaredField("requiredAspects");

        Column column = field.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("required_aspects");
        assertThat(column.columnDefinition()).isEqualTo("jsonb");
        assertThat(column.nullable()).isFalse();

        assertThat(field.getAnnotation(JdbcTypeCode.class)).isNotNull();
    }

    @Test
    @DisplayName("required_family is mapped as @Enumerated STRING with column name + length")
    void requiredFamilyJpaMapping() throws NoSuchFieldException {
        Field field = Practice.class.getDeclaredField("requiredFamily");

        Column column = field.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("required_family");
        assertThat(column.length()).isEqualTo(32);

        Enumerated enumerated = field.getAnnotation(Enumerated.class);
        assertThat(enumerated).isNotNull();
        assertThat(enumerated.value().name()).isEqualTo("STRING");
    }
}
