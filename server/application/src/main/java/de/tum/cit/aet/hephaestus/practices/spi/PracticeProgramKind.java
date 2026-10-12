package de.tum.cit.aet.hephaestus.practices.spi;

/** Which program reviewed a practice: the built-in review, or the practice's own script. */
public enum PracticeProgramKind {
    /** The practice holds no script; the built-in program asks the review session. */
    BUILTIN,
    /** The practice's own script. */
    CUSTOM
}
