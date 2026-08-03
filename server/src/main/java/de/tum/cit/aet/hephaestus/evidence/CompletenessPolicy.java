package de.tum.cit.aet.hephaestus.evidence;

public record CompletenessPolicy(boolean supportsComplete, boolean supportsPartial, boolean supportsEmpty) {
    public CompletenessPolicy {
        if (!supportsComplete && !supportsPartial) {
            throw new IllegalArgumentException("A source must support COMPLETE or PARTIAL evidence");
        }
    }
}
