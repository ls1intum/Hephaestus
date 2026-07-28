package de.tum.cit.aet.hephaestus.integration.outline.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class OutlineDocumentSelectorTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;

    @Mock
    private OutlineDocumentRepository documentRepository;

    private OutlineDocumentSelector selector;

    @BeforeEach
    void setUp() {
        selector = new OutlineDocumentSelector(documentRepository);
    }

    @Test
    void blankOrNullQueryShortCircuitsWithoutTouchingTheRepository() {
        assertThat(selector.select(WORKSPACE_ID, null, 10)).isEmpty();
        assertThat(selector.select(WORKSPACE_ID, "   ", 10)).isEmpty();
        verifyNoInteractions(documentRepository);
    }

    @Test
    void nonPositiveLimitShortCircuitsWithoutTouchingTheRepository() {
        assertThat(selector.select(WORKSPACE_ID, "deploy OR rollback", 0)).isEmpty();
        verifyNoInteractions(documentRepository);
    }

    @Test
    void delegatesTrimmedQueryAndClampsTheLimit() {
        OutlineDocument hit = new OutlineDocument();
        when(
            documentRepository.searchByRelevance(WORKSPACE_ID, "deploy OR rollback", OutlineDocumentSelector.MAX_LIMIT)
        ).thenReturn(List.of(hit));

        List<OutlineDocument> result = selector.select(WORKSPACE_ID, "  deploy OR rollback  ", 500);

        assertThat(result).containsExactly(hit);
    }
}
