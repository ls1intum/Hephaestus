package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventCoverage;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventDefinition;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.practices.PracticeItemOptions;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class PracticeItemEventCoverageTest {
    private final ItemDescriptor descriptor = mock(ItemDescriptor.class);
    private final ItemDescriptorRegistry catalog = mock(ItemDescriptorRegistry.class);
    private final ItemEventCoverage coverage = mock(ItemEventCoverage.class);
    private final PracticeItemEventCoverage policy = new PracticeItemEventCoverage(
            coverage, new PracticeItemOptions(catalog), mock(PracticeRepository.class), new JsonMapper());

    @BeforeEach
    void vocabulary() {
        when(catalog.all()).thenReturn(List.of(descriptor));
    }

    @Test
    void refusesAnAuthorableIngestedEventThatNoCompiledIntegrationProduces() {
        var name = new ItemEventName("scm.pull_request.opened");
        var definition = mock(ItemEventDefinition.class);
        when(definition.name()).thenReturn(name);
        when(definition.bindableForAuthoring()).thenReturn(true);
        when(definition.producedBy()).thenReturn(Set.of(mock(EventTypeKey.class)));
        when(descriptor.events()).thenReturn(List.of(definition));
        when(coverage.compiledCoverage()).thenReturn(Set.of());
        assertThatThrownBy(policy::validateAuthoringVocabulary)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name.value());
    }

    @Test
    void acceptsADeclaredInternalEventWithoutAnIngestionProducer() {
        var definition = mock(ItemEventDefinition.class);
        when(definition.name()).thenReturn(new ItemEventName("chat.thread.settled"));
        when(definition.bindableForAuthoring()).thenReturn(true);
        when(definition.producedBy()).thenReturn(Set.of());
        when(descriptor.events()).thenReturn(List.of(definition));
        when(coverage.compiledCoverage()).thenReturn(Set.of());
        assertThatCode(policy::validateAuthoringVocabulary).doesNotThrowAnyException();
    }
}
