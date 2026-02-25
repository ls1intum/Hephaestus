package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("Null Annotation Consistency")
class NullAnnotationConsistencyTest extends HephaestusArchitectureTest {

    /**
     * No classes should use {@code lombok.NonNull}.
     *
     * <p>{@code lombok.NonNull} generates null-check code in setters and constructors,
     * which is a different concern from documenting nullability. Use
     * {@code org.springframework.lang.NonNull} for consistent nullability semantics
     * and correct OpenAPI spec generation.
     *
     * <p><strong>Exclusion:</strong> The generated intelligence-service client
     * ({@code ..intelligenceservice..}) is excluded because it is auto-generated
     * code that we do not control.
     */
    @Test
    @DisplayName("No lombok.NonNull usage — use org.springframework.lang.NonNull")
    void noLombokNonNull() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + "..")
            .and()
            .resideOutsideOfPackage("..intelligenceservice..")
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
     *
     * <p><strong>Exclusion:</strong> The generated intelligence-service client
     * ({@code ..intelligenceservice..}) is excluded because it is auto-generated
     * code that we do not control.
     */
    @Test
    @DisplayName("No jakarta.annotation.Nonnull usage — use org.springframework.lang.NonNull")
    void noJakartaAnnotationNonnull() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + "..")
            .and()
            .resideOutsideOfPackage("..intelligenceservice..")
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
