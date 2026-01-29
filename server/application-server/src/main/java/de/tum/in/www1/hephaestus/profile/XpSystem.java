package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.profile.dto.ProfileXPRecordDTO;

/**
 * Utility class for XP and Level calculations.
 *
 * <p>Ported from webapp/src/lib/xp.ts
 */
public final class XpSystem {

    public static final int BASE_XP = 100;

    private XpSystem() {
        // Utility class
    }

    /**
     * Calculates the level from total XP using a quadratic curve.
     * Formula: Level = floor(sqrt(totalXP / 100)) + 1
     * This implies:
     * Level 1: 0-99 XP
     * Level 2: 100-399 XP
     * Level 3: 400-899 XP
     *
     * @param totalXP the total accumulated XP
     * @return the calculated level (starting at 1)
     */
    public static int getLevel(long totalXP) {
        if (totalXP < 0) {
            return 1;
        }
        return (int) Math.floor(Math.sqrt(totalXP / (double) BASE_XP)) + 1;
    }

    /**
     * Calculates the total XP required to reach a specific level.
     * Formula: XP = 100 * (Level - 1)^2
     *
     * @param level the level to reach
     * @return the total XP required to reach that level
     */
    public static long getXpRequiredForLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        return (long) (BASE_XP * Math.pow(level - 1, 2));
    }

    /**
     * Calculates all XP related stats for a given total XP.
     *
     * @param totalXP the total accumulated XP
     * @return a DTO containing level, current level XP, XP needed for next level, and total XP
     */
    public static ProfileXPRecordDTO getLevelProgress(long totalXP) {
        int level = getLevel(totalXP);
        long currentLevelStartXp = getXpRequiredForLevel(level);
        long nextLevelStartXp = getXpRequiredForLevel(level + 1);

        long xpInCurrentLevel = Math.max(0, totalXP - currentLevelStartXp);
        long xpNeededForNextLevel = nextLevelStartXp - currentLevelStartXp;

        return new ProfileXPRecordDTO(
            level,
            xpInCurrentLevel,
            xpNeededForNextLevel,
            totalXP
        );
    }
}
