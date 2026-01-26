export const XpSystem = {
  BASE_XP: 100,

  // TODO: Reconsider to use a divider of 2 or similar to flatten the quadratic curve a little.
  // TODO: The current one could be too steep.

  /**
   * Calculates the level from total XP using a quadratic curve.
   * Formula: Level = floor(sqrt(totalXP / 100)) + 1
   * This implies:
   * Level 1: 0-99 XP
   * Level 2: 100-399 XP
   * Level 3: 400-899 XP
   */
  getLevel(totalXP: number): number {
    if (totalXP < 0) return 1;
    return Math.floor(Math.sqrt(totalXP / this.BASE_XP)) + 1;
  },

  /**
   * Calculates the total XP required to reach a specific level.
   * Formula: XP = 100 * (Level - 1)^2
   */
  getXpRequiredForLevel(level: number): number {
    if (level <= 1) return 0;
    return this.BASE_XP * (level - 1) ** 2;
  },

  /**
   * Calculates all XP related stats for a given total XP.
   */
  getLevelProgress(totalXP: number) {
    const level = this.getLevel(totalXP);
    const currentLevelStartXp = this.getXpRequiredForLevel(level);
    const nextLevelStartXp = this.getXpRequiredForLevel(level + 1);

    const xpInCurrentLevel = Math.max(0, totalXP - currentLevelStartXp);
    const xpNeededForNextLevel = nextLevelStartXp - currentLevelStartXp;

    // Guard against division by zero (though mathematically shouldn't happen for level >= 1 with our formula)
    const percentage =
      xpNeededForNextLevel > 0
        ? Math.min(
            100,
            Math.max(0, (xpInCurrentLevel / xpNeededForNextLevel) * 100),
          )
        : 100;

    return {
      level,
      currentLevelXP: xpInCurrentLevel,
      xpNeeded: xpNeededForNextLevel,
      percentage,
      nextLevelThreshold: nextLevelStartXp,
    };
  },
};
