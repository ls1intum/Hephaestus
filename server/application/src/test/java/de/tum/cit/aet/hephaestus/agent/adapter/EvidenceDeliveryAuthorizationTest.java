package de.tum.cit.aet.hephaestus.agent.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.CitedSourceAccess;
import de.tum.cit.aet.hephaestus.agent.handler.AdmittedResultFixtures;
import de.tum.cit.aet.hephaestus.evidence.SourceCatalog;
import de.tum.cit.aet.hephaestus.evidence.SourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceDeliveryAuthorizationTest extends BaseUnitTest {
    @Test
    void keepsVerifiedDeliveryIndependentOfRuntimeJobRetention() {
        var sources = mock(SourceCatalogRegistry.class);
        var access = mock(CitedSourceAccess.class);
        var catalog = mock(SourceCatalog.class);
        when(catalog.version()).thenReturn(new SourceContractVersion("2.0.0"));
        when(sources.current()).thenReturn(catalog);
        var authorization = new EvidenceDeliveryAuthorization(sources, access);
        var result = Result.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .developerId(2L)
                .evidence(AdmittedResultFixtures.evidence("docs.document.core"))
                .build();
        when(access.permits(1, result.getId(), SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(true);
        when(sources.isSourceUsePermitted(any(), any(), eq(SourceUsePurpose.CONVERSATIONAL_MENTORING)))
                .thenReturn(true);
        assertThat(authorization.permitsForNewDelivery(1, List.of(result), SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .containsExactly(result.getId());
        result.setEvidenceVersion((short) 0);
        assertThat(authorization.permitsForNewDelivery(1, List.of(result), SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .isEmpty();
        when(access.permits(1, result.getId(), SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(false);
        assertThat(authorization.permits(1, result, SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .isFalse();
        assertThat(authorization.permits(9, result, SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .isFalse();
    }
}
