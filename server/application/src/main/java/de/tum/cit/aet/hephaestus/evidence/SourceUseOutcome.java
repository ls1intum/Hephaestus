package de.tum.cit.aet.hephaestus.evidence;

/**
 * What was decided about a source use.
 *
 * <p>One value, for the same reason as {@link SourceUseBasis}: an entry exists only where engineering
 * approved the use, and a use nobody approved is expressed by the absence of an entry rather than by a
 * denial recorded in one.
 */
public enum SourceUseOutcome {
    ENGINEERING_APPROVED,
}
