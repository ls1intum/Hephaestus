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

export interface WorkspaceSwitchBehavior {
	target: WorkspaceSwitchTarget;
	preserveSearch?: boolean;
}

export interface WorkspaceRouteMatch {
	workspaceSlug: string;
	workspaceSwitch?: WorkspaceSwitchBehavior;
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
		params: Record<string, unknown>;
		staticData?: {
			workspaceSwitch?: WorkspaceSwitchBehavior;
		};
	}[],
): WorkspaceRouteMatch | undefined {
	let workspaceSlug: string | undefined;
	let workspaceSwitch: WorkspaceSwitchBehavior | undefined;

	for (let i = matches.length - 1; i >= 0; i -= 1) {
		const match = matches[i];
		const matchWorkspaceSlug = match.params.workspaceSlug;

		if (!workspaceSwitch && match.staticData?.workspaceSwitch) {
			workspaceSwitch = match.staticData.workspaceSwitch;
		}

		if (!workspaceSlug && typeof matchWorkspaceSlug === "string" && matchWorkspaceSlug.length > 0) {
			workspaceSlug = matchWorkspaceSlug;
		}
	}

	if (!workspaceSlug) {
		return undefined;
	}

	return {
		workspaceSlug,
		workspaceSwitch,
	};
}

export function buildWorkspaceSwitchPlan(
	workspaceSwitch: WorkspaceSwitchBehavior | undefined,
	workspaceSlug: string,
): WorkspaceSwitchPlan {
	const target = workspaceSwitch?.target ?? "workspace.home";

	return {
		to: workspaceSwitchTargetToRoute[target],
		params: { workspaceSlug },
		preserveSearch: workspaceSwitch?.preserveSearch ?? false,
	};
}
