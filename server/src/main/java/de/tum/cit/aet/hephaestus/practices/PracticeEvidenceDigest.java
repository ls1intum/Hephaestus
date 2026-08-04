package de.tum.cit.aet.hephaestus.practices;

public final class PracticeEvidenceDigest {

    private PracticeEvidenceDigest() {}

    public static String digest(PracticeEvidenceDeclaration declaration) {
        CanonicalDigest digest = new CanonicalDigest()
            .add(declaration.sourceContractVersion().value())
            .add(declaration.profile().value())
            .add(declaration.detectorCapability().assessmentMethod().name())
            .add(declaration.detectorCapability().evidenceCoverage().name())
            .add(declaration.onUnsatisfied().name())
            .addInt(declaration.required().size());
        declaration.required().forEach(requirement -> add(digest, requirement));
        digest.addInt(declaration.optional().size());
        declaration.optional().forEach(requirement -> add(digest, requirement));
        digest.addInt(declaration.blindSpots().size());
        declaration.blindSpots().forEach(blindSpot -> digest.add(blindSpot.code()).add(blindSpot.summary()));
        return digest.hex();
    }

    private static void add(CanonicalDigest digest, PracticeEvidenceRequirement requirement) {
        digest
            .add(requirement.sourceKind().value())
            .add(requirement.completeness().name())
            .add(requirement.freshness().name());
    }

    private static void add(CanonicalDigest digest, OptionalPracticeEvidenceRequirement requirement) {
        digest
            .add(requirement.sourceKind().value())
            .add(requirement.completeness().name())
            .add(requirement.freshness().name());
    }
}
