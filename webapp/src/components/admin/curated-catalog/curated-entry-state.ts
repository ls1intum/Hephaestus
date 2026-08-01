import type { CatalogEntryStatus } from "@/api/types.gen";

export type CatalogEntryState = CatalogEntryStatus["state"];
export type CatalogChangeKind = CatalogEntryStatus["changeKind"];

export interface CuratedEntryCopy {
	label: string;
	tone: "neutral" | "info" | "attention";
	detail: string;
}

/**
 * One vocabulary for how a catalog entry stands, used wherever an area or a practice is shown. The
 * two are the same kind of thing, so they read the same way.
 */
export function curatedEntryCopy(
	status: CatalogEntryStatus,
	kind: "practice" | "area",
): CuratedEntryCopy {
	switch (status.state) {
		case "YOURS":
			return {
				label: "Yours",
				tone: "info",
				detail: `You wrote this ${kind}. Hephaestus does not ship one under this name.`,
			};
		case "EDITED_HERE":
			return {
				label: "Edited here",
				tone: "info",
				detail: `You replaced the Hephaestus ${kind}. Updates to it wait for you rather than overwriting yours.`,
			};
		case "UPDATE_WAITING":
			// Framed by what taking it would do, not by what Hephaestus changed: the difference on
			// offer includes your own edit, so "Hephaestus changed the detection" can be false while
			// "taking this changes the detection" is exactly the decision in front of you.
			return {
				label: status.changeKind === "WORDING" ? "New wording waiting" : "Update waiting",
				tone: "attention",
				detail:
					status.changeKind === "WORDING"
						? `The Hephaestus version of this ${kind} differs only in wording — taking it cannot change what gets detected.`
						: `Taking the Hephaestus version would change what this ${kind} detects. Yours keeps running until you do.`,
			};
		case "NO_LONGER_SHIPPED":
			return {
				label: "No longer shipped",
				tone: "attention",
				detail: `Hephaestus stopped shipping this ${kind}. Your version and any workspace copies are untouched.`,
			};
		default:
			return {
				label: "From Hephaestus",
				tone: "neutral",
				detail: `This ${kind} follows Hephaestus. New versions arrive on their own until you edit it.`,
			};
	}
}

/** Whether there is a Hephaestus version to return to. */
export function canUseHephaestusVersion(status: CatalogEntryStatus): boolean {
	return status.state === "EDITED_HERE" || status.state === "UPDATE_WAITING";
}
