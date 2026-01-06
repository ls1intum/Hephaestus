package de.tum.in.www1.hephaestus.practices.model;

import de.tum.in.www1.hephaestus.intelligenceservice.model.BadPractice;
import java.util.Set;

/**
 * State of a detected bad practice in a pull request.
 *
 * <h2>State Machine Overview</h2>
 * <p>This enum serves dual purposes in the {@link PullRequestBadPractice} entity:
 * <ul>
 *   <li><b>Detection State</b> ({@code state} field): Set by AI during detection, indicates severity</li>
 *   <li><b>User State</b> ({@code userState} field): Set by user to override/resolve the detection</li>
 * </ul>
 *
 * <h2>Detection States (AI-assigned)</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────┐
 * │                    DETECTION SEVERITY LEVELS                     │
 * │                                                                  │
 * │  GOOD_PRACTICE ← No issues detected                              │
 * │  MINOR_ISSUE   ← Can be deferred, low impact                     │
 * │  NORMAL_ISSUE  ← Should be addressed before merge                │
 * │  CRITICAL_ISSUE← Blocks merge, must be fixed                     │
 * └──────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>User Resolution States (User-assigned)</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────┐
 * │                    USER RESOLUTION ACTIONS                       │
 * │                                                                  │
 * │  FIXED    ← User claims to have addressed the issue              │
 * │  WONT_FIX ← User acknowledges but chooses not to fix             │
 * │  WRONG    ← User reports false positive (feedback to AI)         │
 * └──────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>State Transitions</h2>
 * <pre>
 * [Any Detection State] ──user action──→ FIXED
 *                       ──user action──→ WONT_FIX
 *                       ──user action──→ WRONG
 *
 * [Re-detection may re-evaluate and update state based on PR changes]
 * </pre>
 *
 * @see PullRequestBadPractice#getState() AI-detected state
 * @see PullRequestBadPractice#getUserState() User override state (takes precedence in DTO)
 */
public enum PullRequestBadPracticeState {
    // === Detection States (AI-assigned severity) ===

    /** No issue detected - PR follows best practices. */
    GOOD_PRACTICE("Good Practice"),

    /** Minor issue that can be deferred - low impact on code quality. */
    MINOR_ISSUE("Minor Issue"),

    /** Standard issue that should be addressed before merge. */
    NORMAL_ISSUE("Normal Issue"),

    /** Severe issue that blocks merge - must be fixed. */
    CRITICAL_ISSUE("Critical Issue"),

    // === User Resolution States (User-assigned) ===

    /** Issue was detected but author has addressed it. */
    FIXED("Fixed"),

    /** Author acknowledges issue but chooses not to fix. */
    WONT_FIX("Won't Fix"),

    /** Detection was incorrect - false positive reported by user. */
    WRONG("Wrong");

    /**
     * States that can be set by users to resolve a detected bad practice.
     * Used for validation in resolution endpoints.
     */
    public static final Set<PullRequestBadPracticeState> USER_RESOLUTION_STATES = Set.of(FIXED, WONT_FIX, WRONG);

    /**
     * States indicating the bad practice has been resolved (no longer actionable).
     * Used to filter out resolved issues from active detection results.
     */
    public static final Set<PullRequestBadPracticeState> RESOLVED_STATES = Set.of(
        FIXED,
        WONT_FIX,
        WRONG,
        GOOD_PRACTICE
    );

    private final String value;

    PullRequestBadPracticeState(String value) {
        this.value = value;
    }

    /** Returns the display value of this state. */
    public String getValue() {
        return value;
    }

    /**
     * Checks if this state represents a resolved (non-actionable) bad practice.
     *
     * @return true if the bad practice no longer requires action
     */
    public boolean isResolved() {
        return RESOLVED_STATES.contains(this);
    }

    /**
     * Checks if this state can be set by users as a resolution action.
     *
     * @return true if users can transition to this state
     */
    public boolean isUserResolutionState() {
        return USER_RESOLUTION_STATES.contains(this);
    }

    /**
     * Converts from intelligence service status enum to domain state.
     *
     * @param status the status from the intelligence service API
     * @return the corresponding domain state
     */
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

    /**
     * Converts from domain state to intelligence service status enum.
     *
     * @param state the domain state
     * @return the corresponding intelligence service status
     */
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
