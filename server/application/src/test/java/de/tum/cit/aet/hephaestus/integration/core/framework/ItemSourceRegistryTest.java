package de.tum.cit.aet.hephaestus.integration.core.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemSourceRegistryTest extends BaseUnitTest {
    private static final ItemKind KIND = new ItemKind("scm.issue");
    private static final ItemRef ITEM = new ItemRef(1, 2, KIND, 3L, null, null);

    @Test
    void rejectsAnUnsupportedItemInsteadOfChoosingAnotherSourceOfTheSameKind() {
        ItemSource source = source(false);
        assertThatThrownBy(() -> new ItemSourceRegistry(List.of(source)).sourceFor(ITEM))
                .isInstanceOf(ItemCaptureException.class);
    }

    @Test
    void rejectsAmbiguousOwnership() {
        assertThatThrownBy(() -> new ItemSourceRegistry(List.of(source(true), source(true))).sourceFor(ITEM))
                .isInstanceOf(ItemCaptureException.class);
    }

    @Test
    void selectsTheOnlySourceThatSupportsTheExactItem() {
        ItemSource source = source(true);
        assertThat(new ItemSourceRegistry(List.of(source)).sourceFor(ITEM)).isSameAs(source);
    }

    private ItemSource source(boolean supports) {
        ItemSource source = mock(ItemSource.class);
        when(source.kind()).thenReturn(KIND);
        when(source.supports(ITEM)).thenReturn(supports);
        return source;
    }
}
