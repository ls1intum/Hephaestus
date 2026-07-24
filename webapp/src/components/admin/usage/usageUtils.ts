import type { LlmUsageByDay, LlmUsageByJobType } from "@/api/types.gen";

export type LlmJobType = LlmUsageByJobType["jobType"];

export const JOB_TYPE_LABELS: Record<LlmJobType, string> = {
	PULL_REQUEST_REVIEW: "PR review",
	ISSUE_REVIEW: "Issue review",
	CONVERSATION_REVIEW: "Conversation review",
	MENTOR_TURN: "Mentor turn",
};

/** Current calendar month in UTC as ISO `yyyy-MM` — matches the server's month bucketing. */
export function currentMonthUtc(): string {
	return new Date().toISOString().slice(0, 7);
}

/** Shift an ISO `yyyy-MM` month by `delta` months (UTC-safe). */
export function addMonths(month: string, delta: number): string {
	const [yearStr, monthStr] = month.split("-");
	const date = new Date(Date.UTC(Number(yearStr), Number(monthStr) - 1 + delta, 1));
	return date.toISOString().slice(0, 7);
}

/** Human label for an ISO `yyyy-MM` month, e.g. "July 2026". */
export function formatMonthLabel(month: string): string {
	const [yearStr, monthStr] = month.split("-");
	return new Date(Date.UTC(Number(yearStr), Number(monthStr) - 1, 1)).toLocaleDateString(
		undefined,
		{ month: "long", year: "numeric", timeZone: "UTC" },
	);
}

/**
 * The generated client types date fields as `Date`, but the response transformers aren't wired
 * into the SDK calls, so at runtime they arrive as ISO strings — coerce defensively (the
 * established pattern in AdminWorkspacesTable / SessionsSection).
 */
export function formatUsageDay(value: LlmUsageByDay["day"]): string {
	const date = value instanceof Date ? value : new Date(value);
	return date.toLocaleDateString(undefined, {
		month: "short",
		day: "numeric",
		timeZone: "UTC",
	});
}
