package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import de.tum.cit.aet.hephaestus.Application;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@Tag("architecture")
class ItemEventBoundaryTest {
    @Test
    void shouldExportLedgerViewsWithoutExportingPersistence() {
        var modules = ApplicationModules.of(
                Application.class,
                resideInAnyPackage(
                        "..integration.scm.github.graphql.model..",
                        "..integration.scm.gitlab.graphql.model..",
                        "..integration.outline.client.model.."));
        var api = modules.getModuleByType(ItemEventService.class)
                .orElseThrow()
                .getNamedInterfaces()
                .getByName("item-events")
                .orElseThrow();

        assertThat(api)
                .extracting(JavaClass::getName)
                .contains(
                        ItemEventService.class.getName(),
                        ItemEventRecorded.class.getName(),
                        ItemEventView.class.getName())
                .doesNotContain(
                        ItemEvent.class.getName(),
                        ItemEventRepository.class.getName(),
                        ItemKindConverter.class.getName(),
                        PendingItemEventReaper.class.getName());
    }
}
