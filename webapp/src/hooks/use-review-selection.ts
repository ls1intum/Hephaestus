import { skipToken, useQuery } from "@tanstack/react-query";

import {
	getAgentJobOptions,
	getPracticeReviewFeedbackOptions,
	getPracticeReviewResultOptions,
	listPracticesOptions,
	listTracedArtifactsOptions,
} from "@/api/@tanstack/react-query.gen";
import type { ReviewedWorkRef } from "@/api/types.gen";
import { subjectLabel } from "@/components/admin/practice-reviews/review-format";
import { type RecordRef, selectionRecord } from "@/components/admin/practice-reviews/review-layers";
import type { ReviewScope } from "@/components/admin/practice-reviews/review-scope";
import {
	type ReviewSelection,
	readScopedRecord,
} from "@/components/admin/practice-reviews/review-selection";
import type { ReviewSelectionName } from "@/components/admin/practice-reviews/ReviewSelectionBar";
import { type PanelState, panelState } from "@/components/common/panel-state";
import { useNow } from "@/components/common/use-now";
import { reviewedWorkName } from "@/lib/artifact-kinds";
import { formatDayTime } from "@/lib/dates";
import { hasText } from "@/lib/text";

/** One piece of work, as the work list filters by it. */
export interface SelectedWork {
	artifactKind: string;
	artifactId: number;
}

/** The piece of work the selection names, or `undefined` for a review that names none. */
export type SelectedWorkState = PanelState<{ work: SelectedWork | undefined }>;

export interface ReviewSelectionRead {
	/** The record the scope narrows to, picked with a crosshair or filtered by. */
	selection: ReviewSelection | undefined;
	/** It was picked with a crosshair: the selection bar shows it, not a filter pill. */
	picked: boolean;
	/** What the selection bar calls it. */
	name: ReviewSelectionName;
	/** What a filter pill calls it, after the kind it names: "#12 · 7 Oct, 14:02" for a review. */
	filterName: string | undefined;
	/** The tracked record, whose trace "Show trace" opens. */
	trace: RecordRef | undefined;
	/** The work the selection names, which the work list narrows to. */
	work: SelectedWorkState;
}

type SelectionScope = Pick<
	ReviewScope,
	| "agentJobId"
	| "practiceResultId"
	| "feedbackId"
	| "selectedPractice"
	| "artifactKind"
	| "artifactId"
	| "selected"
>;

/**
 * The record the scope narrows to — the selection, or the same names set as a filter — read from its
 * own record: its name, the review its trace opens, and the work it names. A review and a result name their work, so the work list can narrow to it
 * without a filter of its own. Every tab and the layout read the same queries, so a switch between
 * tabs asks for nothing again.
 */
export function useReviewSelection(
	workspaceSlug: string,
	scope: SelectionScope,
): ReviewSelectionRead {
	const read = useRecordRead(workspaceSlug, scope);
	const named = read.name.status === "ready" ? read.name.name : undefined;
	return {
		...read,
		filterName: read.selection?.kind === "review" ? named?.replace(REVIEW_NAME_PREFIX, "") : named,
	};
}

/** The selection bar says "Review of #12 · …"; a pill already says "Review:". */
const REVIEW_NAME_PREFIX = /^Review of /u;

