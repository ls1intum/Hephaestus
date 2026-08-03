package de.tum.cit.aet.hephaestus.practices;

final class PracticeDefinitionDigest {

    private PracticeDefinitionDigest() {}

    static String digest(String slug, PracticeDefinition definition) {
        CanonicalDigest digest = new CanonicalDigest()
            .add(slug)
            .add(definition.name())
            .add(definition.artifactType().name())
            .addInt(definition.triggerEvents().size());
        definition.triggerEvents().forEach(digest::add);
        return digest
            .add(definition.criteria())
            .addNullable(definition.precomputeScript())
            .add(PracticeEvidenceDigest.digest(definition.evidence()))
            .addNullable(definition.whyItMatters())
            .addNullable(definition.whatGoodLooksLike())
            .addNullable(definition.areaSlug())
            .hex();
    }
}
