import { useId, useState } from "react";

import type { ReviewPractice, ReviewSummary } from "@/api/types.gen";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { ResultCount } from "@/components/common/ResultCount";
import { StatusBadge } from "@/components/common/StatusBadge";
import { PracticeScriptTraceLine } from "@/components/practice-trace/PracticeScriptTraceLine";
import { REVIEW_FILTER_MAX_LENGTH } from "@/components/practice-trace/trace-format";
import { REVIEW_PRACTICE_STATUS_DEFS } from "@/components/practice-vocabulary/review-status-defs";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { hasText } from "@/lib/text";

import {
	feedbackSlots,
	type OutcomeSlot,
	resultSlots,
} from "@/components/admin/practice-reviews/review-outcomes";

function slotText(slots: readonly OutcomeSlot[], none: string): string {
	const counted = slots.filter((slot) => slot.count > 0);
	return counted.length === 0
		? none
		: counted.map((slot) => `${slot.count} ${slot.label}`).join(", ");
}

export function reviewCountsLine(review: Pick<ReviewSummary, "results" | "feedback">): string {
	return `${slotText(resultSlots(review.results), "No results")} · Feedback: ${slotText(
		feedbackSlots(review.feedback),
		"none composed",
	)}`;
}

interface ReviewPracticesProps {
	workspaceSlug: string;
	review: Pick<ReviewSummary, "id" | "status" | "practices" | "results" | "feedback">;
}

export function ReviewPractices({ workspaceSlug, review }: ReviewPracticesProps) {
	const [name, setName] = useState("");
	const nameId = useId();
	const entries = review.practices;
	const needle = name.trim().toLowerCase();
	const rows = entries.filter(
		(entry) => needle === "" || entry.practiceName.toLowerCase().includes(needle),
	);
	const hasFilter = needle !== "";
	const running = review.status === "QUEUED" || review.status === "RUNNING";
	let empty = "This review reached no practice, so there is nothing to list here.";
	if (entries.length > 0) {
		empty = "No practices match your filters.";
	} else if (running) {
		empty = "Practices appear here as the review reaches them.";
	}
	return (
		<div className="flex min-w-0 flex-col gap-3">
			<p className="text-sm">{reviewCountsLine(review)}</p>
			{entries.length > 1 && (
				<FilterToolbar hasFilter={hasFilter} onReset={() => setName("")}>
					<div className="flex min-w-0 items-center gap-2">
						<Label htmlFor={nameId} className="shrink-0 text-muted-foreground">
							Practice
						</Label>
						<Input
							id={nameId}
							value={name}
							maxLength={REVIEW_FILTER_MAX_LENGTH}
							placeholder="Filter by name"
							className="w-56 max-w-full"
							onChange={(event) => setName(event.target.value)}
						/>
					</div>
				</FilterToolbar>
			)}
			{hasFilter && rows.length !== entries.length && (
				<ResultCount total={rows.length} noun={["practice", "practices"]} hasFilter />
			)}
			{rows.length === 0 ? (
				<p className="text-sm text-muted-foreground">{empty}</p>
			) : (
				<ul aria-label="Every practice in this review" className="divide-y rounded-xl border">
					{rows.map((entry) => (
						<PracticeRow
							key={entry.practiceSlug}
							workspaceSlug={workspaceSlug}
							entry={entry}
							reviewId={review.id}
						/>
					))}
				</ul>
			)}
		</div>
	);
}

const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`;

/**
 * Why a practice stands as it does in this review, in the review's own terms: its recorded reason
 * where it has one, otherwise what it recorded. A withheld practice says its reason, never a result.
 */
function practiceReason(entry: ReviewPractice): string | undefined {
	if (hasText(entry.reason)) {
		return entry.reason;
	}
	if (entry.status !== "DONE") {
		return undefined;
	}
	const { met, notMet, doesNotApply, couldNotDetermine } = entry.results;
	const results = met + notMet + doesNotApply + couldNotDetermine;
	const { reused, notReviewed } = entry.itemCounts;
	return [
		plural(results, "result", "results"),
		reused > 0 ? `${reused} reused from an earlier review of the same code` : undefined,
		notReviewed > 0 ? `${notReviewed} not reviewed` : undefined,
	]
		.filter(hasText)
		.join(" · ");
}

function PracticeRow({
	workspaceSlug,
	entry,
	reviewId,
}: {
	workspaceSlug: string;
	entry: ReviewPractice;
	reviewId: string;
}) {
	return (
		<li data-practice-status={entry.status} className="@container px-3 py-2.5 text-sm">
			<div className="grid min-w-0 items-start gap-x-4 gap-y-1.5 @lg:grid-cols-[9rem_minmax(0,14rem)_minmax(0,1fr)]">
				<span>
					<StatusBadge def={REVIEW_PRACTICE_STATUS_DEFS[entry.status]} />
				</span>
				<span className="min-w-0 font-medium break-words">{entry.practiceName}</span>
				<span className="flex min-w-0 flex-col gap-1 text-pretty text-muted-foreground">
					<span>{practiceReason(entry)}</span>
					{entry.script !== null && (
						<span className="text-xs">
							<PracticeScriptTraceLine
								workspaceSlug={workspaceSlug}
								practiceSlug={entry.practiceSlug}
								practiceName={entry.practiceName}
								reviewId={reviewId}
								run={entry.script}
								details={false}
							/>
						</span>
					)}
				</span>
			</div>
		</li>
	);
}
