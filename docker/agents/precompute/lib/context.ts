/**
 * Read-only access to the materialised agent context directory (inputs/context/) from a precompute script.
 *
 * A precompute script receives the context dir as its 4th argument (after repoPath, diffFiles, metadata).
 * These helpers let a script consult the SAME cross-artifact context the agent sees — the whole-project
 * inventory, the resolved linked work-items, the issue thread — so it can emit `directions` that point the
 * LLM at relevant neighbours (e.g. "12 OPEN issues exist; check project_inventory.json for overlap").
 *
 * Contract reminder: precompute surfaces FACTS and DIRECTIONS, never verdicts. These helpers only READ;
 * they never decide.
 */

/** Best-effort JSON read of a context file; returns `null` when absent/unreadable (the common case). */
export async function readContextJson<T = any>(
	contextDir: string | undefined,
	name: string,
): Promise<T | null> {
	if (!contextDir) return null;
	try {
		const file = Bun.file(`${contextDir}/${name}`);
		if (!(await file.exists())) return null;
		return (await file.json()) as T;
	} catch {
		return null;
	}
}

/** Shape of `project_inventory.json` (see WorkspaceInventoryContentSource). All fields best-effort. */
export interface ProjectInventory {
	repository?: string;
	focal?: { type?: string; number?: number };
	issues?: InventoryItem[];
	pullRequests?: InventoryItem[];
	counts?: { issuesListed?: number; pullRequestsListed?: number };
	truncated?: boolean;
}

export interface InventoryItem {
	number: number;
	title: string;
	state?: string;
	author?: string;
	milestone?: string;
	url?: string;
	isDraft?: boolean;
}

/** Convenience: load the whole-project inventory, or `null` when it was not materialised. */
export function readProjectInventory(
	contextDir: string | undefined,
): Promise<ProjectInventory | null> {
	return readContextJson<ProjectInventory>(contextDir, "project_inventory.json");
}
