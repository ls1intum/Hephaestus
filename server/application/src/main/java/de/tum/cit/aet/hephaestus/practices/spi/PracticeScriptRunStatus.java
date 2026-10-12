package de.tum.cit.aet.hephaestus.practices.spi;

/**
 * What became of one admitted practice's program in one review attempt. Execution success only: a program that
 * returned a candidate is {@link #OK} even when admission later withheld it.
 */
public enum PracticeScriptRunStatus {
    /** The program returned a candidate that passed the runner's checks. */
    OK,
    /** The program did not run, because a model that it requires has no binding. */
    SKIPPED,
    /** The program stopped without a candidate: an error, its deadline, its budget, or an invalid program. */
    FAILED,
    /** The attempt ended before the program started. */
    NOT_REACHED,
    /** The report does not name the practice: the attempt stopped or crashed before it could. */
    NOT_FINISHED
}
