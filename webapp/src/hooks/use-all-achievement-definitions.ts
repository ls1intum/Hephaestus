import { useQuery } from "@tanstack/react-query";
import { getAllAchievementDefinitionsOptions } from "@/api/@tanstack/react-query.gen";

/**
 * Fetches all available achievement definitions from the backend.
 *
 * This hook is intended for the Designer Mode in the development environment
 * and will only execute the query when `import.meta.env.DEV` is `true`.
 * In production builds, the query is permanently disabled and no network
 * request is made.
 *
 * @param workspaceSlug - The workspace slug
 * @param login - The user's login/username
 * @returns TanStack Query result with all achievement definitions
 */
export function useAllAchievementDefinitions(workspaceSlug: string, login: string) {
	return useQuery({
		...getAllAchievementDefinitionsOptions({
			path: { workspaceSlug, login },
		}),
		enabled: import.meta.env.DEV && Boolean(workspaceSlug) && Boolean(login),
	});
}
