package de.tum.in.www1.hephaestus.activity.model;

import de.tum.in.www1.hephaestus.intelligenceservice.model.BadPractice;

/**
 * State of a detected bad practice in a pull request.
 *
 * <p>Tracks both the severity detected by the intelligence service
 * and user-applied resolutions.
 */
public enum PullRequestBadPracticeState {
    /** No issue detected - PR follows best practices */
    GOOD_PRACTICE("Good Practice"),
    /** Issue was detected but author has addressed it */
    FIXED("Fixed"),
    /** Severe issue that blocks merge */
    CRITICAL_ISSUE("Critical Issue"),
    /** Standard issue that should be addressed */
    NORMAL_ISSUE("Normal Issue"),
    /** Minor issue that can be deferred */
    MINOR_ISSUE("Minor Issue"),
    /** Author acknowledges issue but chooses not to fix */
    WONT_FIX("Won't Fix"),
    /** Detection was incorrect - false positive */
    WRONG("Wrong");

    private final String value;

    PullRequestBadPracticeState(String value) {
        this.value = value;
    }

    /** Returns the display value of this state. */
    public String getValue() {
        return value;
    }

    public static PullRequestBadPracticeState fromBadPracticeStatus(BadPractice.StatusEnum status) {
        return switch (status) {
            case GOOD_PRACTICE -> GOOD_PRACTICE;
            case FIXED -> FIXED;
            case CRITICAL_ISSUE -> CRITICAL_ISSUE;
            case NORMAL_ISSUE -> NORMAL_ISSUE;
            case MINOR_ISSUE -> MINOR_ISSUE;
            case WON_T_FIX -> WONT_FIX;
            case WRONG -> WRONG;
        };
    }

    public static BadPractice.StatusEnum toBadPracticeStatus(PullRequestBadPracticeState state) {
        return switch (state) {
            case GOOD_PRACTICE -> BadPractice.StatusEnum.GOOD_PRACTICE;
            case FIXED -> BadPractice.StatusEnum.FIXED;
            case CRITICAL_ISSUE -> BadPractice.StatusEnum.CRITICAL_ISSUE;
            case NORMAL_ISSUE -> BadPractice.StatusEnum.NORMAL_ISSUE;
            case MINOR_ISSUE -> BadPractice.StatusEnum.MINOR_ISSUE;
            case WONT_FIX -> BadPractice.StatusEnum.WON_T_FIX;
            case WRONG -> BadPractice.StatusEnum.WRONG;
        };
    }
}
