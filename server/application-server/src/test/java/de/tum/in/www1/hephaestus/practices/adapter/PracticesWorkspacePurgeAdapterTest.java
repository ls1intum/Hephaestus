package de.tum.in.www1.hephaestus.practices.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("PracticesWorkspacePurgeAdapter")
class PracticesWorkspacePurgeAdapterTest extends BaseUnitTest {

    @Mock
    private PracticeFindingRepository practiceFindingRepository;

    @Mock
    private PracticeRepository practiceRepository;

    private PracticesWorkspacePurgeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PracticesWorkspacePurgeAdapter(practiceFindingRepository, practiceRepository);
    }

    @Test
    @DisplayName("deletes both findings and practices for workspace")
    void deleteWorkspaceData_deletesFindingsAndPractices() {
        // Given
        Long workspaceId = 789L;

        // When
        adapter.deleteWorkspaceData(workspaceId);

        // Then — both deletes called (explicit finding deletion is defense-in-depth; CASCADE also handles it)
        verify(practiceFindingRepository).deleteAllByPracticeWorkspaceId(workspaceId);
        verify(practiceRepository).deleteAllByWorkspaceId(workspaceId);
    }

    @Test
    @DisplayName("runs before default-order contributors")
    void getOrder_returnsNegativeValue() {
        // The adapter should run early, before other contributors
        assertThat(adapter.getOrder()).isLessThan(0);
    }
}
