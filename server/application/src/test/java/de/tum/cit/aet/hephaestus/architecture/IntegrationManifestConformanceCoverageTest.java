package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A conformance suite nobody is obliged to join is a suite the next integration skips: a new manifest is
 * just another {@code @Component}, and nothing would notice a missing subclass. This closes that: every
 * shipped {@code IntegrationManifest} must have a concrete {@code IntegrationManifestContractTest}
 * subclass, and the fixture manifest must be in the suite too — a fixture that is not itself held to the
 * contract is decoration.
 */
@DisplayName("every integration manifest is held to the conformance contract")
class IntegrationManifestConformanceCoverageTest extends HephaestusArchitectureTest {

    private static final String MANIFEST = "de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest";
    private static final String CONTRACT_TEST =
            "de.tum.cit.aet.hephaestus.integration.core.conformance.IntegrationManifestContractTest";

    @Test
    void everyShippedManifestHasAContractTest() {
        Set<String> manifests = shippedManifests();
        Set<String> tested = contractTestedManifestSimpleNames();

        Set<String> untested = new TreeSet<>(manifests);
        untested.removeIf(manifest -> tested.contains(expectedTestName(manifest)));

        assertThat(untested)
                .as(
                        "Add a %s subclass named <Manifest>ContractTest. Boot validation only ever sees the "
                                + "manifests the running configuration enabled; the suite sees all of them.",
                        CONTRACT_TEST)
                .isEmpty();
    }

    @Test
    void theFixtureManifestIsHeldToTheSameContract() {
        assertThat(contractTestedManifestSimpleNames())
                .as("the synthetic integration must pass the suite it exists to validate")
                .contains("FixtureManifestContractTest");
    }

    @Test
    void thereIsSomethingToCheck() {
        // Both assertions above pass vacuously on an empty import; this fails first if that ever happens.
        assertThat(shippedManifests()).isNotEmpty();
    }

    private static Set<String> shippedManifests() {
        return classes.stream()
                .filter(javaClass ->
                        !javaClass.isInterface() && !javaClass.getModifiers().contains(JavaModifier.ABSTRACT))
                .filter(javaClass -> javaClass.getAllRawInterfaces().stream()
                        .anyMatch(i -> i.getName().equals(MANIFEST)))
                .map(JavaClass::getSimpleName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> contractTestedManifestSimpleNames() {
        return classesWithTests.stream()
                .filter(javaClass -> javaClass.getAllRawSuperclasses().stream()
                        .anyMatch(s -> s.getName().equals(CONTRACT_TEST)))
                .map(JavaClass::getSimpleName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String expectedTestName(String manifestSimpleName) {
        return manifestSimpleName + "ContractTest";
    }
}
