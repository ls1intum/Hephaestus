import type { ReactNode } from "react";

import type { BadgeVariant } from "@/components/common/status-def";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

import {
	REVIEW_READINESS_DEFS,
	type ReviewReadinessState,
	reviewReadinessDescription,
	reviewReadinessTone,
} from "./review-readiness";

// Badge and alert primitives use different tone sets.
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
		<Alert
			variant={ALERT_VARIANTS[badgeVariant]}
			role="note"
			aria-label="Practice review readiness"
		>
			<ToneIcon />
			<AlertTitle>{label}</AlertTitle>
			<AlertDescription>
				<p>{reviewReadinessDescription(readiness)}</p>
				{children}
			</AlertDescription>
		</Alert>
	);
}
