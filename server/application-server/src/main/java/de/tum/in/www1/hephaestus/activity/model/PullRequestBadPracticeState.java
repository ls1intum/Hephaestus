package de.tum.in.www1.hephaestus.activity.model;

import de.tum.in.www1.hephaestus.intelligenceservice.model.BadPractice;
import lombok.Getter;

@Getter
public enum PullRequestBadPracticeState {
    GOOD_PRACTICE("Good Practice"),
    FIXED("Fixed"),
    CRITICAL_ISSUE("Critical Issue"),
    NORMAL_ISSUE("Normal Issue"),
    MINOR_ISSUE("Minor Issue"),
    WONT_FIX("Won't Fix"),
    WRONG("Wrong");

    private final String value;

    PullRequestBadPracticeState(String value) {
        this.value = value;
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
