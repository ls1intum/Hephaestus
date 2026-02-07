import { useQuery } from "@tanstack/react-query";
import { getUserAchievementsOptions } from "@/api/@tanstack/react-query.gen";

/**
 * Fetches achievements for a user in a workspace.
 *
 * @param workspaceSlug - The workspace slug
 * @param login - The user's login/username
 * @returns TanStack Query result with achievements array
 */
export function useAchievements(workspaceSlug: string, login: string) {
	return useQuery({
		...getUserAchievementsOptions({
			path: { workspaceSlug, login },
		}),
		enabled: Boolean(workspaceSlug) && Boolean(login),
	});
}
