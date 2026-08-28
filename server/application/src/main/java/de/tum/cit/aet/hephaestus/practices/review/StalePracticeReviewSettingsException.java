package de.tum.cit.aet.hephaestus.practices.review;

public class StalePracticeReviewSettingsException extends RuntimeException {

    StalePracticeReviewSettingsException() {
        super("Practice-review settings changed after they were loaded");
    }
}
