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
 * Resolved states for bad practices - these have been addressed by the user
 */
const RESOLVED_STATES: ReadonlySet<PullRequestBadPractice["state"]> = new Set([
	"FIXED",
	"WONT_FIX",
	"WRONG",
]);

/**
 * Unresolved issue states - these require user action
 */
const UNRESOLVED_ISSUE_STATES: ReadonlySet<PullRequestBadPractice["state"]> = new Set([
	"CRITICAL_ISSUE",
	"NORMAL_ISSUE",
	"MINOR_ISSUE",
]);

/**
 * Check if a bad practice state represents an unresolved issue that can be resolved
 */
export function isUnresolvedIssue(state: PullRequestBadPractice["state"]): boolean {
	return UNRESOLVED_ISSUE_STATES.has(state);
}

/**
 * Check if a bad practice state represents a resolved issue
 */
export function isResolvedState(state: PullRequestBadPractice["state"]): boolean {
	return RESOLVED_STATES.has(state);
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

	const badPractices = allBadPractices.filter((badPractice) =>
		isUnresolvedIssue(badPractice.state),
	);

	const resolvedPractices = allBadPractices.filter((badPractice) =>
		isResolvedState(badPractice.state),
	);

	return { goodPractices, badPractices, resolvedPractices };
}
