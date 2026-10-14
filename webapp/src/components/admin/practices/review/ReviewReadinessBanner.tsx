import type { ReactNode } from "react";

import type { BadgeVariant } from "@/components/common/status-def";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

import {
	REVIEW_READINESS_DEFS,
	type ReviewReadinessState,
	reviewReadinessDescription,
	reviewReadinessTone,
} from "./review-readiness";

/**
 * The registry speaks in badge tones because that is the shared vocabulary of status across the
 * practice surfaces; the Alert kit names four of its own. Kept as a total map so a tone added to the
 * registry fails `typecheck:webapp` here rather than falling back to a silent neutral banner.
 */
const ALERT_VARIANTS: Record<BadgeVariant, "default" | "destructive" | "success" | "warning"> = {
	default: "default",
	secondary: "default",
	outline: "default",
	destructive: "destructive",
	success: "success",
	warning: "warning",
};

export interface ReviewReadinessBannerProps {
	readiness: ReviewReadinessState;
	/** Where to fix it, on a page that is not the settings themselves. */
	children?: ReactNode;
}

/** Readiness does not imply that a review is active. */
export function ReviewReadinessBanner({ readiness, children }: ReviewReadinessBannerProps) {
	const {
		label,
		icon: ToneIcon,
		badgeVariant,
	} = REVIEW_READINESS_DEFS[reviewReadinessTone(readiness)];

	return (
		<Alert variant={ALERT_VARIANTS[badgeVariant]} role="status">
			<ToneIcon />
			<AlertTitle>{label}</AlertTitle>
			<AlertDescription>
				<p>{reviewReadinessDescription(readiness)}</p>
				{children}
			</AlertDescription>
		</Alert>
	);
}
