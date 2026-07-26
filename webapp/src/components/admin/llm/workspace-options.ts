import type { FacetOption } from "@/components/common/FacetMultiSelect";

/** The workspaces an instance admin can grant a model to, as the admin surfaces carry them around. */
export interface WorkspaceOption {
	id: number;
	displayName: string;
	workspaceSlug: string;
}

/**
 * Adapt the workspace list to the shared facet picker. The slug rides along as the option's
 * description so it is both visible and searchable — two workspaces may share a display name, and the
 * slug is what disambiguates them.
 */
export function workspaceFacetOptions(workspaces: WorkspaceOption[]): FacetOption<number>[] {
	return workspaces.map((workspace) => ({
		value: workspace.id,
		label: workspace.displayName,
		description: workspace.workspaceSlug,
	}));
}
