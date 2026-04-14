export type WorkspaceSwitchTarget =
	| "workspace.home"
	| "workspace.achievements"
	| "workspace.teams"
	| "workspace.mentor"
	| "admin.settings"
	| "admin.members"
	| "admin.teams"
	| "admin.achievements"
	| "admin.practices"
	| "admin.practice-create"
	| "admin.achievement-designer";
export interface WorkspaceRouteMatch {
	workspaceSlug: string;
	routeId: string;
}

export interface WorkspaceSwitchPlan {
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

type WorkspaceRouteId =
	| "/w/$workspaceSlug/"
	| "/w/$workspaceSlug/achievements"
	| "/w/$workspaceSlug/teams/"
	| "/w/$workspaceSlug/mentor/"
	| "/w/$workspaceSlug/mentor/$threadId"
	| "/w/$workspaceSlug/admin/achievement-designer"
	| "/settings"
	| "/members"
	| "/teams"
	| "/achievements"
	| "/practices"
	| "/new"
	| "/$practiceSlug"
	| "/w/$workspaceSlug/user/$username/"
	| "/w/$workspaceSlug/user/$username/achievements";

const workspaceSwitchTargetsByRouteId: Record<
	WorkspaceRouteId,
	{ target: WorkspaceSwitchTarget; preserveSearch: boolean }
> = {
	"/w/$workspaceSlug/": { target: "workspace.home", preserveSearch: true },
	"/w/$workspaceSlug/achievements": { target: "workspace.achievements", preserveSearch: false },
	"/w/$workspaceSlug/teams/": { target: "workspace.teams", preserveSearch: false },
	"/w/$workspaceSlug/mentor/": { target: "workspace.mentor", preserveSearch: false },
	"/w/$workspaceSlug/mentor/$threadId": { target: "workspace.mentor", preserveSearch: false },
	"/w/$workspaceSlug/admin/achievement-designer": {
		target: "admin.achievement-designer",
		preserveSearch: false,
	},
	"/settings": { target: "admin.settings", preserveSearch: false },
	"/members": { target: "admin.members", preserveSearch: false },
	"/teams": { target: "admin.teams", preserveSearch: false },
	"/achievements": { target: "admin.achievements", preserveSearch: false },
	"/practices": { target: "admin.practices", preserveSearch: false },
	"/new": { target: "admin.practices", preserveSearch: false },
	"/$practiceSlug": { target: "admin.practices", preserveSearch: false },
	"/w/$workspaceSlug/user/$username/": { target: "workspace.home", preserveSearch: false },
	"/w/$workspaceSlug/user/$username/achievements": {
		target: "workspace.home",
		preserveSearch: false,
	},
};

const workspaceSwitchTargetToRoute = {
	"workspace.home": "/w/$workspaceSlug",
	"workspace.achievements": "/w/$workspaceSlug/achievements",
	"workspace.teams": "/w/$workspaceSlug/teams",
	"workspace.mentor": "/w/$workspaceSlug/mentor",
	"admin.settings": "/w/$workspaceSlug/admin/settings",
	"admin.members": "/w/$workspaceSlug/admin/members",
	"admin.teams": "/w/$workspaceSlug/admin/teams",
	"admin.achievements": "/w/$workspaceSlug/admin/achievements",
	"admin.practices": "/w/$workspaceSlug/admin/practices",
	"admin.practice-create": "/w/$workspaceSlug/admin/practices/new",
	"admin.achievement-designer": "/w/$workspaceSlug/admin/achievement-designer",
} satisfies Record<WorkspaceSwitchTarget, WorkspaceSwitchPlan["to"]>;

export function getWorkspaceRouteMatch(
	matches: readonly {
		routeId: string;
		params: Record<string, unknown>;
	}[],
): WorkspaceRouteMatch | undefined {
	for (let i = matches.length - 1; i >= 0; i -= 1) {
		const match = matches[i];
		const matchWorkspaceSlug = match.params.workspaceSlug;

		if (typeof matchWorkspaceSlug === "string" && matchWorkspaceSlug.length > 0) {
			return {
				workspaceSlug: matchWorkspaceSlug,
				routeId: match.routeId,
			};
		}
	}

	return undefined;
}

export function buildWorkspaceSwitchPlan(
	routeId: string | undefined,
	workspaceSlug: string,
): WorkspaceSwitchPlan {
	const behavior =
		(routeId && routeId in workspaceSwitchTargetsByRouteId
			? workspaceSwitchTargetsByRouteId[routeId as WorkspaceRouteId]
			: undefined) ?? workspaceSwitchTargetsByRouteId["/w/$workspaceSlug/user/$username/"];

	return {
		to: workspaceSwitchTargetToRoute[behavior.target],
		params: { workspaceSlug },
		preserveSearch: behavior.preserveSearch,
	};
}
