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

/** Long day label for a UTC date, e.g. "July 22" — used by budget projections and reset dates. */
export function formatDayLabel(date: Date): string {
	return date.toLocaleDateString(undefined, {
		month: "long",
		day: "numeric",
		timeZone: "UTC",
	});
}

/**
 * The day a pause lifts by itself: the first of the month after `month`, in UTC. Budgets are
 * scoped to a UTC calendar month, so this is the honest "until when" in every pause banner.
 */
export function budgetResetDayLabel(month: string): string {
	const next = addMonths(month, 1);
	const [yearStr, monthStr] = next.split("-");
	return formatDayLabel(new Date(Date.UTC(Number(yearStr), Number(monthStr) - 1, 1)));
}

/**
 * Share of a cap consumed, in percent, or `undefined` when there is no cap to compare against.
 * A $0 cap is a supported state ("paused immediately") and reads as 100% used — only an absent
 * cap has no percentage to show.
 */
export function budgetUsedPercent(
	spendUsd: number,
	capUsd: number | undefined,
): number | undefined {
	if (capUsd == null) {
		return undefined;
	}
	return capUsd > 0 ? (spendUsd / capUsd) * 100 : 100;
}

/** At or above this share of a cap the page warns before the wall instead of only reporting it. */
export const BUDGET_WARN_PERCENT = 80;

export interface BudgetProjection {
	/** Spend at month end if the month's average daily pace holds, in USD. */
	projectedMonthEndUsd: number;
	/** UTC date the cap is projected to be reached, or `null` when the pace doesn't reach it. */
	reachedOn: Date | null;
}

/**
 * Straight-line burn-rate projection for a capped month: `spend / daysElapsed * daysInMonth`.
 *
 * Returns `null` — meaning "say nothing" — whenever the denominator is garbage rather than
 * guessing anyway: a month other than the one `now` falls in, the first two days of the month
 * (one busy afternoon would project a wildly wrong month), zero spend, or no positive cap.
 */
export function projectBudget(
	spendUsd: number,
	capUsd: number | undefined,
	month: string,
	now: Date,
): BudgetProjection | null {
	if (capUsd == null || capUsd <= 0 || spendUsd <= 0) {
		return null;
	}
	if (now.toISOString().slice(0, 7) !== month) {
		return null;
	}
	const daysElapsed = now.getUTCDate();
	if (daysElapsed < 3) {
		return null;
	}
	const [yearStr, monthStr] = month.split("-");
	const year = Number(yearStr);
	const monthIndex = Number(monthStr) - 1;
	// Day 0 of the following month is the last day of this one.
	const daysInMonth = new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
	const dailyRate = spendUsd / daysElapsed;
	const projectedMonthEndUsd = dailyRate * daysInMonth;
	if (projectedMonthEndUsd < capUsd) {
		return { projectedMonthEndUsd, reachedOn: null };
	}
	const dayReached = Math.min(Math.max(Math.ceil(capUsd / dailyRate), daysElapsed), daysInMonth);
	return {
		projectedMonthEndUsd,
		reachedOn: new Date(Date.UTC(year, monthIndex, dayReached)),
	};
}
