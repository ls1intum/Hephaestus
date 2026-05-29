package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static de.tum.cit.aet.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Null Annotation Consistency Tests.
 *
 * <p>Enforces consistent usage of {@code org.jspecify.annotations.NonNull}
 * across the codebase. Alternative null annotations from Lombok or Jakarta
 * must not be used to keep the annotation style uniform and ensure correct
 * OpenAPI spec generation by SpringDoc.
 *
 * <p><strong>Allowed annotations:</strong>
 * <ul>
 *   <li>{@code org.jspecify.annotations.NonNull} — standard non-null marker</li>
 *   <li>{@code org.jspecify.annotations.Nullable} — standard nullable marker</li>
 *   <li>{@code jakarta.validation.constraints.NotNull} — Bean Validation (different concern)</li>
 * </ul>
 *
 * <p><strong>Prohibited annotations:</strong>
 * <ul>
 *   <li>{@code lombok.NonNull} — generates null-check code, not equivalent</li>
 *   <li>{@code jakarta.annotation.Nonnull} — inconsistent with project convention</li>
 *   <li>{@code org.springframework.lang.NonNull} / {@code Nullable} — deprecated in Spring 7;
 *       migrated to JSpecify</li>
 * </ul>
 *
 * <p>SpringDoc/swagger-core does not natively read JSpecify for OpenAPI {@code required}, so
 * {@code JSpecifyRequiredModelConverter} bridges {@code @NonNull} → schema {@code required}.
 *
 * @see <a href="https://jspecify.dev/docs/api/org/jspecify/annotations/NonNull.html">JSpecify @NonNull</a>
 */
class NullAnnotationConsistencyTest extends HephaestusArchitectureTest {

    /**
     * No classes should use {@code lombok.NonNull}.
     *
     * <p>{@code lombok.NonNull} generates null-check code in setters and constructors,
     * which is a different concern from documenting nullability. Use
     * {@code org.jspecify.annotations.NonNull} for consistent nullability semantics
     * and correct OpenAPI spec generation.
     */
    @Test
    void noLombokNonNull() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("lombok.NonNull")
            .because(
                "Use org.jspecify.annotations.NonNull instead of lombok.NonNull " +
                    "for consistent nullability semantics and OpenAPI spec generation"
            );
        rule.check(classes);
    }

    /**
     * No classes should use {@code jakarta.annotation.Nonnull}.
     *
     * <p>This annotation is not recognized by SpringDoc for OpenAPI generation
     * and introduces inconsistency. Use {@code org.jspecify.annotations.NonNull}
     * instead.
     */
    @Test
    void noJakartaAnnotationNonnull() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("jakarta.annotation.Nonnull")
            .because(
                "Use org.jspecify.annotations.NonNull instead of jakarta.annotation.Nonnull " +
                    "for consistent nullability semantics and OpenAPI spec generation"
            );
        rule.check(classes);
    }

    /**
     * No classes should use the deprecated {@code org.springframework.lang.NonNull} /
     * {@code Nullable}. Spring Framework 7 deprecated them in favour of JSpecify; the codebase
     * has fully migrated, and this rule prevents regressions.
     */
    @Test
    void noSpringLangNullness() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.lang.NonNull")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.lang.Nullable")
            .because(
                "org.springframework.lang.NonNull/Nullable are deprecated in Spring 7 — use org.jspecify.annotations.*"
            );
        rule.check(classes);
    }
}
