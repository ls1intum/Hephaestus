export interface WorkspaceSwitchConfig {
	fallbackTo?: string;
	preserveSearch?: boolean;
}

export interface WorkspaceRouteMatch {
	workspaceSlug: string;
	routeId: string;
	workspaceSwitch?: WorkspaceSwitchConfig;
}

export type WorkspaceSwitchPlan =
	| {
			kind: "absolute";
			to: "/w/$workspaceSlug";
			params: {
				workspaceSlug: string;
			};
			preserveSearch: false;
	  }
	| {
			kind: "relative";
			from: string;
			to: string;
			preserveSearch: boolean;
	  };

export function getWorkspaceRouteMatch(
	matches: readonly {
		routeId: string;
		params: Record<string, unknown>;
		staticData?: {
			workspaceSwitch?: WorkspaceSwitchConfig;
		};
	}[],
): WorkspaceRouteMatch | undefined {
	let workspaceSwitch: WorkspaceSwitchConfig | undefined;

	for (let i = matches.length - 1; i >= 0; i -= 1) {
		const match = matches[i];

		if (!workspaceSwitch && match.staticData?.workspaceSwitch) {
			workspaceSwitch = match.staticData.workspaceSwitch;
		}

		const workspaceSlug = match.params.workspaceSlug;
		if (typeof workspaceSlug === "string" && workspaceSlug.length > 0) {
			return {
				workspaceSlug,
				routeId: match.routeId,
				workspaceSwitch,
			};
		}
	}

	return undefined;
}

export function buildWorkspaceSwitchPlan(
	workspaceRoute: WorkspaceRouteMatch | undefined,
	workspaceSlug: string,
): WorkspaceSwitchPlan {
	if (!workspaceRoute) {
		return {
			kind: "absolute",
			to: "/w/$workspaceSlug",
			params: { workspaceSlug },
			preserveSearch: false,
		};
	}

	if (workspaceRoute.workspaceSwitch?.fallbackTo) {
		return {
			kind: "relative",
			from: workspaceRoute.routeId,
			to: workspaceRoute.workspaceSwitch.fallbackTo,
			preserveSearch: workspaceRoute.workspaceSwitch.preserveSearch ?? false,
		};
	}

	return {
		kind: "relative",
		from: workspaceRoute.routeId,
		to: ".",
		preserveSearch: workspaceRoute.workspaceSwitch?.preserveSearch ?? true,
	};
}
