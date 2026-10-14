import { hashKey, skipToken, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";

import {
	getItemFactsOptions,
	getItemTraceOptions,
	getPracticeDefinitionOptionsOptions,
	getReviewLineageOptions,
	getReviewOptions,
	getReviewProblemOptions,
	listPracticeGroupsOptions,
	listPracticesOptions,
	listPracticeVersionsOptions,
	listReviewResultsOptions,
	listReviewsOptions,
} from "@/api/@tanstack/react-query.gen";
import type {
	ItemEvent,
	ItemTrace,
	Practice,
	ReviewLineage,
	ReviewRequestOutcome,
} from "@/api/types.gen";
import { eventLabel } from "@/components/admin/practice-editor/review-events";
import { FeedbackLevel } from "@/components/admin/practice-reviews/FeedbackLevel";
import { REVIEWABLE_ON_DEMAND } from "@/components/admin/practice-reviews/job-utils";
import {
	PracticeLevel,
	type PracticeLevelProps,
} from "@/components/admin/practice-reviews/PracticeLevel";
import { ProblemLevel } from "@/components/admin/practice-reviews/ProblemLevel";
import { REQUEST_BLOCKED } from "@/components/admin/practice-reviews/RequestReviewAction";
import { ResultLevel } from "@/components/admin/practice-reviews/ResultLevel";
import { levelRecord, type RecordRef } from "@/components/admin/practice-reviews/review-layers";
import {
	feedbackLevel,
	PRACTICE_REVIEW_LEVEL_LABELS,
	type PracticeReviewLevel,
} from "@/components/admin/practice-reviews/review-levels";
import { ACTIVE_REVIEW_POLL_MS } from "@/components/admin/practice-reviews/review-search";
import type { TraceNode } from "@/components/admin/practice-reviews/review-trace";
import { ReviewLevel } from "@/components/admin/practice-reviews/ReviewLevel";
import { useTraceDisplay } from "@/components/admin/practice-reviews/trace-display";
import { ALL_STEPS, type TraceFilter } from "@/components/admin/practice-reviews/trace-filter";
import { type TraceChainState, TraceLevel } from "@/components/admin/practice-reviews/TraceLevel";
import { WorkLevel } from "@/components/admin/practice-reviews/WorkLevel";
import { panelState } from "@/components/common/panel-state";
import { detailStackKey, stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { levelPathAt } from "@/components/layout/detail-drawer/level-path";
import { RefusalFixLink } from "@/components/practice-trace/RefusalFixLink";
import { TraceRefusalAlert } from "@/components/practice-trace/TraceRefusalAlert";
import { isActiveReview } from "@/components/practice-vocabulary/review-status-defs";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import {
	useApprovalQueue,
	useFeedbackController,
	useNextInApprovalQueue,
} from "@/hooks/use-feedback-controller";
import { useRequestPracticeReview } from "@/hooks/use-request-practice-review";
import { useResultController } from "@/hooks/use-result-controller";
import { useReviewController } from "@/hooks/use-review-controller";
import { type ReviewLineageTarget, useReviewLineage } from "@/hooks/use-review-lineage";
import { useSearchState } from "@/lib/search-params";

export interface PracticeReviewDrawerProps {
	workspaceSlug: string;
	/** The open levels: a record's sheet, a trace, or a trace with one record's sheet over it. */
	stack: PracticeReviewLevel[];
	onClose: (depth: number) => void;
	/** Opens a record's sheet over the trace, the one level a trace has above it. */
	onOpenOverTrace: (record: RecordRef) => void;
	/** The feedback level in front is the approval queue, opened as one from Needs you. */
	approvalQueue: boolean;
}

const labelOf = (entry: PracticeReviewLevel) => PRACTICE_REVIEW_LEVEL_LABELS[entry.kind];

/**
 * What Practice reviews opens over its tabs (`review-layers.ts`): one record's sheet from a row, a
 * record's trace from a row's Trace, and from a step of the trace that step's record's sheet over
 * it. Each level reads its own record. Every level is the stack's one width, as on the Practice
 * profile, so a sheet over the trace leaves the same column of it in view as any level over another.
 */
export function PracticeReviewDrawer({
	workspaceSlug,
	stack,
	onClose,
	onOpenOverTrace,
	approvalQueue,
}: PracticeReviewDrawerProps) {
	const pathAt = levelPathAt(stack, { pageLabel: "Practice reviews", labelOf, onClose });
	const setSearch = useSearchState();
	const router = useRouter();
	const moveOn = (decided: string, next: string | undefined) => {
		if (next === undefined) {
			if (stillInFront(router.latestLocation.search, decided)) {
				onClose(0);
			}
			return;
		}
		// Written over the current history entry and keeping its mark: see `OpenInStackOptions.swap`.
		void setSearch(
			(previous) =>
				stillInFront(previous, decided)
					? { ...previous, detail: [detailStackKey(feedbackLevel(next))] }
					: previous,
			{ state: true, replace: true },
		);
	};

	return (
		<DetailDrawerStack stack={stack} onClose={onClose}>
			{(entry, level) => {
				const record = levelRecord(entry);
				if (record === undefined) {
					return null;
				}
				const shared = { workspaceSlug, nested: level.nested, path: pathAt(level.depth) };
				if (entry.kind === "trace") {
					return <TraceLevelRead {...shared} record={record} onOpen={onOpenOverTrace} />;
				}
				switch (record.kind) {
					case "review": {
						return <ReviewLevelRead {...shared} reviewId={record.id} />;
					}
					case "result": {
						return <ResultLevelRead {...shared} resultId={record.id} />;
					}
					case "problem": {
						return <ProblemLevelRead {...shared} problemId={record.id} />;
					}
					case "feedback": {
						return (
							<FeedbackLevelRead
								{...shared}
								feedbackId={record.id}
								inQueue={approvalQueue && level.depth === 0}
								onMoveOn={(next) => moveOn(record.id, next)}
							/>
						);
					}
					case "item": {
						return <ItemLevelRead {...shared} itemId={record.id} />;
					}
					case "practice": {
						return <PracticeLevelRead {...shared} practiceSlug={record.id} />;
					}
				}
			}}
		</DetailDrawerStack>
	);
}

/**
 * A decision moves on only from where it was taken: the decided feedback alone in the address, as
 * it is now rather than as this render was given it. By the time the decision lands the admin may
 * have stepped to another proposal, opened a record over this one or dismissed it, and moving on
 * then would override the step, drop the record or reopen the level.
 */
function stillInFront(search: Record<string, unknown>, feedbackId: string) {
	const current = stackInSearch(search.detail);
	return current.length === 1 && current[0] === detailStackKey(feedbackLevel(feedbackId));
}

interface LevelReadProps {
	workspaceSlug: string;
	nested: boolean;
	path: LevelPath;
}

/** A refusal said on the sheet that asked, with its way out: every reader here is an admin. */
function RequestRefusal({
	workspaceSlug,
	refusal,
}: {
	workspaceSlug: string;
	refusal: ReviewRequestOutcome;
}) {
	return (
		<TraceRefusalAlert
			refusal={refusal}
			fix={
				refusal.reason !== null && (
					<RefusalFixLink workspaceSlug={workspaceSlug} reason={refusal.reason} />
				)
			}
		/>
	);
}

/**
 * An event's name in the words the practice editor offers it, from the definition options: one
 * source for the trace, the review sheet and the item sheet.
 */
function useEventLabel(workspaceSlug: string): (name: string) => string {
	const options = useQuery(getPracticeDefinitionOptionsOptions({ path: { workspaceSlug } })).data;
	const events = options?.itemTypes.flatMap((type) => type.events) ?? [];
	return (name) => eventLabel(name, events);
}

/** The name a practice goes by now: its current version's. */
function practiceName(practice: Practice): string | undefined {
	return practice.currentVersion.definition?.name ?? practice.currentVersion.name ?? undefined;
}

// ------------------------------------------------------------------------------------------------
// The trace
// ------------------------------------------------------------------------------------------------

/** The record a trace marks "You started here": the one it was opened for. A practice's is its newest result. */
function hereOf(record: RecordRef, newestResult: string | undefined): TraceNode | undefined {
	switch (record.kind) {
		case "review": {
			return { kind: "review" };
		}
		case "item": {
			return { kind: "work" };
		}
		case "result":
		case "feedback": {
			return { kind: record.kind, id: record.id };
		}
		// A problem's step is its result's: the trace draws results, and the problem's sheet opens over it.
		case "problem": {
			return undefined;
		}
		case "practice": {
			return newestResult === undefined ? undefined : { kind: "result", id: newestResult };
		}
	}
}

function lineageTarget(
	record: RecordRef,
	newestResult: string | undefined,
): ReviewLineageTarget | undefined {
	switch (record.kind) {
		case "review": {
			return { kind: "REVIEW", reviewId: record.id };
		}
		case "result": {
			return { kind: "RESULT", resultId: record.id };
		}
		case "problem": {
			return { kind: "PROBLEM", problemId: record.id };
		}
		case "feedback": {
			return { kind: "FEEDBACK", feedbackId: record.id };
		}
		case "item": {
			return { kind: "ITEM", itemId: record.id };
		}
		case "practice": {
			return newestResult === undefined ? undefined : { kind: "RESULT", resultId: newestResult };
		}
	}
}

/** What the trace's head calls what it stands on. */
function traceTitle(
	record: RecordRef,
	lineage: ReviewLineage | undefined,
	practice: string | undefined,
): string {
	const item = lineage?.nodes.find((node) => node.kind === "ITEM")?.item ?? undefined;
	const work = item?.label ?? "the work";
	switch (record.kind) {
		case "review": {
			return `Review of ${work}`;
		}
		case "result": {
			const result = lineage?.nodes.find((node) => node.result?.id === record.id)?.result;
			return result === undefined || result === null
				? PRACTICE_REVIEW_LEVEL_LABELS.result
				: (result.summary ?? result.practiceName);
		}
		case "problem": {
			const problem = lineage?.nodes.find((node) => node.problem?.id === record.id)?.problem;
			return problem?.summary ?? PRACTICE_REVIEW_LEVEL_LABELS.problem;
		}
		case "feedback": {
			return `Feedback on ${work}`;
		}
		case "item": {
			return item?.title ?? item?.label ?? PRACTICE_REVIEW_LEVEL_LABELS.item;
		}
		case "practice": {
			return practice ?? PRACTICE_REVIEW_LEVEL_LABELS.practice;
		}
	}
}

/** The latest event recorded on the item, the one a trace of an item no review started shows. */
function latestEvent(trace: ItemTrace): ItemEvent | undefined {
	let latest: ItemEvent | undefined;
	for (const event of trace.events) {
		if (latest === undefined || event.occurredAt > latest.occurredAt) {
			latest = event;
		}
	}
	return latest;
}

/**
 * An item as recorded, read before its chain: a chain is asked for only where one of its reviews
 * exists, so the trace of an item no review started never asks for a chain it does not have. Any
 * other record asks for its chain at once.
 */
function useRecordedItem(workspaceSlug: string, record: RecordRef) {
	const itemId = record.kind === "item" ? record.id : undefined;
	const traceOptions = getItemTraceOptions({ path: { workspaceSlug, itemId: itemId ?? "" } });
	const traced = useQuery({
		...traceOptions,
		queryFn: itemId === undefined ? skipToken : traceOptions.queryFn,
	});
	const withoutReview = traced.isSuccess && traced.data.reviews.length === 0;
	const askChain = itemId === undefined || !(traced.isPending || withoutReview);
	return { itemId, traced, askChain };
}

/**
 * One record's trace. A practice's trace is the story of its newest result, narrowed to the
 * practice; an item no review started has no chain, and shows its latest event. The filter is the
 * reader's while the trace is open; what it shows on top of its steps is the reader's in this
 * browser, for every trace. It marks the record it was opened for, and the mark stays there while
 * other records open over it.
 */
function TraceLevelRead({
	workspaceSlug,
	nested,
	path,
	record,
	onOpen,
}: LevelReadProps & {
	record: RecordRef;
	onOpen: (record: RecordRef) => void;
}) {
	const practiceFilter: TraceFilter =
		record.kind === "practice" ? { kind: "practice", practiceSlug: record.id } : ALL_STEPS;
	const [display, setDisplay] = useTraceDisplay();
	const [filter, setFilter] = useState<TraceFilter>(practiceFilter);
	const labelEvent = useEventLabel(workspaceSlug);

	const newestOptions = listReviewResultsOptions({
		path: { workspaceSlug },
		query: { practiceSlug: record.id, size: 1 },
	});
	const newestQuery = useQuery({
		...newestOptions,
		queryFn: record.kind === "practice" ? newestOptions.queryFn : skipToken,
	});
	const newest = newestQuery.data?.content[0];
	const practicesOptions = listPracticesOptions({ path: { workspaceSlug } });
	const practices = useQuery({
		...practicesOptions,
		queryFn: record.kind === "practice" ? practicesOptions.queryFn : skipToken,
	}).data;

	const { itemId, traced, askChain } = useRecordedItem(workspaceSlug, record);
	const target = askChain ? lineageTarget(record, newest?.id) : undefined;
	const lineage = useReviewLineage(workspaceSlug, target);
	const practicesEnabled = useActiveWorkspaceSlug().workspaces.find(
		(workspace) => workspace.workspaceSlug === workspaceSlug,
	)?.practicesEnabled;

	let chain: TraceChainState = { status: "none" };
	if (lineage !== undefined) {
		chain = lineage;
	} else if (record.kind === "practice" && newestQuery.isPending) {
		chain = { status: "loading" };
	} else if (itemId !== undefined) {
		if (traced.isPending) {
			chain = { status: "loading" };
		} else if (traced.isSuccess) {
			const event = latestEvent(traced.data);
			if (event !== undefined) {
				chain = {
					status: "unreviewed",
					item: traced.data.item,
					event,
					eventLabel: labelEvent(event.name),
				};
			}
		}
	}

	const ready = chain.status === "ready" ? chain.lineage : undefined;
	// The review the trace tells, read as its sheet reads it: one source for its times, model,
	// status and first read, and the cache the sheet over the trace shares.
	const reviewId = ready?.nodes.find((node) => node.kind === "REVIEW")?.review?.id;
	const reviewOptions = getReviewOptions({ path: { workspaceSlug, reviewId: reviewId ?? "" } });
	const reviewQuery = useQuery({
		...reviewOptions,
		queryFn: reviewId === undefined ? skipToken : reviewOptions.queryFn,
		refetchInterval: ({ state }) =>
			state.status === "success" && state.data !== undefined && isActiveReview(state.data.status)
				? ACTIVE_REVIEW_POLL_MS
				: false,
	});
	const current = reviewQuery.data;
	const review = reviewQuery.isError ? undefined : current;
	const queryClient = useQueryClient();
	const lineageOptions = getReviewLineageOptions({
		path: { workspaceSlug },
		query: target ?? { kind: "REVIEW" },
	});
	const key = hashKey(lineageOptions.queryKey);
	// Feedback can settle after the review stops running. Read its final chain once more, only for
	// the same selected workspace, target and review.
	const settled = current !== undefined && !isActiveReview(current.status);
	const previous = useRef<{ key: string; reviewId: string; settled: boolean } | undefined>(
		undefined,
	);
	useEffect(() => {
		const before = previous.current;
		previous.current =
			reviewId === undefined || current === undefined ? undefined : { key, reviewId, settled };
		if (before?.key === key && before.reviewId === reviewId && !before.settled && settled) {
			void queryClient.invalidateQueries({ queryKey: lineageOptions.queryKey, exact: true });
		}
	});
	if (chain.status === "ready" && reviewQuery.isError) {
		chain = {
			status: "error",
			error: reviewQuery.error,
			onRetry: () => {
				void reviewQuery.refetch();
			},
		};
	}
	const practice = practices?.find((candidate) => candidate.slug === record.id);
	return (
		<TraceLevel
			nested={nested}
			path={path}
			title={traceTitle(
				record,
				ready,
				(practice && practiceName(practice)) ?? newest?.practiceName,
			)}
			description={
				record.kind === "practice"
					? "The trace of its newest result, narrowed to the practice."
					: undefined
			}
			chain={chain}
			here={hereOf(record, newest?.id)}
			onOpen={onOpen}
			display={display}
			onDisplayChange={setDisplay}
			filter={filter}
			onFilterChange={setFilter}
			workRecord={record.kind === "item" ? record : undefined}
			practicesEnabled={practicesEnabled}
			review={review}
			origin={review?.origin}
		/>
	);
}

// ------------------------------------------------------------------------------------------------
// The record sheets
// ------------------------------------------------------------------------------------------------

function ReviewLevelRead({
	workspaceSlug,
	reviewId,
	...props
}: LevelReadProps & { reviewId: string }) {
	const controller = useReviewController(workspaceSlug, reviewId);
	const labelEvent = useEventLabel(workspaceSlug);
	// The sheet shows the work, so a refusal is said on it.
	const request = useRequestPracticeReview(workspaceSlug, { showsInline: () => true });
	const item = controller.review.status === "ready" ? controller.review.review.item : undefined;
	const requestable = item !== undefined && REVIEWABLE_ON_DEMAND.has(item.kind);
	// The trace is this review's answers, so another review of the item is asked for by the list.
	const activeOptions = listReviewsOptions({
		path: { workspaceSlug },
		query: { itemId: item?.id, status: ["QUEUED", "RUNNING"], size: 1 },
	});
	const activeQuery = useQuery({
		...activeOptions,
		queryFn: requestable ? activeOptions.queryFn : skipToken,
	});
	const otherActive = (activeQuery.data?.content.length ?? 0) > 0;
	return (
		<ReviewLevel
			{...props}
			{...controller}
			workspaceSlug={workspaceSlug}
			eventLabel={labelEvent}
			onRequestReview={requestable ? () => request.ask(item.id) : undefined}
			requestingReview={request.asking !== undefined}
			// Another ask while a review of this item waits or runs would only be coalesced into it.
			requestBlocked={otherActive ? REQUEST_BLOCKED.active : undefined}
			requestRefusal={
				request.refusal && (
					<RequestRefusal workspaceSlug={workspaceSlug} refusal={request.refusal.outcome} />
				)
			}
		/>
	);
}

function ResultLevelRead({
	resultId,
	workspaceSlug,
	...props
}: LevelReadProps & { resultId: string }) {
	const controller = useResultController(workspaceSlug, resultId);
	return <ResultLevel {...props} {...controller} />;
}

/** One problem: its own detail, which names its parent result for the marks on it. */
function ProblemLevelRead({
	problemId,
	workspaceSlug,
	...props
}: LevelReadProps & { problemId: string }) {
	const query = useQuery(getReviewProblemOptions({ path: { workspaceSlug, problemId } }));
	return (
		<ProblemLevel
			{...props}
			problem={panelState(query, (problem) => ({ status: "ready" as const, problem }))}
		/>
	);
}

/**
 * Feedback opened as the approval queue reads the queue too, so a decision moves on to the next
 * proposal in it — or, once nothing else waits, closes the level.
 */
function FeedbackLevelRead({
	feedbackId,
	workspaceSlug,
	inQueue,
	onMoveOn,
	...props
}: LevelReadProps & {
	feedbackId: string;
	inQueue: boolean;
	/** Swaps this level for the `next` proposal in the queue, or closes it on none. */
	onMoveOn: (next: string | undefined) => void;
}) {
	const nextAfterDecision = useNextInApprovalQueue(workspaceSlug, feedbackId);
	const moveOn = async () => {
		if (!inQueue) {
			return;
		}
		let next = queue?.next;
		if (next === undefined) {
			// Nothing shown after it — the last of the page read, one reached by skipping others, or a
			// queue not read yet — so whatever still waits is read, and the level closes on none, or
			// when that read fails.
			try {
				next = await nextAfterDecision();
			} catch {
				next = undefined;
			}
		}
		onMoveOn(next);
	};
	const controller = useFeedbackController(workspaceSlug, feedbackId, {
		onDecided: () => {
			void moveOn();
		},
	});
	const awaitingApproval =
		controller.feedback.status === "ready" &&
		controller.feedback.feedback.state === "AWAITING_APPROVAL";
	const queue = useApprovalQueue(workspaceSlug, feedbackId, inQueue && awaitingApproval);
	return <FeedbackLevel {...props} {...controller} queue={queue} />;
}

/**
 * One item: what was recorded about it, every event on it, and the facts its provider reports. The
 * trace is read again while one of its reviews is queued or running, since a settled review moves
 * its events.
 */
function ItemLevelRead({ workspaceSlug, itemId, ...props }: LevelReadProps & { itemId: string }) {
	const labelEvent = useEventLabel(workspaceSlug);
	const traceQuery = useQuery({
		...getItemTraceOptions({ path: { workspaceSlug, itemId } }),
		retry: false,
		refetchInterval: (result) => (reviewing(result.state.data) ? ACTIVE_REVIEW_POLL_MS : false),
	});
	const factsQuery = useQuery({
		...getItemFactsOptions({ path: { workspaceSlug, itemId } }),
		// An item outside the monitored repositories has no facts: asking again answers the same.
		retry: false,
	});
	const request = useRequestPracticeReview(workspaceSlug, { showsInline: () => true });
	const item = traceQuery.data?.item;
	return (
		<WorkLevel
			{...props}
			workspaceSlug={workspaceSlug}
			item={panelState(traceQuery, (trace) => ({ status: "ready" as const, item: trace.item }))}
			events={panelState(traceQuery, (trace) => ({
				status: "ready" as const,
				events: trace.events,
			}))}
			eventLabel={labelEvent}
			facts={panelState(factsQuery, ({ facts }) => ({ status: "ready" as const, facts }))}
			onRequestReview={
				item !== undefined && REVIEWABLE_ON_DEMAND.has(item.kind)
					? () => request.ask(item.id)
					: undefined
			}
			requesting={request.asking !== undefined}
			// Another ask while a review of this item runs would only be coalesced into it.
			requestBlocked={reviewing(traceQuery.data) ? REQUEST_BLOCKED.active : undefined}
			refusal={
				request.refusal && (
					<RequestRefusal workspaceSlug={workspaceSlug} refusal={request.refusal.outcome} />
				)
			}
		/>
	);
}

function PracticeLevelRead({
	workspaceSlug,
	practiceSlug,
	...props
}: LevelReadProps & { practiceSlug: string }) {
	const practicesQuery = useQuery(listPracticesOptions({ path: { workspaceSlug } }));
	const optionsQuery = useQuery(getPracticeDefinitionOptionsOptions({ path: { workspaceSlug } }));
	const groupsQuery = useQuery(listPracticeGroupsOptions({ path: { workspaceSlug } }));
	const versionsQuery = useQuery(
		listPracticeVersionsOptions({ path: { workspaceSlug, practiceSlug } }),
	);
	const practice = practicesQuery.data?.find((candidate) => candidate.slug === practiceSlug);
	let state: PracticeLevelProps["practice"];
	if (practicesQuery.isError || optionsQuery.isError) {
		state = {
			status: "error",
			error: practicesQuery.error ?? optionsQuery.error,
			onRetry: () => {
				void practicesQuery.refetch();
				void optionsQuery.refetch();
			},
		};
	} else if (practicesQuery.data === undefined || optionsQuery.data === undefined) {
		state = { status: "loading" };
	} else {
		state = {
			status: "ready",
			practice,
			definitionOptions: optionsQuery.data,
			groupName: groupsQuery.data?.find((group) => group.slug === practice?.groupSlug)?.name,
		};
	}
	return (
		<PracticeLevel
			{...props}
			workspaceSlug={workspaceSlug}
			practice={state}
			versions={panelState(versionsQuery, (versions) => ({
				status: "ready" as const,
				versions,
			}))}
		/>
	);
}

/** A review of the item is queued or running. */
function reviewing(trace: ItemTrace | undefined): boolean {
	return trace?.reviews.some((review) => isActiveReview(review.status)) === true;
}
