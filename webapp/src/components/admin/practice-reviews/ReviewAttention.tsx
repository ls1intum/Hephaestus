import { CircleCheckIcon, ClipboardCheckIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "cn";
import type { ReviewFeedback } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { statusToneClass } from "@/components/common/status-def";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { Section } from "@/components/layout/Section";
import { buttonVariants } from "@/components/ui/button";

import { feedbackLevel } from "./review-levels";
import type { ReviewListTarget } from "./review-outcomes";
import type { ReviewSectionState } from "./review-states";
import { ReviewDayTime } from "./ReviewDayTime";
import { ReviewResultsSkeleton } from "./ReviewResultsSkeleton";

export interface AttentionGroup<TState> {
	state: TState;
	/** Every row the group counts. */
	list: ReviewListTarget;
	/**
	 * What the count is out of over the same window: the reviews that finished, or the feedback that
	 * was sent. Absent while it loads, when the count stands alone.
	 */
	of?: number;
}

export interface ReviewAttentionProps {
	/**
	 * Feedback awaiting approval, oldest first and whenever it was composed: a decision owed has no
	 * range, and the one waiting longest is the one to take next.
	 */
	approvals: { state: ReviewSectionState<ReviewFeedback> };
}

/** A range by name, as a sentence leads with it and ends with it: "Last 30 days", "in the last 30 days". */
export interface AttentionRange {
	label: string;
	during: string;
}

/**
 * Only what asks something of an admin: the decisions owed, as one way to work through them oldest
 * first. What failed is counted where it happened, over the range: failed reviews under What the
 * reviews did, results not processed and failed deliveries under Delivery. With nothing owed, one
 * line says so, so an empty section never reads as broken.
 */
export function ReviewAttention({ approvals }: ReviewAttentionProps) {
	let body: ReactNode;
	if (approvals.state.status === "loading") {
		body = <ReviewResultsSkeleton label="Loading what needs you…" rows={1} />;
	} else if (approvals.state.status === "ready" && approvals.state.total === 0) {
		body = (
			<p className="flex items-center gap-2 text-sm text-muted-foreground">
				<CircleCheckIcon
					aria-hidden
					className={cn("size-4 shrink-0", statusToneClass("success"))}
				/>
				No feedback awaits your approval.
			</p>
		);
	} else {
		body = <Approvals group={approvals} />;
	}
	return (
		<Section title="Needs you" size="lg">
			{body}
		</Section>
	);
}

function Approvals({ group: { state } }: { group: ReviewAttentionProps["approvals"] }) {
	if (state.status === "error") {
		return (
			<QueryErrorAlert
				error={state.error}
				title="We could not load the feedback awaiting your approval"
				onRetry={state.onRetry}
			/>
		);
	}
	if (state.status !== "ready" || state.total === 0) {
		return null;
	}
	const [oldest] = state.items;
	const noun = state.total === 1 ? "piece of feedback" : "pieces of feedback";
	return (
		<Section
			level={3}
			size="sm"
			title={
				<>
					Awaiting your approval{" "}
					<span className="text-muted-foreground tabular-nums">{state.total}</span>
				</>
			}
		>
			{/* One way in: the queue, oldest first. Its level walks the rest in the same order, and the
			    Feedback list has every piece for whoever wants to pick one. */}
			{oldest && (
				<div className="flex flex-wrap items-center gap-3">
					<DetailStackLink
						entry={feedbackLevel(oldest.id)}
						// Opened as the queue, which the same level opened from a row is not.
						levelSearch={{ queue: "approvals" }}
						className={buttonVariants()}
					>
						<ClipboardCheckIcon aria-hidden />
						Review {state.total} {noun}
					</DetailStackLink>
					<p className="text-sm text-muted-foreground">
						The oldest has waited since <ReviewDayTime at={oldest.createdAt} />.
					</p>
				</div>
			)}
		</Section>
	);
}
