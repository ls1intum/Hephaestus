export interface WorkspaceRouteMatch {
	routeId: string;
	params: Record<string, unknown>;
}

export function getWorkspaceRouteMatch(
	matches: readonly { routeId: string; params: Record<string, unknown> }[],
): WorkspaceRouteMatch | undefined {
	for (let index = matches.length - 1; index >= 0; index -= 1) {
		const match = matches[index];
		if (
			match &&
			typeof match.params.workspaceSlug === "string" &&
			match.params.workspaceSlug.length > 0
		) {
			return match;
		}
	}
	return undefined;
}

export function isPortableWorkspaceRoute(match: WorkspaceRouteMatch | undefined) {
	return Boolean(
		match && Object.keys(match.params).every((parameter) => parameter === "workspaceSlug"),
	);
}
