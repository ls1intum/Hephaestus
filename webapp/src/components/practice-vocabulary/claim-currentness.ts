import { CircleHelp, ClockAlert } from "lucide-react";

import type { ResultList } from "@/api/types.gen";

export type Currentness = ResultList["currentness"];

const STALE_NOTE = "The practice or the reviewed work changed after this result.";

/** Shared wording distinguishes an unverifiable result from a changed practice or reviewed work. */
export const NON_CURRENT = {
	STALE: {
		badge: "Historical result",
		badgeVariant: "warning",
		Icon: ClockAlert,
		title: "This result is no longer current",
		description: `${STALE_NOTE} It stays in the record, but it does not describe the current state.`,
		note: STALE_NOTE,
	},
	UNVERIFIABLE: {
		badge: "Unverified result",
		badgeVariant: "outline",
		Icon: CircleHelp,
		title: "We cannot verify this result",
		description:
			"The record of what this result was based on is missing, or it comes from an earlier version of the review. It cannot show whether the practice’s current standard is met.",
		note: "We cannot verify what this result was based on.",
	},
} as const satisfies Record<
	Exclude<Currentness, "CURRENT">,
	{
		badge: string;
		badgeVariant: "warning" | "outline";
		Icon: typeof ClockAlert;
		title: string;
		description: string;
		note: string;
	}
>;

export function claimCurrentnessNote(currentness: Currentness): string | undefined {
	return currentness === "CURRENT" ? undefined : NON_CURRENT[currentness].note;
}

/** Every currentness, in the order a filter offers it. */
export const CURRENTNESS_VALUES = [
	"CURRENT",
	"STALE",
	"UNVERIFIABLE",
] as const satisfies readonly Currentness[];
