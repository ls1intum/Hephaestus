package de.tum.in.www1.hephaestus.achievement;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
     * Status of an achievement relative to a user's progress.
     *
     * <p>The status determines how the achievement is displayed in the UI:
     * <ul>
     *   <li>{@code LOCKED} - Not yet available (parent achievement not unlocked)</li>
     *   <li>{@code AVAILABLE} - Can be worked on (parent unlocked or no parent)</li>
     *   <li>{@code UNLOCKED} - Already earned by the user</li>
     *   <li>{@code HIDDEN} - Not shown in the UI and ignored by normal progress/unlock logic (used for internal or deprecated achievements)</li>
     * </ul>
     */
    public enum AchievementStatus {
        /**
         * The achievement is locked because its parent has not been unlocked yet.
         * Displayed with a lock icon or dimmed in the UI.
         */
        @JsonProperty("locked") LOCKED,

        /**
         * The achievement is available to work towards.
         * Either it has no parent, or its parent is already unlocked.
         */
        @JsonProperty("available") AVAILABLE,

        /**
         * The achievement has been unlocked by the user.
         * Shows the unlock date and completed visual state.
         */
        @JsonProperty("unlocked") UNLOCKED,

        /**
         * The achievement is hidden and not presented to users in the UI.
         * It represents a hidden or secret unlockable achievement.
         */
        @JsonProperty("hidden") HIDDEN
    }
