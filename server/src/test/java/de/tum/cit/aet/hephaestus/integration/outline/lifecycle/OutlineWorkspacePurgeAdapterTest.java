package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;

class OutlineWorkspacePurgeAdapterTest extends BaseUnitTest {

    @Mock
    private OutlineDocumentRepository outlineDocumentRepository;

    /** A provider whose {@code ifAvailable} runs its consumer against {@code registrar}, or is a no-op when null. */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<OutlineWebhookRegistrar> providerOf(OutlineWebhookRegistrar registrar) {
        ObjectProvider<OutlineWebhookRegistrar> provider = mock(ObjectProvider.class);
        if (registrar != null) {
            doAnswer(inv -> {
                inv.<Consumer<OutlineWebhookRegistrar>>getArgument(0).accept(registrar);
                return null;
            })
                .when(provider)
                .ifAvailable(any());
        }
        return provider;
    }

    private OutlineWorkspacePurgeAdapter adapter(OutlineWebhookRegistrar registrar) {
        return new OutlineWorkspacePurgeAdapter(outlineDocumentRepository, providerOf(registrar));
    }

    @Test
    void deleteWorkspaceData_deregistersSubscriptionThenBulkDeletesDocuments() {
        OutlineWebhookRegistrar registrar = mock(OutlineWebhookRegistrar.class);

        adapter(registrar).deleteWorkspaceData(789L);

        verify(registrar).deregister(789L);
        verify(outlineDocumentRepository).deleteByWorkspaceId(789L);
    }

    @Test
    @DisplayName("still drops documents when the (conditional) registrar bean is absent")
    void deleteWorkspaceData_dropsDocumentsWhenRegistrarAbsent() {
        adapter(null).deleteWorkspaceData(42L);

        verify(outlineDocumentRepository).deleteByWorkspaceId(42L);
    }

    @Test
    @DisplayName("a deregistration failure never blocks the document erasure")
    void deleteWorkspaceData_swallowsDeregisterFailure() {
        OutlineWebhookRegistrar registrar = mock(OutlineWebhookRegistrar.class);
        doThrow(new RuntimeException("boom")).when(registrar).deregister(7L);

        adapter(registrar).deleteWorkspaceData(7L);

        verify(outlineDocumentRepository).deleteByWorkspaceId(7L);
    }

    @Test
    @DisplayName("runs before the connection purge contributor (-100) so content drops while the connection is intact")
    void getOrder_runsBeforeConnectionTeardown() {
        assertThat(adapter(null).getOrder()).isLessThan(-100);
        verifyNoInteractions(outlineDocumentRepository);
    }
}
