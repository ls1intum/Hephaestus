export interface WorkspaceSwitchNavigation {
	to:
		| "/w/$workspaceSlug"
		| "/w/$workspaceSlug/achievements"
		| "/w/$workspaceSlug/teams"
		| "/w/$workspaceSlug/mentor"
		| "/w/$workspaceSlug/admin/settings"
		| "/w/$workspaceSlug/admin/members"
		| "/w/$workspaceSlug/admin/teams"
		| "/w/$workspaceSlug/admin/achievements"
		| "/w/$workspaceSlug/admin/practices"
		| "/w/$workspaceSlug/admin/practices/new"
		| "/w/$workspaceSlug/admin/achievement-designer";
	params: {
		workspaceSlug: string;
	};
	preserveSearch: boolean;
}

type WorkspaceRouteMatcher = {
	pattern: RegExp;
	build: (workspaceSlug: string) => WorkspaceSwitchNavigation;
};

const workspaceRouteMatchers: readonly WorkspaceRouteMatcher[] = [
	{
		pattern: /^\/w\/[^/]+\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug",
			params: { workspaceSlug },
			preserveSearch: true,
		}),
	},
	{
		pattern: /^\/w\/[^/]+\/achievements\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug/achievements",
			params: { workspaceSlug },
			preserveSearch: false,
		}),
	},
	{
		pattern: /^\/w\/[^/]+\/teams\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug/teams",
			params: { workspaceSlug },
			preserveSearch: false,
		}),
	},
	{
		pattern: /^\/w\/[^/]+\/mentor(?:\/[^/]+)?\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug/mentor",
			params: { workspaceSlug },
			preserveSearch: false,
		}),
	},
	{
		pattern: /^\/w\/[^/]+\/admin\/settings\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug/admin/settings",
			params: { workspaceSlug },
			preserveSearch: false,
		}),
	},
	{
		pattern: /^\/w\/[^/]+\/admin\/members\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug/admin/members",
			params: { workspaceSlug },
			preserveSearch: false,
		}),
	},
	{
		pattern: /^\/w\/[^/]+\/admin\/teams\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug/admin/teams",
			params: { workspaceSlug },
			preserveSearch: false,
		}),
	},
	{
		pattern: /^\/w\/[^/]+\/admin\/achievements\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug/admin/achievements",
			params: { workspaceSlug },
			preserveSearch: false,
		}),
	},
	{
		pattern: /^\/w\/[^/]+\/admin\/practices\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug/admin/practices",
			params: { workspaceSlug },
			preserveSearch: false,
		}),
	},
	{
		pattern: /^\/w\/[^/]+\/admin\/practices\/new\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug/admin/practices/new",
			params: { workspaceSlug },
			preserveSearch: false,
		}),
	},
	{
		pattern: /^\/w\/[^/]+\/admin\/achievement-designer\/?$/,
		build: (workspaceSlug) => ({
			to: "/w/$workspaceSlug/admin/achievement-designer",
			params: { workspaceSlug },
			preserveSearch: false,
		}),
	},
];

export function getWorkspaceSwitchNavigation(
	pathname: string,
	workspaceSlug: string,
): WorkspaceSwitchNavigation {
	for (const matcher of workspaceRouteMatchers) {
		if (matcher.pattern.test(pathname)) {
			return matcher.build(workspaceSlug);
		}
	}

	return {
		to: "/w/$workspaceSlug",
		params: { workspaceSlug },
		preserveSearch: false,
	};
}
