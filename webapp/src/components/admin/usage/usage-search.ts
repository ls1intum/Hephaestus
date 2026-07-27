import { z } from "zod";
import { currentMonthUtc } from "./usage-utils";

const ISO_MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * URL state for the AI-usage surfaces: the month being reported on lives in the query string, for the
 * same reason the audit filters do — a spend report exists to be cited. An admin forwards
 * `…/admin/usage?month=2026-06` to finance and they see June; Back steps months; reload stays put.
 *
 * `.catch()` rather than a hard rejection, matching `audit-search.ts`: a hand-typed or stale `?month=`
 * must still open the report on a sensible month instead of rendering an error page.
 */
export const usageSearchSchema = z.object({
	/**
	 * ISO `yyyy-MM`, UTC — the server's own month bucketing.
	 *
	 * Optional, and absent means *the current month* rather than a month frozen into the link. That
	 * keeps a bare `…/admin/usage` link (the sidebar's, the budget banner's) meaning "this month"
	 * forever, while a month someone actually navigated to is written down and survives a reload.
	 *
	 * Clamped to now because there is no such thing as a future month's spend and the stepper already
	 * refuses to walk past it — a link must not reach a state the UI cannot.
	 */
	month: z
		.string()
		.regex(ISO_MONTH)
		// ISO yyyy-MM compares lexicographically, so this is a real "no later than now".
		.transform((value) => (value > currentMonthUtc() ? currentMonthUtc() : value))
		.optional()
		.catch(undefined),
});

export type UsageSearch = z.infer<typeof usageSearchSchema>;

/** The month on screen: what the URL says, or this month when it says nothing. */
export function monthOf(search: UsageSearch): string {
	return search.month ?? currentMonthUtc();
}

/** The params `retainSearchParams` carries across navigations within a usage surface. */
export const USAGE_SEARCH_PARAMS: (keyof UsageSearch)[] = ["month"];
