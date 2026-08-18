package de.tum.cit.aet.hephaestus.practices;

final class PracticeDefinitionDigest {

    private PracticeDefinitionDigest() {}

    static String digest(String slug, PracticeDefinition definition) {
        CanonicalDigest digest = new CanonicalDigest().add(slug).add(definition.name());
        ReviewRuleFingerprint.addBindings(digest, definition.bindings());
        return digest
            .add(definition.criteria())
            .addNullable(definition.precomputeScript())
            .add(PracticeAutomatedReviewPolicyDigest.digest(definition.automatedReviewPolicy()))
            .addNullable(definition.whyItMatters())
            .addNullable(definition.whatGoodLooksLike())
            .addNullable(definition.areaSlug())
            .hex();
    }
}
