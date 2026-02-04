import { useQuery } from "@tanstack/react-query";
import type { AchievementDTO } from "@/components/achievements/types";

/**
 * Query key factory for achievements.
 */
export function getAchievementsQueryKey(workspaceSlug: string, login: string) {
	return ["achievements", workspaceSlug, login] as const;
}

/**
 * Fetches achievements for a user in a workspace.
 *
 * @param workspaceSlug - The workspace slug
 * @param login - The user's login/username
 * @returns TanStack Query result with achievements array
 */
export function useAchievements(workspaceSlug: string, login: string) {
	return useQuery({
		queryKey: getAchievementsQueryKey(workspaceSlug, login),
		queryFn: async (): Promise<AchievementDTO[]> => {
			const response = await fetch(
				`/api/workspaces/${encodeURIComponent(workspaceSlug)}/users/${encodeURIComponent(login)}/achievements`,
				{
					credentials: "include",
					headers: {
						Accept: "application/json",
					},
				},
			);

			if (!response.ok) {
				throw new Error(`Failed to fetch achievements: ${response.status} ${response.statusText}`);
			}

			return response.json();
		},
		enabled: Boolean(workspaceSlug) && Boolean(login),
	});
}
