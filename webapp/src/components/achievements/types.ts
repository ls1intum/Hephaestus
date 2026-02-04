/**
 * Achievement types matching the backend API.
 * These types are manually defined since API client generation is unavailable.
 */

export type AchievementStatus = "LOCKED" | "AVAILABLE" | "UNLOCKED";

export type AchievementCategory =
	| "COMMITS"
	| "PULL_REQUESTS"
	| "REVIEWS"
	| "ISSUES"
	| "COMMENTS"
	| "CROSS_CATEGORY";

/**
 * Achievement data transfer object returned by the backend API.
 */
export interface AchievementDTO {
	id: string;
	name: string;
	description: string;
	icon: string;
	category: AchievementCategory;
	level: number;
	parentId: string | null;
	status: AchievementStatus;
	progress: number;
	maxProgress: number;
	unlockedAt: string | null;
}

/**
 * Tier type for visual representation.
 * Derived from level for backwards compatibility with existing UI.
 */
export type AchievementTier = "minor" | "notable" | "keystone" | "legendary";

/**
 * Maps achievement level (1-7) to visual tier.
 */
export function levelToTier(level: number): AchievementTier {
	if (level <= 2) return "minor";
	if (level <= 4) return "notable";
	if (level <= 6) return "keystone";
	return "legendary";
}

/**
 * Maps backend status (uppercase) to UI status (lowercase).
 */
export function normalizeStatus(status: AchievementStatus): "locked" | "available" | "unlocked" {
	return status.toLowerCase() as "locked" | "available" | "unlocked";
}
