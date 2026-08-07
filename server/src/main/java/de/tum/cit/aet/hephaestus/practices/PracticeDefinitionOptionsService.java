package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalog;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfile;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeDefinitionOptionsDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceSourceOptionDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeTriggerEventOptionDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeWorkTypeDefinitionOptionsDTO;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PracticeDefinitionOptionsService {

    private final ArtifactSourceCatalogRegistry catalogs;
    private final PracticeEvidenceDefaults defaults;
    private final PracticeTriggerOptions triggerOptions;

    public PracticeDefinitionOptionsService(
        ArtifactSourceCatalogRegistry catalogs,
        PracticeEvidenceDefaults defaults,
        PracticeTriggerOptions triggerOptions
    ) {
        this.catalogs = catalogs;
        this.defaults = defaults;
        this.triggerOptions = triggerOptions;
    }

    public PracticeDefinitionOptionsDTO options() {
        ArtifactSourceCatalog catalog = catalogs.current();
        return new PracticeDefinitionOptionsDTO(
            ArtifactKinds.authorable()
                .stream()
                .map(artifact -> options(catalog, artifact))
                .toList()
        );
    }

    private PracticeWorkTypeDefinitionOptionsDTO options(ArtifactSourceCatalog catalog, ArtifactKind artifact) {
        PracticeAutomatedReviewPolicy recommendedPolicy = defaults.forArtifact(artifact);
        EvidenceProfile profile = catalogs.requireProfile(catalog.version(), recommendedPolicy.evidenceProfile());
        return new PracticeWorkTypeDefinitionOptionsDTO(
            artifact,
            triggerOptions
                .optionsFor(artifact)
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
                        source.completenessPolicy().supportsEmpty()
                    )
                )
                .toList()
        );
    }
}
