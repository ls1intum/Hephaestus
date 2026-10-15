package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ScmCapturePreparationTest extends BaseUnitTest {
    private final ReviewRepositoryPreparer preparer = mock(ReviewRepositoryPreparer.class);
    private final ScmCaptureContext context = ScmCaptureFixtures.context(new JsonMapper().createObjectNode(), 1, false);

    @Test
    void shouldReportAnUnavailablePinnedRangeWithoutUsingAnotherVersion() {
        when(preparer.prepare(context))
                .thenThrow(new ItemCaptureException(ItemCaptureCode.VERSION_UNAVAILABLE, "range"));

        assertThat(context.preparation().prepare(preparer, context)).isNull();
        assertThat(context.preparation().unavailableReason()).isEqualTo(SourceAbsenceReason.REVIEW_VERSION_UNAVAILABLE);
        assertThat(context.preparation().prepared()).isNull();
    }

    @Test
    void shouldKeepWorkspaceAuthorizationFailuresFatal() {
        var denied = new ItemCaptureException(ItemCaptureCode.ITEM_NOT_VISIBLE, "workspace");
        when(preparer.prepare(context)).thenThrow(denied);

        assertThatThrownBy(() -> context.preparation().prepare(preparer, context))
                .isSameAs(denied);
        assertThat(context.preparation().unavailableReason()).isNull();
    }

    @Test
    void shouldKeepLocalStorageFailuresDistinctFromUpstreamAvailability() {
        var failure = new GitRepositoryManager.GitOperationException("local storage", new IOException("disk full"));
        when(preparer.prepare(context)).thenThrow(failure);

        assertThatThrownBy(() -> context.preparation().prepare(preparer, context))
                .isSameAs(failure);
        assertThat(context.preparation().unavailableReason()).isNull();
    }
}
