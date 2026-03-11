package de.tum.in.www1.hephaestus.achievement;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Comparator;

/**
 * Rarity type for visual representation.
 * Derived from level for backwards compatibility with existing UI.
 * <p>
 * Ordering (lowest -> highest): common < uncommon < rare < epic < legendary < mythic
 */
public enum AchievementRarity {
    @JsonProperty("common")
    COMMON,
    @JsonProperty("uncommon")
    UNCOMMON,
    @JsonProperty("rare")
    RARE,
    @JsonProperty("epic")
    EPIC,
    @JsonProperty("legendary")
    LEGENDARY,
    @JsonProperty("mythic")
    MYTHIC;

    /**
     * Numeric rank used for sorting (lower = less rare).
     */
    public int getRank() {
        return switch (this) {
            case COMMON -> 0;
            case UNCOMMON -> 1;
            case RARE -> 2;
            case EPIC -> 3;
            case LEGENDARY -> 4;
            case MYTHIC -> 5;
        };
    }

    /**
     * Comparator that sorts by rarity from lowest to highest (common -> mythic).
     */
    public static final Comparator<AchievementRarity> RARITY_COMPARATOR = Comparator.comparingInt(
        AchievementRarity::getRank
    );
}
