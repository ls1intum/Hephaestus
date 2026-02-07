import { useEffect, useRef } from "react";
import { toast } from "sonner";
import type { Achievement } from "@/api/types.gen";
import type { AchievementStatus } from "@/components/achievements/types";

/**
 * Hook to display toast notifications when achievements are unlocked.
 * Tracks previously seen achievements and shows toasts for newly unlocked ones.
 *
 * @param achievements - Current list of achievements from the API
 * @param enabled - Whether notifications are enabled (default: true)
 */
export function useAchievementNotifications(achievements: Achievement[], enabled = true) {
	// Track the set of unlocked achievement IDs we've already notified about
	const notifiedIdsRef = useRef<Set<string>>(new Set());
	// Track whether this is the initial load (don't notify for existing unlocks)
	const isInitialLoadRef = useRef(true);

	useEffect(() => {
		if (!enabled || achievements.length === 0) {
			return;
		}

		// Get currently unlocked achievements
		const unlockedAchievements = achievements.filter(
			(a) => a.status === ("unlocked" as AchievementStatus),
		);

		if (isInitialLoadRef.current) {
			// On initial load, just record all currently unlocked achievements
			// Don't show toasts for achievements that were already unlocked
			for (const achievement of unlockedAchievements) {
				if (achievement.id) {
					notifiedIdsRef.current.add(achievement.id);
				}
			}
			isInitialLoadRef.current = false;
			return;
		}

		// Find newly unlocked achievements (not in our notified set)
		const newlyUnlocked = unlockedAchievements.filter(
			(a) => a.id && !notifiedIdsRef.current.has(a.id),
		);

		// Show toast for each newly unlocked achievement
		for (const achievement of newlyUnlocked) {
			if (achievement.id) {
				notifiedIdsRef.current.add(achievement.id);
			}

			toast.success(`Achievement Unlocked: ${achievement.name ?? "Unknown"}`, {
				description: achievement.description ?? "",
				duration: 5000,
				icon: "🏆",
			});
		}
	}, [achievements, enabled]);

	// Reset the notification state (useful for testing)
	const resetNotifications = () => {
		notifiedIdsRef.current.clear();
		isInitialLoadRef.current = true;
	};

	return { resetNotifications };
}
