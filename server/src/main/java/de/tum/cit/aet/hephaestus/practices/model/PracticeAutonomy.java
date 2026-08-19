package de.tum.cit.aet.hephaestus.practices.model;

public enum PracticeAutonomy {
    OFF,
    HUMAN_APPROVAL,

    AUTOMATIC;

    public static final int MAX_LENGTH = 16;
    public static final PracticeAutonomy DEFAULT = HUMAN_APPROVAL;

    public boolean admitsReview() {
        return this != OFF;
    }

    public boolean deliversWithoutApproval() {
        return this == AUTOMATIC;
    }
}
