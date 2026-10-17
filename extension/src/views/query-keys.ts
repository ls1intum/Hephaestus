/**
 * The views' queries over the worker bridge. The worker owns the API calls, so no generated key
 * fits; each view and its tests read the keys from here.
 */
export const EXTENSION_QUERY_KEYS = {
	state: ["state"],
	reportView: ["report-view"],
	signInOptions: ["sign-in-options"],
	siteAccess: ["site-access"],
	context: (workspaceSlug: string | undefined) => ["context", workspaceSlug],
	anyContext: ["context"],
	workFeedback: (workspaceSlug: string) => ["work-feedback", workspaceSlug],
	results: (workspaceSlug: string) => ["results", workspaceSlug],
	action: (generation: number, intent: string | undefined) => ["action", generation, intent],
} as const;
