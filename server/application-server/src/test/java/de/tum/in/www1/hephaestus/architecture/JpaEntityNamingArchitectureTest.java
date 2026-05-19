package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces hygiene of JPA entity naming annotations.
 *
 * <h2>Unnamed {@link UniqueConstraint} is banned</h2>
 *
 * <p>Hibernate's {@code ImplicitNamingStrategy} synthesizes hash-style names
 * (e.g. {@code uknlcwyn2relkgw95s8okgpkqrt}) whenever a {@code @UniqueConstraint}
 * lacks an explicit {@code name}. With {@code ddl-auto: update} in dev these
 * synthesized constraints accumulated <em>in parallel</em> with the
 * Liquibase-managed named constraints, producing duplicate uniques and noisy
 * {@code constraint X does not exist, skipping} warnings on every boot.
 *
 * <p>Once a name is recorded by Liquibase, the JPA-side annotation MUST match it.
 * The rule below catches a fresh occurrence at compile time, before the orphan
 * propagates onto a developer DB.
 */
@DisplayName("JPA entity naming hygiene")
class JpaEntityNamingArchitectureTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("@UniqueConstraint on JPA entities must declare an explicit name")
    void uniqueConstraintsMustBeNamed() {
        ArchRule rule = classes()
            .that()
            .resideInAPackage(BASE_PACKAGE + "..")
            .and()
            .areAnnotatedWith(Table.class)
            .should(haveNamedUniqueConstraintsOnly())
            .because(
                "Unnamed @UniqueConstraint causes Hibernate to synthesize hash-style names that drift from " +
                    "the Liquibase-recorded constraint names, producing duplicate uniques on dev DBs running ddl-auto:update. " +
                    "Always set name=\"uq_<table>_<columns>\" matching the Liquibase changeset."
            );

        rule.check(classes);
    }

    private static ArchCondition<JavaClass> haveNamedUniqueConstraintsOnly() {
        return new ArchCondition<>("only declare @UniqueConstraint with an explicit name") {
            @Override
            public void check(JavaClass entityClass, ConditionEvents events) {
                Table table = entityClass.reflect().getAnnotation(Table.class);
                if (table == null) {
                    return;
                }
                for (UniqueConstraint uc : table.uniqueConstraints()) {
                    if (uc.name() == null || uc.name().isBlank()) {
                        events.add(
                            SimpleConditionEvent.violated(
                                entityClass,
                                String.format(
                                    "%s declares an unnamed @UniqueConstraint(columnNames=%s) — give it an explicit name= matching the Liquibase changeset.",
                                    entityClass.getName(),
                                    java.util.Arrays.toString(uc.columnNames())
                                )
                            )
                        );
                    }
                }
            }
        };
    }
}
