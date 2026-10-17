import type { CatalogGroup, CatalogPractice } from "@/api/types.gen";

export type CatalogEntryStatus = Pick<
	CatalogPractice | CatalogGroup,
	"state" | "changeKind" | "excluded"
>;

export interface CatalogEntryCopy {
	label: string;
	tone: "neutral" | "info" | "attention";
	detail: string;
}

export function catalogEntryCopy(
	status: CatalogEntryStatus,
	kind: "practice" | "group",
): CatalogEntryCopy {
	switch (status.state) {
		case "YOURS": {
			return {
				label: "No Hephaestus default",
				tone: "info",
				detail: status.excluded
					? `This ${kind} has no Hephaestus default and is excluded from new workspaces.`
					: `This ${kind} has no Hephaestus default and is maintained on this instance.`,
			};
		}
		case "EDITED_HERE": {
			return {
				label: "Customized on this instance",
				tone: "info",
				detail: status.excluded
					? `This customized ${kind} is excluded from new workspaces.`
					: `This ${kind} is customized on this instance. Updates to the Hephaestus default need review before they apply.`,
			};
		}
		case "UPDATE_WAITING": {
			if (kind === "group") {
				return {
					label: "Hephaestus update available",
					tone: "info",
					detail:
						"Applying this update would change the group’s name, description, icon, or color. Your saved version stays in place until you decide.",
				};
			}
			return {
				// The label, not just the tone, has to say which kind of update this is: colour alone
				// cannot carry the distinction (WCAG 2.2 SC 1.4.1).
				label:
					status.changeKind === "WORDING"
						? "Hephaestus update available: wording"
						: "Hephaestus update available: review rules",
				tone: status.changeKind === "WORDING" ? "info" : "attention",
				detail:
					status.changeKind === "WORDING"
						? "Applying this update would change wording or developer guidance only. Review rules would stay the same."
						: "Applying this update would change review rules. Your saved version stays in place until you decide.",
			};
		}
		case "NO_LONGER_SHIPPED": {
			return {
				label: "Removed from Hephaestus defaults",
				tone: "attention",
				detail: status.excluded
					? `This ${kind} is no longer a Hephaestus default and is excluded from new workspaces. Existing workspaces do not change.`
					: `This ${kind} is no longer a Hephaestus default. Keep it as a custom ${kind}, or exclude it from new workspaces.`,
			};
		}
		// Enumerate states so new API values fail type checking rather than imply success.
		case "FROM_HEPHAESTUS": {
			return {
				label: "Uses Hephaestus default",
				tone: "neutral",
				detail: status.excluded
					? `This ${kind} uses the Hephaestus default but is excluded from new workspaces.`
					: `This ${kind} uses the Hephaestus default. Future updates apply automatically until you customize it.`,
			};
		}
	}
}

export function canUseHephaestusVersion(status: CatalogEntryStatus): boolean {
	return status.state === "EDITED_HERE" || status.state === "UPDATE_WAITING";
}

export function canKeepCurrentDefinition(status: CatalogEntryStatus): boolean {
	return status.state === "UPDATE_WAITING" || status.state === "NO_LONGER_SHIPPED";
}

export function isOrdinary(status: CatalogEntryStatus): boolean {
	return status.state === "FROM_HEPHAESTUS" && !status.excluded;
}

export interface CatalogChangeCounts {
	reviewRules: number;
	wording: number;
	groupAppearance: number;
	removedDefaults: number;
}

export function catalogChangeCounts({
	practices,
	groups,
}: {
	practices: readonly CatalogPractice[];
	groups: readonly CatalogGroup[];
}): CatalogChangeCounts {
	const waitingPractices = practices.filter((practice) => practice.state === "UPDATE_WAITING");
	return {
		reviewRules: waitingPractices.filter((practice) => practice.changeKind !== "WORDING").length,
		wording: waitingPractices.filter((practice) => practice.changeKind === "WORDING").length,
		groupAppearance: groups.filter((group) => group.state === "UPDATE_WAITING").length,
		removedDefaults: [...practices, ...groups].filter(
			(entry) => entry.state === "NO_LONGER_SHIPPED" && entry.offered,
		).length,
	};
}

export function needsReview(entry: CatalogPractice | CatalogGroup): boolean {
	return entry.state === "UPDATE_WAITING" || (entry.state === "NO_LONGER_SHIPPED" && entry.offered);
}
