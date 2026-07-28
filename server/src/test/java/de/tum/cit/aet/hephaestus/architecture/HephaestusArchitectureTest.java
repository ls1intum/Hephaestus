package de.tum.cit.aet.hephaestus.architecture;

import static de.tum.cit.aet.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
     * Initializes shared JavaClasses instances for all architecture tests.
     *
     * <p>Uses double-checked locking to ensure thread-safe lazy initialization.
     * The class import is expensive (~3-5 seconds), so we cache the result
     * across all test classes that extend this base.
     *
     * <p><b>Generated Outline vendor models.</b> The imported set deliberately includes the
     * {@code openapi-generator}-emitted models in {@code integration.outline.client.model} (Jackson-2
     * annotations, {@code @jakarta.annotation.Nonnull}, plain wire POJOs). They are <em>not</em> globally
     * excluded here: a blanket exclusion would also blind {@link OutlineApiDtoIsolationTest}, whose whole
     * job is to keep those exact classes on the extract seam, so the boundary guard would silently match
     * zero classes and pass vacuously. Each rule that legitimately does not apply to generated wire models
     * (nullability-annotation consistency, Jackson-namespace, external-vendor-import allowlist, …) instead
     * exempts the {@code ..integration.outline.client.model..} package on its own {@code .that()} subject
     * set, so the exemption is visible and auditable at the rule that needs it rather than hidden here.
     */
    @BeforeAll
    static void initializeClasses() {
        if (!initialized) {
            synchronized (HephaestusArchitectureTest.class) {
                if (!initialized) {
                    classes = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                        .importPackages(BASE_PACKAGE);

                    classesWithTests = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                        .importPackages(BASE_PACKAGE);

                    initialized = true;
                }
            }
        }
    }
}