function useRecordRead(
	workspaceSlug: string,
	scope: SelectionScope,
): Omit<ReviewSelectionRead, "filterName"> {
	const selection = readScopedRecord(scope);
	const picked = selection !== undefined && scope.selected === true;
	const nowMs = useNow();
	const path = { workspaceSlug };

	const practiceResultId = selection?.kind === "result" ? selection.practiceResultId : undefined;
	const resultOptions = getPracticeReviewResultOptions({
		path: { ...path, practiceResultId: practiceResultId ?? "" },
	});
	const resultQuery = useQuery({
		...resultOptions,
		queryFn: practiceResultId === undefined ? skipToken : resultOptions.queryFn,
	});

	const jobId = selection?.kind === "review" ? selection.agentJobId : undefined;
	const jobOptions = getAgentJobOptions({ path: { ...path, jobId: jobId ?? "" } });
	const jobQuery = useQuery({
		...jobOptions,
		queryFn: jobId === undefined ? skipToken : jobOptions.queryFn,
	});

	const feedbackId = selection?.kind === "feedback" ? selection.feedbackId : undefined;
	const feedbackOptions = getPracticeReviewFeedbackOptions({
		path: { ...path, feedbackId: feedbackId ?? "" },
	});
	const feedbackQuery = useQuery({
		...feedbackOptions,
		queryFn: feedbackId === undefined ? skipToken : feedbackOptions.queryFn,
	});

	const practicesOptions = listPracticesOptions({ path });
	const practicesQuery = useQuery({
		...practicesOptions,
		queryFn: selection?.kind === "practice" ? practicesOptions.queryFn : skipToken,
	});

	const work = selection?.kind === "work" ? selection : undefined;
	const workQuery = { artifactKind: work?.artifactKind, artifactId: work?.artifactId, size: 1 };
	const tracedOptions = listTracedArtifactsOptions({ path, query: workQuery });
	const tracedQuery = useQuery({
		...tracedOptions,
		queryFn: work === undefined ? skipToken : tracedOptions.queryFn,
	});
	const trace = selection === undefined ? undefined : selectionRecord(selection);

	switch (selection?.kind) {
		case undefined: {
			return {
				selection,
				picked,
				name: { status: "loading" },
				trace: undefined,
				work: { status: "ready", work: undefined },
			};
		}
		case "work": {
			// Work nothing was recorded about has no row in the work list: the kind alone names it.
			const named = tracedQuery.data?.content?.[0]?.reviewedWork;
			return {
				selection,
				picked,
				name: panelState(tracedQuery, () => ({
					status: "ready" as const,
					name:
						named === undefined
							? reviewedWorkName({ kind: selection.artifactKind, label: "" })
							: workName(named),
				})),
				trace,
				work: {
					status: "ready",
					work: { artifactKind: selection.artifactKind, artifactId: selection.artifactId },
				},
			};
		}
		case "review": {
			return {
				selection,
				picked,
				name: panelState(jobQuery, (job) => ({
					status: "ready" as const,
					name: `Review of ${job.target.reviewedWork?.label ?? job.target.title} · ${formatDayTime(job.createdAt, new Date(nowMs))}`,
				})),
				trace,
				work: panelState(jobQuery, (job) => ({
					status: "ready" as const,
					work: workOf(job.target.reviewedWork),
				})),
			};
		}
		case "result": {
			return {
				selection,
				picked,
				name: panelState(resultQuery, (result) => ({
					status: "ready" as const,
					name: result.summary,
				})),
				trace,
				work: panelState(resultQuery, (result) => ({
					status: "ready" as const,
					work: workOf(result.reviewedWork),
				})),
			};
		}
		case "feedback": {
			return {
				selection,
				picked,
				name: panelState(feedbackQuery, (feedback) => ({
					status: "ready" as const,
					name: `Feedback for ${subjectLabel(feedback.recipient)} · ${formatDayTime(feedback.createdAt, new Date(nowMs))}`,
				})),
				trace,
				work: panelState(feedbackQuery, (feedback) => ({
					status: "ready" as const,
					work: workOf(feedback.reviewedWork),
				})),
			};
		}
		case "practice": {
			return {
				selection,
				picked,
				name: panelState(practicesQuery, (practices) => ({
					status: "ready" as const,
					name:
						practices.find((practice) => practice.slug === selection.practiceSlug)?.name ??
						selection.practiceSlug,
				})),
				trace,
				work: { status: "ready", work: undefined },
			};
		}
	}
}

function workOf(reviewedWork: ReviewedWorkRef | undefined): SelectedWork | undefined {
	const artifactId = Number(reviewedWork?.id);
	return reviewedWork !== undefined && Number.isSafeInteger(artifactId) && artifactId > 0
		? { artifactKind: reviewedWork.kind, artifactId }
		: undefined;
}

/** "Pull request #1423 · Fix the parser", or the name alone where the work has no title. */
function workName(reviewedWork: ReviewedWorkRef): string {
	return [reviewedWorkName(reviewedWork), reviewedWork.title].filter(hasText).join(" · ");
}
