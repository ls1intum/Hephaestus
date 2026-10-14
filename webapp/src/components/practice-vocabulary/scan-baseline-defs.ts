import {
	CircleCheckBigIcon,
	CircleDotDashedIcon,
	EqualIcon,
	FileMinusIcon,
	FlagIcon,
	GitCompareArrowsIcon,
	HourglassIcon,
	ListXIcon,
	PencilLineIcon,
	ScaleIcon,
	ShieldAlertIcon,
	ShieldCheckIcon,
	SparklesIcon,
	SplitIcon,
	WrenchIcon,
} from "lucide-react";

import type { RemovalGuard, ScanReport } from "@/api/types.gen";

import type { StatusDef, StatusDefs } from "@/components/common/status-def";

/**
 * How one problem of a scan compares with the scan before it. The problem list has no generated
 * type yet, so the values are written out here, as the server's `ProblemBaselineState` names them.
 */
export type ProblemBaselineState = "NEW" | "UNCHANGED" | "UPDATED" | "ABSENT";
export const PROBLEM_BASELINE_STATES = [
	"NEW",
	"UNCHANGED",
	"UPDATED",
	"ABSENT",
] as const satisfies readonly ProblemBaselineState[];

/** Why a problem of the earlier scan is absent from this one. Only a proved repair is named. */
export type ProblemAbsentReason = "REPAIRED";

export type ScanComparison = ScanReport["comparison"];
export type RemovalGuardState = RemovalGuard["state"];

export const SCAN_BASELINE_DEFS: StatusDefs<ProblemBaselineState> = {
	NEW: {
		label: "New",
		icon: SparklesIcon,
		badgeVariant: "warning",
		description: "The earlier scan did not record this problem.",
	},
	UNCHANGED: {
		label: "Unchanged",
		icon: EqualIcon,
		badgeVariant: "secondary",
		description: "The earlier scan recorded the same problem.",
	},
	UPDATED: {
		label: "Updated",
		icon: PencilLineIcon,
		badgeVariant: "secondary",
		description: "The earlier scan recorded this problem with different details.",
	},
	ABSENT: {
		label: "Absent",
		icon: CircleDotDashedIcon,
		badgeVariant: "outline",
		description:
			"The earlier scan recorded this problem and this scan did not. That alone does not prove a fix.",
	},
};

/**
 * An absent problem whose file was reviewed again, at a new version, under the same standard and
 * method, and met it or recorded a complete set of problems without this one. Only this proves a fix.
 */
export const NOW_MET_DEF: StatusDef = {
	label: "Now met",
	icon: CircleCheckBigIcon,
	badgeVariant: "success",
	description:
		"The file changed and was reviewed again under the same standard. This problem is no longer there.",
};

/** The badge for one problem's comparison: a proved repair reads "Now met", never just "Absent". */
export function baselineDef(
	state: ProblemBaselineState,
	absentReason: ProblemAbsentReason | null,
): StatusDef {
	return state === "ABSENT" && absentReason === "REPAIRED"
		? NOW_MET_DEF
		: SCAN_BASELINE_DEFS[state];
}

export const METHOD_CHANGED_SENTENCE =
	"The review method changed. This scan cannot show which problems were fixed.";

/** Whether a scan can be compared with the scan before it, and so whether it can show fixes. */
export const SCAN_COMPARISON_DEFS: StatusDefs<ScanComparison> = {
	COMPARABLE: {
		label: "Compared",
		icon: GitCompareArrowsIcon,
		badgeVariant: "secondary",
		description:
			"Compared with the earlier scan of this repository under the same standard and method.",
	},
	FIRST_SCAN: {
		label: "First scan",
		icon: FlagIcon,
		badgeVariant: "outline",
		description:
			"No earlier scan of this repository to compare with. Every problem is new to this baseline.",
	},
	METHOD_CHANGED: {
		label: "Method changed",
		icon: WrenchIcon,
		badgeVariant: "warning",
		description: METHOD_CHANGED_SENTENCE,
	},
	STANDARD_CHANGED: {
		label: "Standard changed",
		icon: ScaleIcon,
		badgeVariant: "warning",
		description:
			"A practice changed what it asks for since the earlier scan. This scan cannot show which problems were fixed.",
	},
	INCOMPLETE: {
		label: "Not comparable",
		icon: HourglassIcon,
		badgeVariant: "warning",
		description:
			"This scan or the earlier one did not cover every file. Problems it did not reach stay as they were, not fixed.",
	},
	MIXED: {
		label: "Partly compared",
		icon: SplitIcon,
		badgeVariant: "warning",
		description:
			"Some practices can be compared with the earlier scan and some cannot. Each practice says which.",
	},
};

/**
 * Whether files missing from this scan's listing were recorded as removed from the repository.
 * A removed file is report history, not a fixed problem.
 */
export const REMOVAL_GUARD_DEFS: StatusDefs<RemovalGuardState> = {
	APPLIED: {
		label: "Removed files recorded",
		icon: FileMinusIcon,
		badgeVariant: "secondary",
		description:
			"The file listing was complete, so files no longer in the repository were recorded as removed.",
	},
	NOT_NEEDED: {
		label: "No files removed",
		icon: ShieldCheckIcon,
		badgeVariant: "outline",
		description: "Every file of the earlier scan is still in the repository.",
	},
	INCOMPLETE_LISTING: {
		label: "Listing incomplete",
		icon: ListXIcon,
		badgeVariant: "warning",
		description:
			"The scan could not list every file, so no file was recorded as removed. Missing files are not counted as fixed.",
	},
	SUSPECT_LISTING: {
		label: "Listing not trusted",
		icon: ShieldAlertIcon,
		badgeVariant: "warning",
		description:
			"Too many files disappeared at once to trust the listing, so no file was recorded as removed.",
	},
};

/** Comparisons under which an absent problem can be a proved fix. */
export function comparisonCanShowFixes(comparison: ScanComparison): boolean {
	return comparison === "COMPARABLE" || comparison === "MIXED";
}
