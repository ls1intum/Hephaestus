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

import { readFile } from "node:fs/promises";

import { isJsonObject } from "./practice-contract.ts";

/**
 * Best-effort JSON read of a context file; returns `null` when absent/unreadable (the common case).
 *
 * The result is `unknown` on purpose: the file is written by whichever connector produced the context,
 * so its shape is a claim about another system, not something this side can know. Narrow it — see
 * `readProjectInventory` below for the pattern.
 */
export async function readContextJson(
	contextDir: string | undefined,
	name: string,
): Promise<unknown> {
	if (!contextDir) return null;
	try {
		return JSON.parse(await readFile(`${contextDir}/${name}`, "utf8"));
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

/** An issue or pull request in the inventory listing. Every consumer renders `#number "title"`. */
export interface InventoryItem {
	number: number;
	title: string;
	state?: string;
	author?: string;
	milestone?: string;
	url?: string;
	isDraft?: boolean;
}

function optionalString(value: unknown): string | undefined {
	return typeof value === "string" ? value : undefined;
}

function optionalNumber(value: unknown): number | undefined {
	return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function optionalBoolean(value: unknown): boolean | undefined {
	return typeof value === "boolean" ? value : undefined;
}

/**
 * An item without a usable number AND title cannot be rendered or compared by any consumer, so it is
 * dropped here rather than being handed on as a hole for every call site to defend against.
 */
function parseInventoryItems(value: unknown): InventoryItem[] | undefined {
	if (!Array.isArray(value)) return undefined;
	const items: InventoryItem[] = [];
	for (const entry of value) {
		if (!isJsonObject(entry)) continue;
		const number = optionalNumber(entry.number);
		const title = optionalString(entry.title);
		if (number === undefined || title === undefined) continue;
		items.push({
			number,
			title,
			state: optionalString(entry.state),
			author: optionalString(entry.author),
			milestone: optionalString(entry.milestone),
			url: optionalString(entry.url),
			isDraft: optionalBoolean(entry.isDraft),
		});
	}
	return items;
}

/** Narrow a parsed `project_inventory.json` to the fields this side actually reads. */
export function parseProjectInventory(value: unknown): ProjectInventory | null {
	if (!isJsonObject(value)) return null;
	const focal = isJsonObject(value.focal) ? value.focal : undefined;
	const counts = isJsonObject(value.counts) ? value.counts : undefined;
	return {
		repository: optionalString(value.repository),
		focal: focal && {
			type: optionalString(focal.type),
			number: optionalNumber(focal.number),
		},
		issues: parseInventoryItems(value.issues),
		pullRequests: parseInventoryItems(value.pullRequests),
		counts: counts && {
			issuesListed: optionalNumber(counts.issuesListed),
			pullRequestsListed: optionalNumber(counts.pullRequestsListed),
		},
		truncated: optionalBoolean(value.truncated),
	};
}

/** Convenience: load the whole-project inventory, or `null` when it was not materialised. */
export async function readProjectInventory(
	contextDir: string | undefined,
): Promise<ProjectInventory | null> {
	return parseProjectInventory(await readContextJson(contextDir, "project_inventory.json"));
}
