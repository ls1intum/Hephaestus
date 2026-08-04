package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUseAudience;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class EvidenceDeliveryAuthorizationTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rejectsDeliveryWhenCurrentSourceAuthorizationWasWithdrawn() {
        AgentJobRepository jobs = mock(AgentJobRepository.class);
        ArtifactSourceCatalogRegistry catalogs = mock(ArtifactSourceCatalogRegistry.class);
        UUID jobId = UUID.randomUUID();
        AgentJob job = new AgentJob();
        job.setEvidenceSnapshot(MAPPER.readTree("{\"manifest\":{\"contractVersion\":\"1.0.0\"}}"));
        when(jobs.findByIdAndWorkspaceId(jobId, 7L)).thenReturn(Optional.of(job));
        when(
            catalogs.isSourceUsePermitted(
                new SourceContractVersion("1.0.0"),
                new SourceKind("scm.pull-request.diff"),
                SourceUseAudience.PRACTICE_MENTORING
            )
        ).thenReturn(false);

        boolean permitted = new EvidenceDeliveryAuthorization(jobs, catalogs).permits(
            7L,
            jobId,
            MAPPER.readTree("{\"citations\":[{\"sourceKind\":\"scm.pull-request.diff\"}]}"),
            SourceUseAudience.PRACTICE_MENTORING
        );

        assertThat(permitted).isFalse();
    }

    @Test
    void permitsCanonicalEvidenceForTheRequestedAudience() {
        AgentJobRepository jobs = mock(AgentJobRepository.class);
        ArtifactSourceCatalogRegistry catalogs = mock(ArtifactSourceCatalogRegistry.class);
        UUID jobId = UUID.randomUUID();
        AgentJob job = new AgentJob();
        job.setEvidenceSnapshot(MAPPER.readTree("{\"manifest\":{\"contractVersion\":\"1.0.0\"}}"));
        when(jobs.findByIdAndWorkspaceId(jobId, 7L)).thenReturn(Optional.of(job));
        when(
            catalogs.isSourceUsePermitted(
                new SourceContractVersion("1.0.0"),
                new SourceKind("scm.pull-request.diff"),
                SourceUseAudience.OPERATOR_QUALITY_ASSURANCE
            )
        ).thenReturn(true);

        assertThat(
            new EvidenceDeliveryAuthorization(jobs, catalogs).permits(
                7L,
                jobId,
                MAPPER.readTree("{\"citations\":[{\"sourceKind\":\"scm.pull-request.diff\"}]}"),
                SourceUseAudience.OPERATOR_QUALITY_ASSURANCE
            )
        ).isTrue();
    }

    @Test
    void rejectsMissingOrMalformedCitationSources() {
        AgentJobRepository jobs = mock(AgentJobRepository.class);
        ArtifactSourceCatalogRegistry catalogs = mock(ArtifactSourceCatalogRegistry.class);
        EvidenceDeliveryAuthorization authorization = new EvidenceDeliveryAuthorization(jobs, catalogs);
        UUID malformedJobId = UUID.randomUUID();
        AgentJob job = new AgentJob();
        job.setEvidenceSnapshot(MAPPER.readTree("{\"manifest\":{\"contractVersion\":\"1.0.0\"}}"));
        when(jobs.findByIdAndWorkspaceId(malformedJobId, 7L)).thenReturn(Optional.of(job));

        assertThat(
            authorization.permits(7L, (UUID) null, (JsonNode) null, SourceUseAudience.PRACTICE_MENTORING)
        ).isFalse();
        assertThat(
            authorization.permits(
                7L,
                UUID.randomUUID(),
                MAPPER.readTree("{\"citations\":[]}"),
                SourceUseAudience.PRACTICE_MENTORING
            )
        ).isFalse();
        assertThat(
            authorization.permits(
                7L,
                malformedJobId,
                MAPPER.readTree("{\"citations\":[{\"sourceKind\":7}]}"),
                SourceUseAudience.PRACTICE_MENTORING
            )
        ).isFalse();
    }
}
