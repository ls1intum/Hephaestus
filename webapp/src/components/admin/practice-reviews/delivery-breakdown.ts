import type {
	ReviewFeedbackCounts,
	ReviewFeedbackLocationCounts,
	ReviewFeedbackReasonCount,
} from "@/api/types.gen";
import { type StatusDef, statusValues } from "@/components/common/status-def";
import { DELIVERY_STATE_DEFS } from "@/components/practice-vocabulary/delivery-outcome-defs";
import { FEEDBACK_LOCATION_DEFS } from "@/components/practice-vocabulary/feedback-location-defs";
import {
	WITHHOLDING_FAMILY_DEFS,
	withholdingFamily,
} from "@/components/practice-vocabulary/withholding-defs";

import { type OutcomeScope, type ReviewListTarget, scopeSearch } from "./review-outcomes";

/** The delivery states the Feedback tile counts as delivered: in full or in part. */
export const DELIVERED_STATES = ["DELIVERED", "PARTIALLY_DELIVERED"] as const;

/**
 * One row of the breakdown: a channel or a reason, in the words and icon of the Feedback table's
 * filter for it, its count, and the table filtered to exactly the rows it counts. A row without a
 * target has no filter that selects exactly it.
 */
export interface BreakdownRow {
	key: string;
	def: StatusDef;
	count: number;
	target?: ReviewListTarget;
}

/**
 * Delivered feedback by channel, in the order and words of the Channel filter, each opening the
 * Feedback table filtered to the delivered states, that channel and the range. Only the channels
 * that delivered something are rows.
 */
export function deliveredByChannel(
	counts: readonly ReviewFeedbackLocationCounts[],
	scope: OutcomeScope,
): BreakdownRow[] {
	return statusValues(FEEDBACK_LOCATION_DEFS).flatMap((location) => {
		const entry = counts.find((count) => count.location === location);
		const count =
			entry === undefined ? 0 : entry.feedback.delivered + entry.feedback.partiallyDelivered;
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
								deliveryState: [...DELIVERED_STATES],
								location: [location],
							},
						},
					},
				];
	});
}

/**
 * Withheld feedback by reason, in the groups and words of the "Why withheld" filter, each opening
 * the Feedback table filtered to withheld feedback of that group and the range. Feedback an admin
 * rejected is withheld too, as the Feedback tile counts it, and is its own row; withheld feedback
 * whose reason is not recorded is one too, without a link, since no filter selects it alone. Only
 * the rows with a count are drawn.
 */
export function withheldByReason(
	reasons: readonly ReviewFeedbackReasonCount[],
	feedback: Pick<ReviewFeedbackCounts, "discarded">,
	scope: OutcomeScope,
): BreakdownRow[] {
	const range = scopeSearch(scope, "feedback");
	const families = statusValues(WITHHOLDING_FAMILY_DEFS).map((family): BreakdownRow => ({
		key: family,
		def: WITHHOLDING_FAMILY_DEFS[family],
		count: reasons
			.filter((entry) => entry.reason !== undefined && withholdingFamily(entry.reason) === family)
			.reduce((sum, entry) => sum + entry.count, 0),
		target: {
			list: "feedback",
			search: { ...range, deliveryState: ["SUPPRESSED"], withheldFamily: [family] },
		},
	}));
	const rows: BreakdownRow[] = [
		...families,
		{
			key: "DISCARDED",
			def: DELIVERY_STATE_DEFS.DISCARDED,
			count: feedback.discarded,
			target: { list: "feedback", search: { ...range, deliveryState: ["DISCARDED"] } },
		},
		{
			key: "UNRECORDED",
			def: { ...DELIVERY_STATE_DEFS.SUPPRESSED, label: "Reason not recorded" },
			// From the reasons read itself, so it never disagrees with the reason rows above it.
			count: reasons.find((entry) => entry.reason === undefined)?.count ?? 0,
		},
	];
	return rows.filter((row) => row.count > 0);
}
