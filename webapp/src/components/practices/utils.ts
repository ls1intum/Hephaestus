import type { LucideIcon } from "lucide-react";
import { AlertTriangle, Ban, Bug, Check, Flame, Rocket, XOctagon } from "lucide-react";
import type { PullRequestBadPractice } from "@/api/types.gen";

// State configuration for bad practice types
export const stateConfig: {
	[key: string]: {
		icon: LucideIcon;
		text: string;
		color: string;
	};
} = {
	GOOD_PRACTICE: {
		icon: Rocket,
		text: "Good Practice",
		color: "text-github-success-foreground",
	},
	FIXED: { icon: Check, text: "Fixed", color: "text-github-done-foreground" },
	CRITICAL_ISSUE: {
		icon: Flame,
		text: "Critical Issue",
		color: "text-github-danger-foreground",
	},
	NORMAL_ISSUE: {
		icon: AlertTriangle,
		text: "Normal Issue",
		color: "text-github-severe-foreground",
	},
	MINOR_ISSUE: {
		icon: Bug,
		text: "Minor Issue",
		color: "text-github-attention-foreground",
	},
	WONT_FIX: {
		icon: Ban,
		text: "Won't Fix",
		color: "text-github-muted-foreground",
	},
	WRONG: {
		icon: XOctagon,
		text: "Wrong",
		color: "text-github-muted-foreground",
	},
};

/**
 * States that can be resolved (issues that require user action).
 * Used to determine whether to show resolution UI controls.
 */
const RESOLVABLE_STATES: ReadonlySet<PullRequestBadPractice["state"]> = new Set([
	"CRITICAL_ISSUE",
	"NORMAL_ISSUE",
	"MINOR_ISSUE",
]);

/**
 * Check if a bad practice can be resolved (shows "Resolve" button).
 * Returns true for issues, false for already-resolved or good practices.
 */
export function canBeResolved(state: PullRequestBadPractice["state"]): boolean {
	return RESOLVABLE_STATES.has(state);
}

// Filter practices by category
export function filterGoodAndBadPractices(allBadPractices: PullRequestBadPractice[]): {
	goodPractices: PullRequestBadPractice[];
	badPractices: PullRequestBadPractice[];
	resolvedPractices: PullRequestBadPractice[];
} {
	const goodPractices = allBadPractices.filter(
		(badPractice) => badPractice.state === "GOOD_PRACTICE",
	);

	const badPractices = allBadPractices.filter((badPractice) => canBeResolved(badPractice.state));

	const resolvedPractices = allBadPractices.filter(
		(badPractice) =>
			badPractice.state === "FIXED" ||
			badPractice.state === "WONT_FIX" ||
			badPractice.state === "WRONG",
	);

	return { goodPractices, badPractices, resolvedPractices };
}
