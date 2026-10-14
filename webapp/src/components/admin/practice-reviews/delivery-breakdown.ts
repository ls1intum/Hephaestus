import type { ReasonCount, ReviewFeedbackCounts } from "@/api/types.gen";
import { type StatusDef, statusValues } from "@/components/common/status-def";
import { DELIVERY_STATE_DEFS } from "@/components/practice-vocabulary/delivery-state-defs";
import { FEEDBACK_LOCATION_DEFS } from "@/components/practice-vocabulary/feedback-location-defs";
import {
	WITHHOLDING_FAMILY_DEFS,
	WITHHOLDING_REASON_DEFS,
	type WithholdingReason,
	withholdingFamily,
} from "@/components/practice-vocabulary/withholding-defs";

import { type OutcomeScope, type ReviewListTarget, scopeSearch } from "./review-outcomes";

/**
 * The delivery states the Feedback tile counts as delivered. A package of which only some copies
 * posted is still delivered; its copy counts say which.
 */
export const DELIVERED_STATES = ["DELIVERED"] as const;

/**
 * One row of the breakdown: a location or a reason, in the words and icon of the Feedback table's
 * filter for it, its count, and the table filtered to exactly the rows it counts. A row without a
 * target has no filter that selects exactly it.
 */
export interface BreakdownRow {
	key: string;
	def: StatusDef;
	count: number;
	target?: ReviewListTarget;
}

function isWithholdingReason(reason: string): reason is WithholdingReason {
	return Object.hasOwn(WITHHOLDING_REASON_DEFS, reason);
}

/**
 * Delivered feedback by location, in the order and words of the Location filter, each opening the
 * Feedback table filtered to the delivered states, that location and the range. Only the locations
 * that delivered something are rows.
 */
export function deliveredByLocation(
	counts: readonly ReasonCount[],
	scope: OutcomeScope,
): BreakdownRow[] {
	return statusValues(FEEDBACK_LOCATION_DEFS).flatMap((location) => {
		const count = counts.find((entry) => entry.reason === location)?.count ?? 0;
		return count === 0
			? []
			: [
					{
						key: location,
						def: FEEDBACK_LOCATION_DEFS[location],
						count,
						target: {
							list: "feedback",
							search: {
								...scopeSearch(scope, "feedback"),
								state: [...DELIVERED_STATES],
								location,
							},
						},
					},
				];
	});
}

/**
 * Withheld feedback by reason, in the groups and words of the "Why withheld" filter, each opening
 * the Feedback table filtered to withheld feedback of that group and the range. Feedback an admin
 * rejected is not sent either, as the Feedback tile counts it, and is its own row; withheld feedback
 * whose reason this build does not know is one too, without a link, since no filter selects it
 * alone. Only the rows with a count are drawn.
 */
export function withheldByReason(
	reasons: readonly ReasonCount[],
	feedback: Pick<ReviewFeedbackCounts, "rejected">,
	scope: OutcomeScope,
): BreakdownRow[] {
	const range = scopeSearch(scope, "feedback");
	const families = statusValues(WITHHOLDING_FAMILY_DEFS).map((family): BreakdownRow => ({
		key: family,
		def: WITHHOLDING_FAMILY_DEFS[family],
		count: reasons
			.filter(
				(entry) => isWithholdingReason(entry.reason) && withholdingFamily(entry.reason) === family,
			)
			.reduce((sum, entry) => sum + entry.count, 0),
		target: {
			list: "feedback",
			search: { ...range, state: ["WITHHELD"], withheldFamily: [family] },
		},
	}));
	const rows: BreakdownRow[] = [
		...families,
		{
			key: "REJECTED",
			def: DELIVERY_STATE_DEFS.REJECTED,
			count: feedback.rejected,
			target: { list: "feedback", search: { ...range, state: ["REJECTED"] } },
		},
		{
			key: "UNKNOWN",
			def: { ...DELIVERY_STATE_DEFS.WITHHELD, label: "Reason not recorded" },
			// From the reasons read itself, so it never disagrees with the reason rows above it.
			count: reasons
				.filter((entry) => !isWithholdingReason(entry.reason))
				.reduce((sum, entry) => sum + entry.count, 0),
		},
	];
	return rows.filter((row) => row.count > 0);
}
