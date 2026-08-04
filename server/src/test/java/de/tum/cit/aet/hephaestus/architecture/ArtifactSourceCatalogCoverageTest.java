package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceSource;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceContract;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The catalog and the collectors must describe the same world.
 *
 * <p>Authorization, privacy class, and retention are all looked up by source kind, so a kind a
 * collector emits but the catalog omits is evidence that no governance decision covers. In the other
 * direction, a catalog kind nothing collects is a promise the runtime cannot keep: a practice may
 * require it and then abstain on every run, which reads as a broken product rather than a missing
 * collector.
 */
@Tag("architecture")
class ArtifactSourceCatalogCoverageTest {

    private static final Set<SourceKind> CATALOG_KINDS = new ClasspathArtifactSourceCatalogRegistry(
        JsonMapper.builder().build(),
        Clock.systemUTC(),
        ""
    )
        .current()
        .sources()
        .stream()
        .map(ArtifactSourceContract::kind)
        .collect(Collectors.toUnmodifiableSet());

    private static final Set<SourceKind> COLLECTED_KINDS = collectedKinds();

    private static Set<SourceKind> collectedKinds() {
        JavaClasses providers = new ClassFileImporter().importPackages(
            "de.tum.cit.aet.hephaestus.agent.context.providers"
        );
        return providers
            .stream()
            .filter(javaClass -> javaClass.isAssignableTo(EvidenceSource.class))
            .map(JavaClass::reflect)
            .filter(type -> !type.isInterface() && !Modifier.isAbstract(type.getModifiers()))
            .flatMap(type -> declaredKinds(type).stream())
            .collect(Collectors.toUnmodifiableSet());
    }

    /** Reads the constants a collector declares, without building its bean graph. */
    private static Set<SourceKind> declaredKinds(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()))
            .filter(field -> SourceKind.class.equals(field.getType()))
            .map(ArtifactSourceCatalogCoverageTest::read)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static SourceKind read(Field field) {
        try {
            field.setAccessible(true);
            return (SourceKind) field.get(null);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Cannot read " + field, exception);
        }
    }

    @Test
    @DisplayName("every collected source kind is governed by the catalog")
    void everyCollectedKindIsGoverned() {
        assertThat(COLLECTED_KINDS)
            .as("a kind the catalog omits carries no privacy class and no use decision")
            .isSubsetOf(CATALOG_KINDS);
    }

    @Test
    @DisplayName("every catalog source kind has a collector")
    void everyCatalogKindIsCollectable() {
        assertThat(CATALOG_KINDS)
            .as("a catalog kind nothing collects can only ever be reported absent")
            .isSubsetOf(COLLECTED_KINDS);
    }
}
