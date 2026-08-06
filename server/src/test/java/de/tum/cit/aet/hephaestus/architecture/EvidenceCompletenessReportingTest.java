package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceSource;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceContract;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * A source the catalog lets claim COMPLETE must decide its own completeness.
 *
 * <p>{@link EvidenceSource#capture} has a default that reports no completeness at all, leaving the
 * manifest to fall back on what the catalog permits. For a source that may be COMPLETE, that fallback
 * says every capture holds everything — so a collector that pages, caps, or otherwise truncates
 * describes truncated evidence as complete, and a practice requiring completeness runs on it. Only the
 * collector knows it truncated, so only the collector can say.
 *
 * <p>A source the catalog forbids from claiming COMPLETE needs no override: the fallback cannot
 * overclaim, because the catalog already rules the claim out.
 */
@Tag("architecture")
class EvidenceCompletenessReportingTest {

    private static final ArtifactSourceContract[] CATALOG = new ClasspathArtifactSourceCatalogRegistry(
        JsonMapper.builder().build(),
        Clock.systemUTC()
    )
        .current()
        .sources()
        .toArray(ArtifactSourceContract[]::new);

    @Test
    @DisplayName("a collector for a source that may be COMPLETE reports completeness itself")
    void collectorsThatMayClaimCompleteOverrideCapture() {
        Set<SourceKind> mayClaimComplete = java.util.Arrays.stream(CATALOG)
            .filter(contract -> contract.completenessPolicy().supportsComplete())
            .map(ArtifactSourceContract::kind)
            .collect(Collectors.toSet());

        List<String> silent = new ArrayList<>();
        for (Class<?> collector : collectors()) {
            if (declaredKinds(collector).stream().noneMatch(mayClaimComplete::contains)) {
                continue;
            }
            if (!overridesCapture(collector)) {
                silent.add(collector.getSimpleName());
            }
        }

        assertThat(silent)
            .as("these collectors let the catalog answer for them, so a truncated capture reads as complete")
            .isEmpty();
    }

    private static Set<Class<?>> collectors() {
        return new ClassFileImporter()
            .importPackages("de.tum.cit.aet.hephaestus.agent.context.providers")
            .stream()
            .filter(javaClass -> javaClass.isAssignableTo(EvidenceSource.class))
            .map(JavaClass::reflect)
            .filter(type -> !type.isInterface() && !Modifier.isAbstract(type.getModifiers()))
            .collect(Collectors.toSet());
    }

    /** The {@code SourceKind} constants a collector declares, read without constructing it. */
    private static Set<SourceKind> declaredKinds(Class<?> collector) {
        Set<SourceKind> kinds = new java.util.HashSet<>();
        for (Field field : collector.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != SourceKind.class) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(null);
                if (value instanceof SourceKind kind) {
                    kinds.add(kind);
                }
            } catch (IllegalAccessException ignored) {
                // A collector that hides its kinds cannot be checked here; the catalog-coverage test
                // still requires every catalog kind to be collected.
            }
        }
        return kinds;
    }

    private static boolean overridesCapture(Class<?> collector) {
        try {
            collector.getDeclaredMethod(
                "capture",
                de.tum.cit.aet.hephaestus.agent.context.ContextRequest.class,
                Set.class
            );
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }
}
