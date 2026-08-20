package de.tum.cit.aet.hephaestus.practices.review;

public class PracticeReviewPreconditionRequiredException extends RuntimeException {

    PracticeReviewPreconditionRequiredException() {
        super("If-Match must contain the current practice-review settings ETag");
    }
}
