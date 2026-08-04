package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalog;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfile;
import de.tum.cit.aet.hephaestus.evidence.SourceUseAudience;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceArtifactOptionsDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceAuthoringDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceSourceOptionDTO;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
public class PracticeEvidenceAuthoringService {

    private final ArtifactSourceCatalogRegistry catalogs;
    private final PracticeEvidenceDefaults defaults;

    public PracticeEvidenceAuthoringService(ArtifactSourceCatalogRegistry catalogs, PracticeEvidenceDefaults defaults) {
        this.catalogs = catalogs;
        this.defaults = defaults;
    }

    public PracticeEvidenceAuthoringDTO options() {
        ArtifactSourceCatalog catalog = catalogs.current();
        return new PracticeEvidenceAuthoringDTO(
            Arrays.stream(WorkArtifact.values())
                .map(artifact -> options(catalog, artifact))
                .toList()
        );
    }

    private PracticeEvidenceArtifactOptionsDTO options(ArtifactSourceCatalog catalog, WorkArtifact artifact) {
        PracticeEvidenceDeclaration baseline = defaults.forArtifact(artifact);
        EvidenceProfile profile = catalogs.requireProfile(catalog.version(), baseline.profile());
        return new PracticeEvidenceArtifactOptionsDTO(
            artifact,
            baseline,
            catalog
                .sources()
                .stream()
                .filter(source -> profile.allows(source.kind()))
                .map(source ->
                    new PracticeEvidenceSourceOptionDTO(
                        source.kind().value(),
                        source.description(),
                        source.privacyClass(),
                        source.completenessPolicy().supportsComplete(),
                        source.freshnessPolicy().supportsCurrentRequirement(),
                        source.completenessPolicy().supportsEmpty(),
                        catalogs.isSourceUsePermitted(
                            catalog.version(),
                            source.kind(),
                            SourceUseAudience.PRACTICE_DETECTION
                        )
                    )
                )
                .toList()
        );
    }
}
