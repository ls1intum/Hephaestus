package de.tum.in.www1.hephaestus.practices.model;

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
 * [Agent pipeline may re-evaluate and update state based on PR changes]
 * </pre>
 *
 * @see PullRequestBadPractice#getState() AI-detected state
 * @see PullRequestBadPractice#getUserState() User override state (takes precedence in DTO)
 */
public enum PullRequestBadPracticeState {
    // === Detection States (AI-assigned severity) ===

    /** No issue detected - PR follows best practices. */
    GOOD_PRACTICE,

    /** Minor issue that can be deferred - low impact on code quality. */
    MINOR_ISSUE,

    /** Standard issue that should be addressed before merge. */
    NORMAL_ISSUE,

    /** Severe issue that blocks merge - must be fixed. */
    CRITICAL_ISSUE,

    // === User Resolution States (User-assigned) ===

    /** Issue was detected but author has addressed it. */
    FIXED,

    /** Author acknowledges issue but chooses not to fix. */
    WONT_FIX,

    /** Detection was incorrect - false positive reported by user. */
    WRONG,
}
