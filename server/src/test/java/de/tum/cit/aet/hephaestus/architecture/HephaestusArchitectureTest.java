package de.tum.cit.aet.hephaestus.architecture;

import static de.tum.cit.aet.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

/**
 * Base class for all Hephaestus architecture tests.
 *
 * <p>Provides shared class loading infrastructure to avoid duplicate imports
 * across test files. All architecture tests should extend this class.
 *
 * <p><b>Why a base class?</b>
 * <ul>
 *   <li>Class import is expensive (~3-5 seconds) - do it once</li>
 *   <li>Consistent import options across all tests</li>
 *   <li>Single place to add new import exclusions</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * class MyArchitectureTest extends HephaestusArchitectureTest {
 *     @Test
 *     void myRule() {
 *         // Use 'classes' for production code only
 *         myRule.check(classes);
 *
 *         // Use 'classesWithTests' when testing test architecture
 *         testRule.check(classesWithTests);
 *     }
 * }
 * }</pre>
 *
 * @see ArchitectureTestConstants for thresholds and package patterns
 */
@Tag("architecture")
public abstract class HephaestusArchitectureTest {

    /** Production classes only (excludes tests). Used for most architecture rules. */
    protected static JavaClasses classes;

    /** All classes including tests. Used for test architecture rules. */
    protected static JavaClasses classesWithTests;

    /** Flag to ensure we only load classes once across all test classes. */
    private static volatile boolean initialized = false;

    /**
     * Excludes the generated Outline vendor wire-models from every architecture rule.
     *
     * <p>These classes are emitted by {@code openapi-generator-maven-plugin} from Outline's OpenAPI
     * spec into {@code integration.outline.client.model} (compiled under
     * {@code target/classes/.../integration/outline/client/model}, sources gitignored). The generator's
     * {@code java} generator stamps {@code @jakarta.annotation.Nonnull}/{@code @Nullable} on every model
     * field, which no clean generator flag suppresses. They are not hand-written code and are never part
     * of the SpringDoc-exposed API surface (never in {@code openapi.yaml}), so rules about nullability
     * annotation consistency, own-package imports, code quality and module boundaries do not apply to
     * them — the rules exist to protect hand-authored code and correct SpringDoc generation.
     *
     * <p>This is the single, central place the exclusion lives (see {@link #initializeClasses()}); it is
     * scoped tightly to the generated {@code client.model} package and leaves hand-written Outline code
     * (client, sync, domain, …) fully governed.
     */
    private static final ImportOption EXCLUDE_GENERATED_OUTLINE_MODELS = (Location location) ->
        !location.contains("integration/outline/client/model");

    /**
     * Initializes shared JavaClasses instances for all architecture tests.
     *
     * <p>Uses double-checked locking to ensure thread-safe lazy initialization.
     * The class import is expensive (~3-5 seconds), so we cache the result
     * across all test classes that extend this base.
     */
    @BeforeAll
    static void initializeClasses() {
        if (!initialized) {
            synchronized (HephaestusArchitectureTest.class) {
                if (!initialized) {
                    classes = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                        .withImportOption(EXCLUDE_GENERATED_OUTLINE_MODELS)
                        .importPackages(BASE_PACKAGE);

                    classesWithTests = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                        .withImportOption(EXCLUDE_GENERATED_OUTLINE_MODELS)
                        .importPackages(BASE_PACKAGE);

                    initialized = true;
                }
            }
        }
    }
}
