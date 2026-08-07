package de.tum.cit.aet.hephaestus.agent.backfill;

/** A campaign cannot be started, cancelled or superseded from the state it is in. Maps to 409. */
public class ReviewBackfillConflictException extends RuntimeException {

    public ReviewBackfillConflictException(String message) {
        super(message);
    }
}
