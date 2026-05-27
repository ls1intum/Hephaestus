package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static de.tum.cit.aet.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Null Annotation Consistency Tests.
 *
 * <p>Enforces consistent usage of {@code org.springframework.lang.NonNull}
 * across the codebase. Alternative null annotations from Lombok or Jakarta
 * must not be used to keep the annotation style uniform and ensure correct
 * OpenAPI spec generation by SpringDoc.
 *
 * <p><strong>Allowed annotations:</strong>
 * <ul>
 *   <li>{@code org.springframework.lang.NonNull} — standard non-null marker</li>
 *   <li>{@code org.springframework.lang.Nullable} — standard nullable marker</li>
 *   <li>{@code jakarta.validation.constraints.NotNull} — Bean Validation (different concern)</li>
 * </ul>
 *
 * <p><strong>Prohibited annotations:</strong>
 * <ul>
 *   <li>{@code lombok.NonNull} — generates null-check code, not equivalent</li>
 *   <li>{@code jakarta.annotation.Nonnull} — inconsistent with project convention</li>
 * </ul>
 *
 * @see <a href="https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/lang/NonNull.html">
 *     Spring @NonNull</a>
 */
class NullAnnotationConsistencyTest extends HephaestusArchitectureTest {

    /**
     * No classes should use {@code lombok.NonNull}.
     *
     * <p>{@code lombok.NonNull} generates null-check code in setters and constructors,
     * which is a different concern from documenting nullability. Use
     * {@code org.springframework.lang.NonNull} for consistent nullability semantics
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
                "Use org.springframework.lang.NonNull instead of lombok.NonNull " +
                    "for consistent nullability semantics and OpenAPI spec generation"
            );
        rule.check(classes);
    }

    /**
     * No classes should use {@code jakarta.annotation.Nonnull}.
     *
     * <p>This annotation is not recognized by SpringDoc for OpenAPI generation
     * and introduces inconsistency. Use {@code org.springframework.lang.NonNull}
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
                "Use org.springframework.lang.NonNull instead of jakarta.annotation.Nonnull " +
                    "for consistent nullability semantics and OpenAPI spec generation"
            );
        rule.check(classes);
    }
}
