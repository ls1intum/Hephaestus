package de.tum.in.www1.hephaestus.practices.model;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.*;

/**
 * Entity representing a detected bad practice in a pull request.
 *
 * <h2>State Management</h2>
 * <p>This entity uses a dual-state pattern to separate AI detection from user resolution:
 * <ul>
 *   <li>{@link #state} - AI-assigned severity (GOOD_PRACTICE, MINOR/NORMAL/CRITICAL_ISSUE)</li>
 *   <li>{@link #userState} - Optional user override (FIXED, WONT_FIX, WRONG)</li>
 * </ul>
 *
 * <h2>Effective State Resolution</h2>
 * <p>When displaying to users, {@code userState} takes precedence over {@code state} if set.
 * This allows users to mark issues as resolved without losing the original detection.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 * 1. Detection: AI analyzes PR → creates entity with state = severity level
 * 2. Display: userState ?? state shown to user
 * 3. Resolution: User sets userState (FIXED/WONT_FIX/WRONG)
 * 4. Re-detection: New detection run may update state, userState persists
 * </pre>
 *
 * @see PullRequestBadPracticeState State machine documentation
 * @see BadPracticeDetection Parent detection run containing multiple bad practices
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "pullrequestbadpractice")
public class PullRequestBadPractice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short title describing the bad practice (e.g., "Missing PR description"). */
    @NonNull
    private String title;

    /** Detailed explanation of the issue and suggested remediation. */
    @Column(columnDefinition = "TEXT")
    @NonNull
    private String description;

    /** The pull request where this bad practice was detected. */
    @NonNull
    @ManyToOne
    @JoinColumn(name = "pullrequest_id")
    @ToString.Exclude
    private PullRequest pullRequest;

    /**
     * AI-detected severity of this bad practice.
     * Set during detection and may be updated on re-detection.
     *
     * @see PullRequestBadPracticeState for severity levels
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    private PullRequestBadPracticeState state;

    /**
     * User-assigned resolution state, if any.
     * When set, takes precedence over {@link #state} for display purposes.
     * Null means no user action has been taken.
     *
     * @see PullRequestBadPracticeState#USER_RESOLUTION_STATES valid user states
     */
    @Enumerated(EnumType.STRING)
    private PullRequestBadPracticeState userState;

    /** Timestamp when this bad practice was first detected. */
    private Instant detectionTime;

    /** Timestamp of the most recent update to this record. */
    private Instant lastUpdateTime;

    /** PR lifecycle state at the time of detection (for analytics). */
    @Enumerated(EnumType.STRING)
    private PullRequestLifecycleState detectionPullrequestLifecycleState;

    /** Trace ID for LLM observability (links to Langfuse traces). */
    private String detectionTraceId;

    /** The detection run that produced this bad practice. */
    @ManyToOne
    @JoinColumn(name = "bad_practice_detection_id")
    @ToString.Exclude
    private BadPracticeDetection badPracticeDetection;

    /**
     * Returns the effective state for display purposes.
     * User state takes precedence over AI-detected state when set.
     *
     * @return userState if set, otherwise state
     */
    public PullRequestBadPracticeState getEffectiveState() {
        return userState != null ? userState : state;
    }

    /**
     * Checks if this bad practice has been resolved by the user.
     *
     * @return true if userState is set and is a resolution state
     */
    public boolean isResolved() {
        return userState != null && userState.isResolved();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PullRequestBadPractice that = (PullRequestBadPractice) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
