package de.tum.cit.aet.hephaestus.architecture;

import static de.tum.cit.aet.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
    protected static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);

    /** All classes including tests. Used for test architecture rules. */
    protected static final JavaClasses classesWithTests = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);
}
