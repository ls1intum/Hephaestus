import type { Achievement } from "@/api/types.gen";

/**
 * Achievement category type derived from the generated Achievement type.
 */
export type AchievementCategory = NonNullable<Achievement["category"]>;

/**
 * Achievement status type derived from the generated Achievement type.
 */
export type AchievementStatus = NonNullable<Achievement["status"]>;

/**
 * Tier type for visual representation.
 */
export type AchievementTier = NonNullable<Achievement["rarity"]>;
