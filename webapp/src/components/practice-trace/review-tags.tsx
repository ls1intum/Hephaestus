import { StatusBadge } from "@/components/common/StatusBadge";
import { REVIEW_ORIGIN_DEFS } from "@/components/practice-vocabulary/review-origin-defs";
import { Badge } from "@/components/ui/badge";

import { reviewOrdinalLabel } from "./review-groups";

/**
 * The tags a review wears on the list and on its own level. None is a status tone: beside a real
 * review state, a toned badge would read as a second state.
 */
export function LatestReviewTag() {
	return <Badge variant="secondary">Latest</Badge>;
}

/** A review somebody asked for, in the review-origin registry's entry for the same fact. */
export function RequestedReviewTag() {
	return <StatusBadge def={REVIEW_ORIGIN_DEFS.REQUEST} />;
}

/**
 * "Review 2 of 3", or "Review 2" while the count is unknown, on a later review of the same work;
 * nothing on the first, or while unknown.
 */
export function ReviewOrdinalTag({
	position,
	total,
}: {
	position: number | undefined;
	/** How many reviews the work has, where the list knows it. */
	total?: number | undefined;
}) {
	const label = position === undefined ? undefined : reviewOrdinalLabel(position, total);
	if (label === undefined) {
		return null;
	}
	return <Badge variant="muted">{label}</Badge>;
}
