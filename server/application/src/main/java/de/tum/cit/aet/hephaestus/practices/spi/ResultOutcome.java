package de.tum.cit.aet.hephaestus.practices.spi;

public enum ResultOutcome {
    MET,
    NOT_MET,
    DOES_NOT_APPLY,
    COULD_NOT_DETERMINE;

    public boolean isMet() {
        return this == MET;
    }

    public boolean isNotMet() {
        return this == NOT_MET;
    }

    public boolean isDecided() {
        return this == MET || this == NOT_MET;
    }
}
