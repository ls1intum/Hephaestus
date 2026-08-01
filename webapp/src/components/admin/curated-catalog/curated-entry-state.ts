import type { CatalogEntryStatus } from "@/api/types.gen";

export type CatalogEntryState = CatalogEntryStatus["state"];
export type CatalogChangeKind = CatalogEntryStatus["changeKind"];

export interface CuratedEntryCopy {
	label: string;
	tone: "neutral" | "info" | "attention";
	detail: string;
}

/** Describes the consequence of taking the Hephaestus version, not who changed what. */
export function curatedEntryCopy(
	status: CatalogEntryStatus,
	kind: "practice" | "area",
): CuratedEntryCopy {
	switch (status.state) {
		case "YOURS":
			return {
				label: "Added here",
				tone: "info",
				detail: status.offered
					? `You added this ${kind}. Nothing arrives from Hephaestus for it.`
					: `You added this ${kind} and have stopped offering it to new workspaces.`,
			};
		case "EDITED_HERE":
			return {
				label: "Edited here",
				tone: "info",
				detail: status.offered
					? `Your version is what this instance offers. If Hephaestus changes its own, you are asked before anything here changes.`
					: `Your version has replaced the Hephaestus one, and this ${kind} is not offered to new workspaces.`,
			};
		case "UPDATE_WAITING":
			if (kind === "area") {
				return {
					label: "Update waiting",
					tone: "info",
					detail:
						"Taking the Hephaestus version would change how this area is presented. Yours stays as it is until you do.",
				};
			}
			return {
				label: "Update waiting",
				tone: status.changeKind === "WORDING" ? "info" : "attention",
				detail:
					status.changeKind === "WORDING"
						? `The Hephaestus version differs only in wording. Taking it cannot change what this ${kind} detects.`
						: `Taking the Hephaestus version would change what this ${kind} detects. Yours keeps running until you do.`,
			};
		case "NO_LONGER_SHIPPED":
			return {
				label: "No longer shipped",
				tone: "attention",
				detail: status.offered
					? `Hephaestus no longer ships this ${kind}. Your version is still offered to new workspaces until you retire it.`
					: `Hephaestus no longer ships this ${kind}, and you have stopped offering it. Workspaces that already have it keep it.`,
			};
		default:
			return {
				label: "From Hephaestus",
				tone: "neutral",
				detail: status.offered
					? `This ${kind} follows Hephaestus. A newer Hephaestus version takes effect on its own until you edit it.`
					: `This ${kind} follows Hephaestus, but you have stopped offering it to new workspaces.`,
			};
	}
}

/** Whether there is a Hephaestus version to return to. */
export function canUseHephaestusVersion(status: CatalogEntryStatus): boolean {
	return status.state === "EDITED_HERE" || status.state === "UPDATE_WAITING";
}

/** Whether declining the waiting update is a decision this entry can still take. */
export function canKeepOurVersion(status: CatalogEntryStatus): boolean {
	return status.state === "UPDATE_WAITING";
}

/**
 * Whether the entry is worth a badge in a list. An entry that follows Hephaestus and is offered is
 * the ordinary case; labelling every row with it would bury the few rows that want a decision.
 */
export function isOrdinary(status: CatalogEntryStatus): boolean {
	return status.state === "FROM_HEPHAESTUS" && status.offered;
}
