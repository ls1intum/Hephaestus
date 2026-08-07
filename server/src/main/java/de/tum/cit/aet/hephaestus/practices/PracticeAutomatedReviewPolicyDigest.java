package de.tum.cit.aet.hephaestus.practices;

public final class PracticeAutomatedReviewPolicyDigest {

    private PracticeAutomatedReviewPolicyDigest() {}

    public static String digest(PracticeAutomatedReviewPolicy requirements) {
        CanonicalDigest digest = new CanonicalDigest()
            .add(requirements.sourceContractVersion().value())
            .add(requirements.automatedReview().mode().name())
            .add(requirements.automatedReview().evidenceSufficiency().name())
            .add(requirements.whenEvidenceIsInsufficient().name());
        digest.addInt(requirements.knownLimitations().size());
        requirements
            .knownLimitations()
            .forEach(limitation -> digest.add(limitation.code()).add(limitation.description()));
        PracticeEvidenceLimitation reason = requirements.insufficiencyReason();
        digest.add(reason == null ? "" : reason.code()).add(reason == null ? "" : reason.description());
        return digest.hex();
    }
}
