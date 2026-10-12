/** Readers of the project inventory that the server captures beside the reviewed work. */

import { isJsonObject, optionalBoolean, optionalNumber, optionalString } from "./json.ts";
import { readRecordJson, type Evidence } from "./practice-script.ts";

/**
 * Shape of `project_inventory.json`: every permitted repository's issues and pull requests, each row
 * naming its own `repository`. A file-level `repository` explicitly scopes a single-repository capture.
 * All fields best-effort.
 */
export interface ProjectInventory {
	repository?: string;
	focal?: { type?: string; number?: number };
	issues?: InventoryItem[];
	pullRequests?: InventoryItem[];
	truncated?: boolean;
}

export interface InventoryItem {
	/** The provider's full name of the row's repository, as the workspace stores it. */
	repository?: string;
	/** The repository's own id; its full name is unique only within one provider. */
	repositoryId?: number;
	providerId?: number;
	number: number;
	title: string;
	state?: string;
	author?: string;
	milestone?: string;
	url?: string;
	isDraft?: boolean;
}

function optionalIdentity(value: unknown): number | undefined {
	return typeof value === "number" && Number.isSafeInteger(value) && value > 0 ? value : undefined;
}

function parseInventoryItems(value: unknown): InventoryItem[] | undefined {
	if (!Array.isArray(value)) {
		return undefined;
	}
	const items: InventoryItem[] = [];
	for (const entry of value) {
		if (!isJsonObject(entry)) {
			continue;
		}
		const number = optionalNumber(entry.number);
		// An empty title still names an item that the inventory holds.
		if (number === undefined || typeof entry.title !== "string") {
			continue;
		}
		items.push({
			repository: optionalString(entry.repository),
			repositoryId: optionalIdentity(entry.repository_id),
			providerId: optionalIdentity(entry.provider_id),
			number,
			title: entry.title,
			state: optionalString(entry.state),
			author: optionalString(entry.author),
			milestone: optionalString(entry.milestone),
			url: optionalString(entry.url),
			isDraft: optionalBoolean(entry.isDraft),
		});
	}
	return items;
}

function parseProjectInventory(value: unknown): ProjectInventory | null {
	if (!isJsonObject(value)) {
		return null;
	}
	const focal = isJsonObject(value.focal) ? value.focal : undefined;
	return {
		repository: optionalString(value.repository),
		focal: focal && {
			type: optionalString(focal.type),
			number: optionalNumber(focal.number),
		},
		issues: parseInventoryItems(value.issues),
		pullRequests: parseInventoryItems(value.pullRequests),
		truncated: optionalBoolean(value.truncated),
	};
}

export async function readProjectInventory(
	evidence: Pick<Evidence, "record">,
): Promise<ProjectInventory | null> {
	return parseProjectInventory(await readRecordJson(evidence, "project_inventory.json"));
}

/** A repository as the capture identifies it: its own id, or its provider and full name. */
export type RepositoryScope = { repositoryId: number } | { providerId: number; fullName: string };

type IdentifiedItem = InventoryItem & {
	repositoryId: number;
	providerId: number;
	repository: string;
};

/**
 * The issues of one repository, for a `#N` lookup in that repository, matched by id and never by full name
 * alone. Null when no issues were captured, the scope is unknown, a row lacks its repository id, provider
 * id or full name, or two rows contradict each other's identity: a number then names no row, and the
 * repository's count is unknown, not zero.
 */
export function repositoryIssues(
	inventory: ProjectInventory | null,
	scope: RepositoryScope | null,
): InventoryItem[] | null {
	const issues = inventory?.issues;
	if (
		issues === undefined ||
		scope === null ||
		("repositoryId" in scope
			? optionalIdentity(scope.repositoryId) === undefined
			: optionalIdentity(scope.providerId) === undefined || scope.fullName === "")
	) {
		return null;
	}
	const identified = issues.filter(
		(issue): issue is IdentifiedItem =>
			issue.repositoryId !== undefined &&
			issue.providerId !== undefined &&
			issue.repository !== undefined &&
			issue.repository !== "",
	);
	if (identified.length !== issues.length) {
		return null;
	}
	const nameOf = new Map<number, string>();
	const idOf = new Map<string, number>();
	for (const issue of identified) {
		const name = `${String(issue.providerId)}:${issue.repository}`;
		if (
			(nameOf.get(issue.repositoryId) ?? name) !== name ||
			(idOf.get(name) ?? issue.repositoryId) !== issue.repositoryId
		) {
			return null;
		}
		nameOf.set(issue.repositoryId, name);
		idOf.set(name, issue.repositoryId);
	}
	return "repositoryId" in scope
		? identified.filter((issue) => issue.repositoryId === scope.repositoryId)
		: identified.filter(
				(issue) => issue.providerId === scope.providerId && issue.repository === scope.fullName,
			);
}

/**
 * Number-only lookup for an explicitly scoped single-repository capture. Persisted scripts import
 * this export; ambiguous workspace rows must fail as advisory leads, never become another work's issue.
 */
export function inventoryIssues(inventory: ProjectInventory | null): Map<number, InventoryItem> {
	const repository = inventory?.repository;
	if (
		repository === undefined ||
		repository === "" ||
		inventory?.issues === undefined ||
		inventory.issues.some(
			(issue) => issue.repository !== undefined && issue.repository !== repository,
		) ||
		// One full name can name repositories of two providers; rows that say so are not one repository.
		new Set(inventory.issues.flatMap((issue) => issue.repositoryId ?? [])).size > 1 ||
		new Set(inventory.issues.flatMap((issue) => issue.providerId ?? [])).size > 1
	) {
		throw new Error(
			"Issue inventory has no explicit single-repository scope; use repositoryIssues with the reviewed repository.",
		);
	}
	return new Map(inventory.issues.map((issue) => [issue.number, issue]));
}

/** Whether an inventory state is the provider's "open" — `OPEN` on GitHub, `OPENED` on GitLab. */
export function isOpenState(state: string | undefined): boolean {
	return state !== undefined && /^open(?:ed)?$/iu.test(state);
}
