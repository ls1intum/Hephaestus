package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.profile.dto.ProfileXpRecordDTO;

/**
 * Utility class for XP and Level calculations.
 *
 * <p>Uses a Power 1.8 curve with linear offset, rounded to nearest 50 for clean UX.
 */
public final class XpSystem {

    public static final int BASE_XP = 100;
    public static final double GROWTH_EXPONENT = 1.8;
    public static final int LINEAR_WEIGHT = 50;
    public static final int ROUNDING_STEP = 50;
    public static final int LEVEL_CAP = 100;

    private XpSystem() {
        // Utility class
    }

    /**
     * Calculates the level from total XP using a modified power curve with linear offset.
     * Formula: XP = Round50(BASE * (Level-1)^EXP + LINEAR * (Level-1))
     *
     * @param totalXP the total accumulated XP
     * @return the calculated level (starting at 1)
     */
    public static int getLevel(long totalXP) {
        if (totalXP < 0) {
            return 1;
        }
        if (totalXP >= 1_000_000) {
            return LEVEL_CAP; // sanity guard as 1 mio xp is equivalent to roughly level 167 (which shouldn't be reachable anyway)
        }

        // 1. Approximate level using inverse of the dominant power term to get close
        // This ignores rounding and linear parts but gives a good starting point
        int estimatedLevel = (int) Math.floor(Math.pow(totalXP / (double) BASE_XP, 1.0 / GROWTH_EXPONENT)) + 1;

        // 2. Deterministic Correction Loop
        // Since getXpRequiredForLevel includes the rounding logic, this loop
        // ensures we find the exact integer level consistent with the step function.

        // If we estimated too high (required XP > total XP), step down
        while (estimatedLevel > 1 && getXpRequiredForLevel(estimatedLevel) > totalXP) {
            estimatedLevel--;
        }
        // If we estimated too low (unlikely but safe), step up
        while (estimatedLevel < LEVEL_CAP && getXpRequiredForLevel(estimatedLevel + 1) <= totalXP) {
            estimatedLevel++;
        }

        return estimatedLevel;
    }

    /**
     * Calculates the total XP required to reach a specific level.
     * Formula: XP = RoundToNearest50( BASE * (Level - 1)^EXP + LINEAR * (Level - 1) )
     *
     * @param level the level to reach
     * @return the total XP required to reach that level, rounded to nearest 50
     */
    public static long getXpRequiredForLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        double quadraticPart = BASE_XP * Math.pow(level - 1, GROWTH_EXPONENT);
        double linearPart = LINEAR_WEIGHT * (level - 1);
        double rawXp = quadraticPart + linearPart;

        // Round to nearest ROUNDING_STEP (50)
        return Math.round(rawXp / ROUNDING_STEP) * ROUNDING_STEP;
    }

    /**
     * Calculates all XP related stats for a given total XP.
     *
     * @param totalXP the total accumulated XP (negative values are clamped to 0)
     * @return a DTO containing level, current level XP, XP needed for next level, and total XP
     */
    public static ProfileXpRecordDTO getLevelProgress(long totalXP) {
        int level = getLevel(Math.max(0, totalXP));
        long currentLevelStartXp = getXpRequiredForLevel(level);
        long nextLevelStartXp = getXpRequiredForLevel(level + 1);

        long xpInCurrentLevel = Math.max(0, totalXP - currentLevelStartXp);
        long xpNeededForNextLevel = nextLevelStartXp - currentLevelStartXp;

        return new ProfileXpRecordDTO(level, xpInCurrentLevel, xpNeededForNextLevel, totalXP);
    }
}
