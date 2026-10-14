package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.spi.ResultProofAccess;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitedSourceAccessTest extends BaseUnitTest {
    @Test
    void shouldRecheckExactWorkspaceResultAndPurposeWithoutInventingAViewer() {
        var results = mock(ResultProofAccess.class);
        var id = UUID.randomUUID();
        var purpose = SourceUsePurpose.CONVERSATIONAL_MENTORING;
        var access = new CitedSourceAccess(results);
        when(results.permits(1, id, purpose)).thenReturn(true, false);
        assertThat(access.permits(1, id, purpose)).isTrue();
        assertThat(access.permits(1, id, purpose)).isFalse();
        assertThat(access.permits(9, id, purpose)).isFalse();
        assertThat(access.permits(1, id, SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                .isFalse();
    }
}
