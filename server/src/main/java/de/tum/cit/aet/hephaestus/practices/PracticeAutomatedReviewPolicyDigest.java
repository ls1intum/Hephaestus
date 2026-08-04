package de.tum.cit.aet.hephaestus.practices;

public final class PracticeAutomatedReviewPolicyDigest {

    private PracticeAutomatedReviewPolicyDigest() {}

    public static String digest(PracticeAutomatedReviewPolicy requirements) {
        CanonicalDigest digest = new CanonicalDigest()
            .add(requirements.sourceContractVersion().value())
            .add(requirements.evidenceProfile().value())
            .add(requirements.automatedReview().mode().name())
            .add(requirements.automatedReview().evidenceSufficiency().name())
            .add(requirements.whenEvidenceIsInsufficient().name())
            .addInt(requirements.requiredEvidence().size());
        requirements.requiredEvidence().forEach(requirement -> add(digest, requirement));
        digest.addInt(requirements.optionalContext().size());
        requirements.optionalContext().forEach(requirement -> add(digest, requirement));
        digest.addInt(requirements.knownLimitations().size());
        requirements
            .knownLimitations()
            .forEach(limitation -> digest.add(limitation.code()).add(limitation.description()));
        return digest.hex();
    }

    private static void add(CanonicalDigest digest, PracticeEvidenceRequirement requirement) {
        digest
            .add(requirement.sourceKind().value())
            .add(requirement.completeness().name())
            .add(requirement.freshness().name());
    }

    private static void add(CanonicalDigest digest, PracticeOptionalContextSource requirement) {
        digest.add(requirement.sourceKind().value());
    }
}
