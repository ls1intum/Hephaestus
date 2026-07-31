package de.tum.cit.aet.hephaestus.practices.curated;

public class CuratedPracticeConflictException extends RuntimeException {

    public CuratedPracticeConflictException(String message) {
        super(message);
    }

    public CuratedPracticeConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
