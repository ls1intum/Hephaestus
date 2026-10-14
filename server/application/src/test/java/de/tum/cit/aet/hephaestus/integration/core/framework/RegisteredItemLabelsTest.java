package de.tum.cit.aet.hephaestus.integration.core.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegisteredItemLabelsTest extends BaseUnitTest {
    private static final ItemKind KIND = new ItemKind("scm.issue");
    private static final ItemRef ITEM = new ItemRef(1, 2, KIND, 3L, null, null);

    @Test
    void rejectsForeignWorkspaceItemsBeforeConsultingAnAdapter() {
        ItemSourceRegistry sources = mock(ItemSourceRegistry.class);
        assertThatThrownBy(() -> new RegisteredItemLabels(sources).labels(9, List.of(ITEM)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(sources);
    }

    @Test
    void keepsMissingLabelsAbsent() {
        ItemSource source = mock(ItemSource.class);
        ItemSourceRegistry sources = registry(source);
        when(source.labels(eq(1L), anyCollection())).thenReturn(Map.of());
        assertThat(new RegisteredItemLabels(sources).labels(1, List.of(ITEM))).isEmpty();
    }

    @Test
    void rejectsAnAdaptersUnrequestedLabel() {
        ItemSource source = mock(ItemSource.class);
        ItemSourceRegistry sources = registry(source);
        when(source.labels(eq(1L), anyCollection()))
                .thenReturn(Map.of(99L, new ItemLabel(99, "Other", "issue", null, null, null, null)));
        assertThatThrownBy(() -> new RegisteredItemLabels(sources).labels(1, List.of(ITEM)))
                .isInstanceOf(IllegalStateException.class);
    }

    private ItemSourceRegistry registry(ItemSource source) {
        ItemSourceRegistry sources = mock(ItemSourceRegistry.class);
        when(sources.sourceFor(ITEM)).thenReturn(source);
        return sources;
    }
}
