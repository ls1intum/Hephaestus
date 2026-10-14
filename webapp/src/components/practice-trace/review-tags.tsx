import { StatusBadge } from "@/components/common/StatusBadge";
import { REVIEW_ORIGIN_DEFS } from "@/components/practice-vocabulary/review-origin-defs";
import { Badge } from "@/components/ui/badge";

import { reviewOrdinalLabel } from "./review-groups";

export function LatestReviewTag() {
	return <Badge variant="secondary">Latest</Badge>;
}

export function RequestedReviewTag() {
	return <StatusBadge def={REVIEW_ORIGIN_DEFS.REQUEST} />;
}

export function ReviewOrdinalTag({
	position,
	total,
}: {
	position: number | undefined;
	total?: number | undefined;
}) {
	const label = position === undefined ? undefined : reviewOrdinalLabel(position, total);
	if (label === undefined) {
		return null;
	}
	return <Badge variant="muted">{label}</Badge>;
}
