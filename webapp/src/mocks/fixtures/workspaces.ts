import type { WorkspaceListItem } from "@/api/types.gen";

/**
 * `createdAt` is a `Date`, the transformed shape, so one value both seeds the query cache and
 * serialises through `HttpResponse.json` to the ISO string the wire carries.
 */
export function workspaceListItem(
	workspaceSlug: string,
	overrides: Partial<WorkspaceListItem> = {},
): WorkspaceListItem {
	return {
		id: 1,
		workspaceSlug,
		displayName: workspaceSlug,
		accountLogin: workspaceSlug,
		providerType: "GITHUB",
		createdAt: new Date("2026-01-01T00:00:00Z"),
		status: "ACTIVE",
		achievementsEnabled: false,
		leaderboardEnabled: false,
		leaguesEnabled: false,
		mentorEnabled: false,
		practicesEnabled: false,
		progressionEnabled: false,
		...overrides,
	};
}
