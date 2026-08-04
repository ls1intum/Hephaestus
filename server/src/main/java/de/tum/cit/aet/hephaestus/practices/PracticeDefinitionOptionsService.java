package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalog;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfile;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeDefinitionOptionsDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceSourceOptionDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeTriggerEventOptionDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeWorkTypeDefinitionOptionsDTO;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PracticeDefinitionOptionsService {

    private final ArtifactSourceCatalogRegistry catalogs;
    private final PracticeEvidenceDefaults defaults;

    public PracticeDefinitionOptionsService(ArtifactSourceCatalogRegistry catalogs, PracticeEvidenceDefaults defaults) {
        this.catalogs = catalogs;
        this.defaults = defaults;
    }

    public PracticeDefinitionOptionsDTO options() {
        ArtifactSourceCatalog catalog = catalogs.current();
        return new PracticeDefinitionOptionsDTO(
            Arrays.stream(WorkArtifact.values())
                .map(artifact -> options(catalog, artifact))
                .toList()
        );
    }

    private PracticeWorkTypeDefinitionOptionsDTO options(ArtifactSourceCatalog catalog, WorkArtifact artifact) {
        PracticeAutomatedReviewPolicy recommendedPolicy = defaults.forArtifact(artifact);
        EvidenceProfile profile = catalogs.requireProfile(catalog.version(), recommendedPolicy.evidenceProfile());
        return new PracticeWorkTypeDefinitionOptionsDTO(
            artifact,
            TriggerEventCatalog.optionsFor(artifact)
                .stream()
                .map(option ->
                    new PracticeTriggerEventOptionDTO(option.event(), option.displayName(), option.recommended())
                )
                .toList(),
            recommendedPolicy,
            List.of(PracticeAutomatedReviewMode.LANGUAGE_MODEL),
            catalog
                .sources()
                .stream()
                .filter(source -> profile.allows(source.kind()))
                .map(source ->
                    new PracticeEvidenceSourceOptionDTO(
                        source.kind().value(),
                        source.displayName(),
                        source.description(),
                        source.privacyClass(),
                        source.completenessPolicy().supportsComplete(),
                        source.freshnessPolicy().supportsCurrentRequirement(),
                        source.completenessPolicy().supportsEmpty(),
                        catalogs.isSourceUsePermitted(
                            catalog.version(),
                            source.kind(),
                            SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW
                        )
                    )
                )
                .toList()
        );
    }
}
