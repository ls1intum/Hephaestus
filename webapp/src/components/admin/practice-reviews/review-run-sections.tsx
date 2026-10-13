import { useId, useState } from "react";

import type {
	AgentJob,
	ArtifactTrace,
	PracticeGroup,
	PracticeTraceEntry,
	ReviewRunSummary,
	TracedSignal,
} from "@/api/types.gen";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { ResultCount } from "@/components/common/ResultCount";
import { SelectFilter } from "@/components/common/SelectFilter";
import { earlierReviewOf, runPractices } from "@/components/practice-trace/ReviewRunPracticeTable";
import { REVIEW_FILTER_MAX_LENGTH } from "@/components/practice-trace/trace-format";
import { TraceOutcomeBadge } from "@/components/practice-trace/TraceOutcomeBadge";
import {
	getGroupVisual,
	UNASSIGNED_GROUP_VISUAL,
} from "@/components/practice-vocabulary/group-visuals";
import { GroupName } from "@/components/practice-vocabulary/GroupName";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { hasText } from "@/lib/text";

import {
	feedbackSlots,
	observationSlots,
	type OutcomeSlot,
} from "@/components/admin/practice-reviews/review-outcomes";

/**
 * What was recorded about the reviewed work, read for this review — or `none`, when the review
 * names no work, or nothing about the work was ever recorded.
 */
export type RunTraceState = PanelState<{ trace: ArtifactTrace }> | { status: "none" };

/** The review stopped before its end, so what it recorded may be only part of it. */
export function endedEarly(job: AgentJob): boolean {
	return job.status === "FAILED" || job.status === "TIMED_OUT" || job.status === "CANCELLED";
}

/** The moment on the work that started this review, when the trace has it. */
export function startingMoment(trace: RunTraceState, reviewId: string): TracedSignal | undefined {
	return trace.status === "ready"
		? trace.trace.signals.find((signal) => signal.reviewId === reviewId)
		: undefined;
}

const UNGROUPED = "Unassigned";

function groupNameOf(entry: PracticeTraceEntry): string {
	return hasText(entry.groupName) ? entry.groupName : UNGROUPED;
}

/** "1 met observation, 3 not met observations", or `none` when nothing was counted. */
function slotText(slots: readonly OutcomeSlot[], none: string): string {
	const counted = slots.filter((slot) => slot.count > 0);
	return counted.length === 0
		? none
		: counted.map((slot) => `${slot.count} ${slot.label}`).join(", ");
}

/** The review's counts, said once, as the head line of its practices. */
export function reviewCountsLine(summary: ReviewRunSummary): string {
	return `${slotText(observationSlots(summary.observations), "No observations")} · Feedback: ${slotText(
		feedbackSlots(summary.feedback),
		"none composed",
	)}`;
}

/** What the sheet needs of a group: which it is, and the icon and colour its name is drawn in. */
export type PracticeGroupLook = Pick<PracticeGroup, "slug" | "icon" | "color">;

interface ReviewPracticesProps {
	job: AgentJob;
	trace: RunTraceState;
	summary: ReviewRunSummary | undefined;
	/** The workspace's groups, for the colour and icon each group's name is drawn in. */
	groups?: readonly PracticeGroupLook[] | undefined;
}

/**
 * Every practice the review answered, with its state and the reason for it: the one list of what
 * the review judged, including the practices it did not reach or could not assess. What it saw and
 * what became of it are other records, so they are its trace; a practice's autonomy is the
 * practice's. A list rather than a table, so a row reflows at 390px instead of scrolling.
 */
