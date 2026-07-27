import type { LlmUsageByDay, LlmUsageByJobType } from "@/api/types.gen";
import { asDate } from "@/lib/dates";

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

/**
 * Whether `month` *is* this month — the question the cap editors and the "at today's rate" hint turn
 * on. Equality, never `>=`: those two ask different things and only one of them is about now.
 * A month past this one is not the current month by any reading, and treating it as one would put a
 * live cap editor and a live rate over a report that cannot exist.
 */
export function isCurrentMonthUtc(month: string): boolean {
	return month === currentMonthUtc();
}

/**
 * Whether the stepper may move forward from `month`. ISO `yyyy-MM` compares lexicographically, so
 * this is a real "earlier than now" — and it stays false past it, which {@link isCurrentMonthUtc}
 * negated would not.
 */
export function canStepForwardFrom(month: string): boolean {
	return month < currentMonthUtc();
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

/** A day bucket as `Jul 22`. An unparseable day renders a dash rather than `Invalid Date`. */
export function formatUsageDay(value: LlmUsageByDay["day"]): string {
	const date = asDate(value);
	if (!date) return "–";
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
 *
 * Display only, and that is a rule rather than a description. Money is exact decimal on the server
 * and a binary64 `number` here (see the API description's "Money and exact decimals"), so nothing
 * this returns may decide anything: whether work is actually held back is `paused` on the payload,
 * and whether spend is within its cap is the server's verdict. A meter that read 99.9997% while the
 * gate was shut would be a rendering wart; a meter that *decided* would be a bug.
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
	// Injected, where {@link isCurrentMonthUtc} reads the real clock. Both are called only by the two
	// usage routes, which pass the same real `new Date()` into this one and hand `isCurrentMonth` down
	// as a prop — so the two clocks are the same clock in production, and a story that injects a
	// different `now` also passes its own `isCurrentMonth` rather than deriving one.
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
