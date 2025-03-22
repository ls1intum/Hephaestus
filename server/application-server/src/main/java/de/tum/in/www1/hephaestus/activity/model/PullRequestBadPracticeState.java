package de.tum.in.www1.hephaestus.activity.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import de.tum.in.www1.hephaestus.intelligenceservice.model.BadPracticeStatus;

public enum PullRequestBadPracticeState {
    GOOD_PRACTICE,
    FIXED,
    CRITICAL_ISSUE,
    NORMAL_ISSUE,
    MINOR_ISSUE,
    WONT_FIX;

    public static PullRequestBadPracticeState fromBadPracticeStatus(BadPracticeStatus status) {
        return switch (status) {
            case GOOD_PRACTICE -> GOOD_PRACTICE;
            case FIXED -> FIXED;
            case CRITICAL_ISSUE -> CRITICAL_ISSUE;
            case NORMAL_ISSUE -> NORMAL_ISSUE;
            case MINOR_ISSUE -> MINOR_ISSUE;
            case WON_T_FIX -> WONT_FIX;
            default -> throw new IllegalArgumentException("Unexpected value '" + status + "'");
        };
    }

    public static BadPracticeStatus toBadPracticeStatus(PullRequestBadPracticeState state) {
        return switch (state) {
            case GOOD_PRACTICE -> BadPracticeStatus.GOOD_PRACTICE;
            case FIXED -> BadPracticeStatus.FIXED;
            case CRITICAL_ISSUE -> BadPracticeStatus.CRITICAL_ISSUE;
            case NORMAL_ISSUE -> BadPracticeStatus.NORMAL_ISSUE;
            case MINOR_ISSUE -> BadPracticeStatus.MINOR_ISSUE;
            case WONT_FIX -> BadPracticeStatus.WON_T_FIX;
        };
    }
}