export function ReviewPractices({
	job,
	trace,
	summary,
	groups: workspaceGroups,
}: ReviewPracticesProps) {
	const [group, setGroup] = useState<string | undefined>();
	const [name, setName] = useState("");
	const nameId = useId();
	if (trace.status !== "ready") {
		return (
			<div className="space-y-3">
				{summary !== undefined && <p className="text-sm">{reviewCountsLine(summary)}</p>}
				{trace.status === "loading" && (
					<div aria-hidden className="space-y-2">
						<Skeleton className="h-5 w-64" />
						<Skeleton className="h-5 w-56" />
					</div>
				)}
				{trace.status === "error" && (
					<QueryErrorAlert
						error={trace.error}
						title="We could not load what was recorded about this work"
						onRetry={trace.onRetry}
					/>
				)}
				{trace.status === "none" && (
					<p className="text-sm text-muted-foreground">
						Nothing was recorded about the work this review looked at, so there is no practice to
						list.
					</p>
				)}
			</div>
		);
	}
	const entries = runPractices(trace.trace.practices, trace.trace.signals, job.id);
	const groups = [...new Set(entries.map(groupNameOf))].sort((left, right) =>
		left.localeCompare(right),
	);
	const needle = name.trim().toLowerCase();
	const rows = entries.filter(
		(entry) =>
			(group === undefined || groupNameOf(entry) === group) &&
			(needle === "" || entry.practiceName.toLowerCase().includes(needle)),
	);
	const hasFilter = group !== undefined || needle !== "";
	const running = job.status === "QUEUED" || job.status === "RUNNING";
	let empty = "This review reached no practice, so there is nothing to list here.";
	if (entries.length > 0) {
		empty = "No practices match your filters.";
	} else if (running) {
		empty = "No practice results have been recorded yet.";
	}
	return (
		<div className="flex min-w-0 flex-col gap-3">
			{summary !== undefined && <p className="text-sm">{reviewCountsLine(summary)}</p>}
			{entries.length > 1 && (
				<FilterToolbar
					hasFilter={hasFilter}
					onReset={() => {
						setGroup(undefined);
						setName("");
					}}
				>
					<SelectFilter
						label="Group"
						allLabel="All groups"
						options={groups.map((value) => ({ value, label: value }))}
						value={group}
						onChange={setGroup}
					/>
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
							entry={entry}
							reviewId={job.id}
							group={workspaceGroups?.find((candidate) => candidate.slug === entry.groupSlug)}
						/>
					))}
				</ul>
			)}
		</div>
	);
}

/** Why a practice stands as it does in this review, in the review's own terms. */
function practiceReason(entry: PracticeTraceEntry, reviewId: string): string {
	if (earlierReviewOf(entry, reviewId) !== undefined) {
		return "Answered by an earlier review of the same code.";
	}
	if (entry.outcome === "REVIEWED") {
		return entry.observationCount === 1
			? "1 observation"
			: `${entry.observationCount} observations`;
	}
	return entry.explanation;
}

/**
 * One practice: its state as a coloured badge, its name over its group in the group's colour, and
 * the reason for its state. How the review answered it, observation by observation, is its trace's:
 * counted verdicts here cost a read of every observation and said little the state does not.
 */
function PracticeRow({
	entry,
	reviewId,
	group,
}: {
	entry: PracticeTraceEntry;
	reviewId: string;
	group: PracticeGroupLook | undefined;
}) {
	const visual =
		group === undefined ? UNASSIGNED_GROUP_VISUAL : getGroupVisual(group.icon, group.color);
	return (
		<li data-practice-outcome={entry.outcome} className="@container px-3 py-2.5 text-sm">
			<div className="grid min-w-0 items-start gap-x-4 gap-y-1.5 @lg:grid-cols-[9rem_minmax(0,14rem)_minmax(0,1fr)]">
				<span>
					<TraceOutcomeBadge outcome={entry.outcome} />
				</span>
				<span className="flex min-w-0 flex-col items-start gap-0.5">
					<span className="font-medium break-words">{entry.practiceName}</span>
					<GroupName
						name={groupNameOf(entry)}
						icon={visual.Icon}
						pill={group === undefined ? undefined : visual.pill}
						className="text-xs"
					/>
				</span>
				<span className="min-w-0 text-pretty text-muted-foreground">
					{practiceReason(entry, reviewId)}
				</span>
			</div>
		</li>
	);
}
