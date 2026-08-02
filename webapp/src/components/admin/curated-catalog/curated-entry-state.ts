import type { CatalogEntryStatus } from "@/api/types.gen";

export type CatalogEntryState = CatalogEntryStatus["state"];
export type CatalogChangeKind = CatalogEntryStatus["changeKind"];

export interface CuratedEntryCopy {
	label: string;
	tone: "neutral" | "info" | "attention";
	detail: string;
}

export function curatedEntryCopy(
	status: CatalogEntryStatus,
	kind: "practice" | "area",
): CuratedEntryCopy {
	switch (status.state) {
		case "YOURS":
			return {
				label: "No Hephaestus default",
				tone: "info",
				detail: status.offered
					? `This ${kind} has no Hephaestus default and is maintained on this instance.`
					: `This ${kind} has no Hephaestus default and is excluded from new workspaces.`,
			};
		case "EDITED_HERE":
			return {
				label: "Customized on this instance",
				tone: "info",
				detail: status.offered
					? `New workspaces receive this customized ${kind}. Hephaestus updates require review.`
					: `This customized ${kind} is excluded from new workspaces.`,
			};
		case "UPDATE_WAITING":
			if (kind === "area") {
				return {
					label: "Hephaestus update available",
					tone: "info",
					detail:
						"Applying this update would change the area's name, description, icon, or color. The current version remains active until you decide.",
				};
			}
			return {
				label: "Hephaestus update available",
				tone: status.changeKind === "WORDING" ? "info" : "attention",
				detail:
					status.changeKind === "WORDING"
						? "Applying this update would change wording or developer guidance only. Review behavior would stay the same."
						: "Applying this update would change review behavior. The current version remains active until you decide.",
			};
		case "NO_LONGER_SHIPPED":
			return {
				label: "Removed from Hephaestus defaults",
				tone: "attention",
				detail: status.offered
					? `Hephaestus no longer provides this ${kind}. Keep it as a custom ${kind}, or exclude it from new workspaces.`
					: `This ${kind} is no longer included with Hephaestus and is excluded from new workspaces. Existing workspaces do not change.`,
			};
		default:
			return {
				label: "Uses Hephaestus default",
				tone: "neutral",
				detail: status.offered
					? `This ${kind} uses the Hephaestus default. Future updates apply automatically until you customize it.`
					: `This ${kind} uses the Hephaestus default but is excluded from new workspaces.`,
			};
	}
}

export function canUseHephaestusVersion(status: CatalogEntryStatus): boolean {
	return status.state === "EDITED_HERE" || status.state === "UPDATE_WAITING";
}

export function canKeepCurrentDefinition(status: CatalogEntryStatus): boolean {
	return status.state === "UPDATE_WAITING" || status.state === "NO_LONGER_SHIPPED";
}

export function isOrdinary(status: CatalogEntryStatus): boolean {
	return status.state === "FROM_HEPHAESTUS" && status.offered;
}
