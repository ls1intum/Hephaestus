package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static de.tum.cit.aet.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

/** Bans unnamed {@code @UniqueConstraint} on JPA entities so Hibernate's implicit naming can't drift from Liquibase. */
class JpaEntityNamingArchitectureTest extends HephaestusArchitectureTest {

    @Test
    void uniqueConstraintsMustBeNamed() {
        ArchRule rule = classes()
            .that()
            .resideInAPackage(BASE_PACKAGE + "..")
            .and()
            .areAnnotatedWith(Table.class)
            .should(haveNamedUniqueConstraintsOnly())
            .because("explicit name= required so Hibernate's implicit naming can't drift from Liquibase");

        rule.check(classes);
    }

    private static ArchCondition<JavaClass> haveNamedUniqueConstraintsOnly() {
        return new ArchCondition<>("only declare @UniqueConstraint with an explicit name") {
            @Override
            public void check(JavaClass entityClass, ConditionEvents events) {
                Table table = entityClass.getAnnotationOfType(Table.class);
                for (UniqueConstraint uc : table.uniqueConstraints()) {
                    if (uc.name().isBlank()) {
                        events.add(
                            SimpleConditionEvent.violated(
                                entityClass,
                                "%s declares an unnamed @UniqueConstraint(columnNames=%s); add name=\"uq_<table>_<cols>\"".formatted(
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
