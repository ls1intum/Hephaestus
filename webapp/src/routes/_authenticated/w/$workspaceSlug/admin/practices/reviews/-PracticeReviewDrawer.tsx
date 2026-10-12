import {
	queryOptions,
	skipToken,
	useQueries,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { useRouter } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";

import {
	getAgentJobOptions,
	getAgentJobQueryKey,
	getArtifactTraceOptions,
	getPracticeDefinitionOptionsOptions,
	getPracticeReviewWorkFactsOptions,
	listGroupsOptions,
	listPracticeReviewObservationsOptions,
	listPracticeReviewsOptions,
	listPracticeRevisionsOptions,
	listPracticesOptions,
	listTracedArtifactsOptions,
} from "@/api/@tanstack/react-query.gen";
import { getPracticeReviewWorkFacts } from "@/api/sdk.gen";
import type {
	AgentJob,
	ArtifactTrace,
	GetPracticeReviewWorkFactsResponse,
	ReviewLineage,
	ReviewWorkFacts,
	TracedSignal,
} from "@/api/types.gen";
import { FeedbackLevel } from "@/components/admin/practice-reviews/FeedbackLevel";
import { REVIEWABLE_ON_DEMAND } from "@/components/admin/practice-reviews/job-utils";
import { ObservationLevel } from "@/components/admin/practice-reviews/ObservationLevel";
import {
	PracticeLevel,
	type PracticeLevelProps,
} from "@/components/admin/practice-reviews/PracticeLevel";
import { REQUEST_BLOCKED } from "@/components/admin/practice-reviews/RequestReviewAction";
import {
	levelRecord,
	type RecordRef,
	workOfRecord,
} from "@/components/admin/practice-reviews/review-layers";
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
import { TraceRefusalAlert } from "@/components/practice-trace/TraceRefusalAlert";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import {
	useApprovalQueue,
	useFeedbackController,
	useNextInApprovalQueue,
} from "@/hooks/use-feedback-controller";
import { useObservationController } from "@/hooks/use-observation-controller";
import { useRequestPracticeReview } from "@/hooks/use-request-practice-review";
import { type ReviewLineageTarget, useReviewLineage } from "@/hooks/use-review-lineage";
import { useReviewRunController } from "@/hooks/use-review-run-controller";
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

/** The kinds of work whose provider facts the server keeps: pull or merge requests and issues. */
const PROVIDER_FACT_KINDS = new Set(["scm.pull_request", "scm.issue"]);

/** The facts a 200 carries; a 204, work in no monitored repository, reads as an empty body. */
function providerFacts(
	data: GetPracticeReviewWorkFactsResponse | undefined,
): ReviewWorkFacts | undefined {
	const body: unknown = data;
	return carriesFacts(body) ? body : undefined;
}

/** The words HTTP gives the statuses this endpoint answers, for a response over HTTP/2 that has none. */
const STATUS_TEXT: Readonly<Record<number, string>> = { 200: "OK", 204: "No Content" };

/** "204 No Content from GET /workspaces/acme/practices/reviews/work/scm.pull_request/42". */
function serverAnswer(response: Response): string {
	const text = response.statusText === "" ? STATUS_TEXT[response.status] : response.statusText;
	const status = text === undefined ? String(response.status) : `${response.status} ${text}`;
	const path = URL.canParse(response.url) ? new URL(response.url).pathname : undefined;
	return path === undefined ? status : `${status} from GET ${path}`;
}

/**
 * The client hands an empty body over as `{}` or `null`, whatever the generated type says; neither
 * carries a `state`.
 */
function carriesFacts(body: unknown): body is ReviewWorkFacts {
	return typeof body === "object" && body !== null && Object.hasOwn(body, "state");
}

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
		<DetailDrawerStack
			stack={stack}

			onClose={onClose}
		>
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
					case "observation": {
						return <ObservationLevelRead {...shared} observationId={record.id} />;
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
					case "work": {
						return <WorkLevelRead {...shared} record={record} />;
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

// ------------------------------------------------------------------------------------------------
// The trace
// ------------------------------------------------------------------------------------------------

/** The record a trace marks "You started here": the one it was opened for. A practice's is its newest observation. */
function hereOf(record: RecordRef, newestObservation: string | undefined): TraceNode | undefined {
	switch (record.kind) {
		case "review": {
			return { kind: "review" };
		}
		case "work": {
			return { kind: "work" };
		}
		case "observation":
		case "feedback": {
			return { kind: record.kind, id: record.id };
		}
		case "practice": {
			return newestObservation === undefined
				? undefined
				: { kind: "observation", id: newestObservation };
		}
	}
}

function lineageTarget(
	record: RecordRef,
	newestObservation: string | undefined,
): ReviewLineageTarget | undefined {
	switch (record.kind) {
		case "review": {
			return { kind: "REVIEW", id: record.id, whole: true };
		}
		case "observation": {
			return { kind: "OBSERVATION", id: record.id, whole: true };
		}
		case "feedback": {
			return { kind: "FEEDBACK", id: record.id, whole: true };
		}
		case "work": {
			const work = workOfRecord(record);
			return work === undefined ? undefined : { kind: "WORK", ...work, whole: true };
		}
		case "practice": {
			return newestObservation === undefined
				? undefined
				: { kind: "OBSERVATION", id: newestObservation, whole: true };
		}
	}
}

/** What the trace's head calls what it stands on. */
function traceTitle(
	record: RecordRef,
	lineage: ReviewLineage | undefined,
	practiceName: string | undefined,
): string {
	const work = lineage?.work?.label ?? "the work";
	switch (record.kind) {
		case "review": {
			return `Review of ${work}`;
		}
		case "observation": {
			return (
				lineage?.observations.find((observation) => observation.id === record.id)?.summary ??
				PRACTICE_REVIEW_LEVEL_LABELS.observation
			);
		}
		case "feedback": {
			return `Feedback on ${work}`;
		}
		case "work": {
			return lineage?.work?.title ?? lineage?.work?.label ?? PRACTICE_REVIEW_LEVEL_LABELS.work;
		}
		case "practice": {
			return practiceName ?? PRACTICE_REVIEW_LEVEL_LABELS.practice;
		}
	}
}

/**
 * A piece of work as recorded work, read before its chain: a chain is asked for only where one of
 * its moments started a review, so the trace of work no review started on never asks for a chain it
 * does not have. Any other record asks for its chain at once.
 */
function useRecordedWork(workspaceSlug: string, record: RecordRef) {
	const work = workOfRecord(record);
	const tracedOptions = listTracedArtifactsOptions({
		path: { workspaceSlug },
		query: { artifactKind: work?.artifactKind, artifactId: work?.artifactId, size: 1 },
	});
	const traced = useQuery({
		...tracedOptions,
		queryFn: work === undefined ? skipToken : tracedOptions.queryFn,
	});
	const recorded = traced.data?.content?.[0];
	const withoutReview = traced.isSuccess && (recorded?.reviewedSignalCount ?? 0) === 0;
	const askChain = work === undefined || !(traced.isPending || withoutReview);
	return { work, traced, recorded, askChain };
}

/**
 * One record's trace. A practice's trace is the story of its newest observation, narrowed to the
 * practice; work none of whose moments started a review has no chain, and shows its latest moment.
 * The filter is the reader's while the trace is open; what it shows on top of its steps is the
 * reader's in this browser, for every trace. It marks the record it was opened for, and the mark
 * stays there while other records open over it.
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

	const newestOptions = listPracticeReviewObservationsOptions({
		path: { workspaceSlug },
		query: { practiceSlug: [record.id], size: 1 },
	});
	const newestQuery = useQuery({
		...newestOptions,
		queryFn: record.kind === "practice" ? newestOptions.queryFn : skipToken,
	});
	const newest = newestQuery.data?.content?.[0];
	const practicesOptions = listPracticesOptions({ path: { workspaceSlug } });
	const practices = useQuery({
		...practicesOptions,
		queryFn: record.kind === "practice" ? practicesOptions.queryFn : skipToken,
	}).data;

	const { work, traced, recorded, askChain } = useRecordedWork(workspaceSlug, record);
	const lineage = useReviewLineage(
		workspaceSlug,
		askChain ? lineageTarget(record, newest?.id) : undefined,
	);
	const practicesEnabled = useActiveWorkspaceSlug().workspaces.find(
		(workspace) => workspace.workspaceSlug === workspaceSlug,
	)?.practicesEnabled;

	let chain: TraceChainState = { status: "none" };
	if (lineage !== undefined) {
		chain = lineage;
	} else if (record.kind === "practice" && newestQuery.isPending) {
		chain = { status: "loading" };
	} else if (work !== undefined) {
		if (traced.isPending) {
			chain = { status: "loading" };
		} else if (recorded !== undefined) {
			chain = { status: "unreviewed", work: recorded };
		}
	}

	const ready = chain.status === "ready" ? chain.lineage : undefined;
	// The review the trace tells, read as its sheet reads it: one source for its times, model,
	// outcome and first read, and the cache the sheet over the trace shares.
	const reviewId = ready?.review?.id;
	const jobOptions = getAgentJobOptions({ path: { workspaceSlug, jobId: reviewId ?? "" } });
	const job = useQuery({
		...jobOptions,
		queryFn: reviewId === undefined ? skipToken : jobOptions.queryFn,
	}).data;
	// The review's row, as its sheet reads it, for how the review came to run.
	const rowOptions = listPracticeReviewsOptions({
		path: { workspaceSlug },
		query: { agentJobId: reviewId, size: 1 },
	});
	const row = useQuery({
		...rowOptions,
		queryFn: reviewId === undefined ? skipToken : rowOptions.queryFn,
	}).data?.content?.[0];
	const practiceName =
		practices?.find((practice) => practice.slug === record.id)?.name ?? newest?.practiceName;
	return (
		<TraceLevel
			nested={nested}
			path={path}
			title={traceTitle(record, ready, practiceName)}
			description={
				record.kind === "practice"
					? "The trace of its newest observation, narrowed to the practice."
					: undefined
			}
			chain={chain}
			here={hereOf(record, newest?.id)}
			onOpen={onOpen}
			display={display}
			onDisplayChange={setDisplay}
			filter={filter}
			onFilterChange={setFilter}
			workRecord={record.kind === "work" ? record : undefined}
			practicesEnabled={practicesEnabled}
			job={job}
			origin={row?.origin}
			position={reviewPosition(row)}
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
	const run = useReviewRunController(workspaceSlug, reviewId);
	const groups = useQuery(listGroupsOptions({ path: { workspaceSlug } })).data;
	const summary = useQuery(
		listPracticeReviewsOptions({
			path: { workspaceSlug },
			query: { agentJobId: reviewId, size: 1 },
		}),
	).data?.content?.[0];
	// The sheet shows the work, so a refusal is said on it.
	const request = useRequestPracticeReview(workspaceSlug, { showsInline: () => true });
	const job = run.job.status === "ready" ? run.job.job : undefined;
	const work = job?.target.reviewedWork;
	const artifactId = Number(work?.id);
	const requestable =
		work !== undefined && REVIEWABLE_ON_DEMAND.has(work.kind) && Number.isSafeInteger(artifactId);
	// The trace is this review's answers, so another review of the work is asked for by the list.
	const activeOptions = listPracticeReviewsOptions({
		path: { workspaceSlug },
		query: { artifactKind: work?.kind, artifactId, status: ["QUEUED", "RUNNING"], size: 1 },
	});
	const activeQuery = useQuery({
		...activeOptions,
		queryFn: requestable ? activeOptions.queryFn : skipToken,
	});
	const otherActive = (activeQuery.data?.content?.length ?? 0) > 0;
	return (
		<ReviewLevel
			{...props}
			job={run.job}
			summary={summary}
			trace={run.trace}
			groups={groups}
			deliveryChecks={run.deliveryChecks}
			usage={run.usage}
			onCancel={run.onCancel}
			cancelPending={run.cancelPending}
			onRetryResultProcessing={run.onRetryResultProcessing}
			retryResultProcessingPending={run.retryResultProcessingPending}
			onRequestReview={
				requestable ? () => request.ask({ artifactKind: work.kind, artifactId }) : undefined
			}
			requestingReview={request.asking !== undefined}
			// Another ask while a review of this work waits or runs would only be coalesced into it.
			requestBlocked={otherActive ? REQUEST_BLOCKED.active : undefined}
			requestRefusal={
				request.refusal && (
					<TraceRefusalAlert
						refusal={request.refusal.outcome}
						workspaceSlug={workspaceSlug}
						canAdminister
					/>
				)
			}
		/>
	);
}

function ObservationLevelRead({
	observationId,
	workspaceSlug,
	...props
}: LevelReadProps & { observationId: string }) {
	const controller = useObservationController(workspaceSlug, observationId);
	return <ObservationLevel {...props} {...controller} />;
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
		controller.feedback.feedback.deliveryState === "AWAITING_APPROVAL";
	const queue = useApprovalQueue(workspaceSlug, feedbackId, inQueue && awaitingApproval);
	return <FeedbackLevel {...props} {...controller} queue={queue} />;
}

function WorkLevelRead({
	workspaceSlug,
	record,
	...props
}: LevelReadProps & { record: RecordRef }) {
	const work = workOfRecord(record);
	const options = listTracedArtifactsOptions({
		path: { workspaceSlug },
		query: { artifactKind: work?.artifactKind, artifactId: work?.artifactId, size: 1 },
	});
	const query = useQuery({ ...options, queryFn: work === undefined ? skipToken : options.queryFn });
	// Every moment on the work, which only its trace lists, and what the provider reported about it;
	// neither is on the work list's row.
	const traceOptions = getArtifactTraceOptions({
		path: {
			workspaceSlug,
			artifactKind: work?.artifactKind ?? "",
			artifactId: work?.artifactId ?? 0,
		},
	});
	const traceQuery = useQuery({
		...traceOptions,
		queryFn: work === undefined ? skipToken : traceOptions.queryFn,
		retry: false,
		// Current practices or latest occasions waiting for admission keep the trace discoverable.
		refetchInterval: (result) => (reviewing(result.state.data) ? ACTIVE_REVIEW_POLL_MS : false),
	});
	// Practice answers can settle before composition and result processing finish. Follow the runs
	// that own current answers and the latest admitted occasions, even when older answers mask them.
	const reviewIds = [
		...new Set(
			[
				...(traceQuery.data?.practices ?? []),
				...latestSignals(
					traceQuery.data?.signals.filter((entry) => entry.reviewId !== undefined) ?? [],
				),
			].flatMap((entry) => (entry.reviewId === undefined ? [] : [entry.reviewId])),
		),
	].sort();
	const processing = useQueries({
		queries: reviewIds.map((jobId) =>
			queryOptions({
				...getAgentJobOptions({ path: { workspaceSlug, jobId } }),
				refetchOnMount: "always",
				refetchInterval: (result) =>
					processingReview(result.state.data) ? ACTIVE_REVIEW_POLL_MS : false,
			}),
		),
		combine: (results) => results.some((result) => processingReview(result.data)),
	});
	// The work's counts change when its reviews settle, so the row is read again once they have.
	const queryClient = useQueryClient();
	const running = reviewing(traceQuery.data) || processing;
	const wasRunning = useRef(running);
	useEffect(() => {
		const before = wasRunning.current;
		wasRunning.current = running;
		if (before && !running) {
			void queryClient.invalidateQueries({ queryKey: options.queryKey });
		}
	});
	// A retry can reuse a settled run's ID, so a changed answer reads its run again. Compared as text,
	// not as the trace's Dates, so a refreshed but unchanged trace reads nothing.
	const assessmentVersion = traceQuery.data?.practices
		.map(
			(entry) =>
				`${entry.practiceSlug}:${entry.reviewId ?? ""}:${entry.outcome}:${entry.decidedAt?.getTime() ?? ""}`,
		)
		.sort()
		.join("|");
	const assessed = useRef(assessmentVersion);
	useEffect(() => {
		const before = assessed.current;
		assessed.current = assessmentVersion;
		if (before === undefined || before === assessmentVersion) {
			return;
		}
		for (const jobId of reviewIds) {
			void queryClient.invalidateQueries({
				queryKey: getAgentJobQueryKey({ path: { workspaceSlug, jobId } }),
			});
		}
	});
	const factsOptions = getPracticeReviewWorkFactsOptions({
		path: {
			workspaceSlug,
			artifactKind: work?.artifactKind ?? "",
			artifactId: work?.artifactId ?? 0,
		},
	});
	// What the server answered is cached beside the body, so a sheet that has no facts says which
	// answer left it without them, also when it opens again from the cache. Only this sheet reads
	// the key, so the cached data is in this shape everywhere.
	const factsQuery = useQuery({
		queryKey: factsOptions.queryKey,
		queryFn:
			work === undefined || !PROVIDER_FACT_KINDS.has(work.artifactKind)
				? skipToken
				: async ({ signal }) => {
						const { data, response } = await getPracticeReviewWorkFacts({
							...factsOptions.queryKey[0],
							signal,
							throwOnError: true,
						});
						return { facts: providerFacts(data), answer: serverAnswer(response) };
					},
		// Work outside the monitored repositories answers 204 with nothing: asking again answers the same.
		retry: false,
	});
	const request = useRequestPracticeReview(workspaceSlug, { showsInline: () => true });
	return (
		<WorkLevel
			{...props}
			workspaceSlug={workspaceSlug}
			work={
				work === undefined
					? { status: "ready", work: undefined }
					: panelState(query, (page) => ({ status: "ready" as const, work: page.content?.[0] }))
			}
			moments={panelState(traceQuery, (trace) => ({
				status: "ready" as const,
				moments: trace.signals,
			}))}
			facts={
				work === undefined || !PROVIDER_FACT_KINDS.has(work.artifactKind)
					? { status: "ready", facts: undefined }
					: panelState(factsQuery, ({ facts, answer }) => ({
							status: "ready" as const,
							facts,
							unmonitored: facts === undefined,
							answer,
						}))
			}
			onRequestReview={
				work !== undefined && REVIEWABLE_ON_DEMAND.has(work.artifactKind)
					? () => request.ask(work)
					: undefined
			}
			requesting={request.asking !== undefined}
			// Another ask while a review of this work runs would only be coalesced into it.
			requestBlocked={reviewRunning(traceQuery.data) ? REQUEST_BLOCKED.active : undefined}
			refusal={
				request.refusal && (
					<TraceRefusalAlert
						refusal={request.refusal.outcome}
						workspaceSlug={workspaceSlug}
						canAdminister
					/>
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
	const groupsQuery = useQuery(listGroupsOptions({ path: { workspaceSlug } }));
	const revisionsQuery = useQuery(
		listPracticeRevisionsOptions({ path: { workspaceSlug, practiceSlug } }),
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
			revisions={panelState(revisionsQuery, (revisions) => ({
				status: "ready" as const,
				revisions,
			}))}
		/>
	);
}

/** A review of the work is queued or running. */
function reviewRunning(trace: ArtifactTrace | undefined): boolean {
	return (
		trace?.practices.some((entry) => entry.outcome === "PENDING" || entry.outcome === "RUNNING") ===
		true
	);
}

/** Follow current assessments and latest occasions still waiting for a review to be admitted. */
function reviewing(trace: ArtifactTrace | undefined): boolean {
	return (
		reviewRunning(trace) ||
		latestSignals(trace?.signals ?? []).some(
			(entry) =>
				entry.state === "RECORDED" || entry.state === "PENDING" || entry.state === "DEFERRED",
		)
	);
}

/** A run still works, or its results still wait to be processed. */
function processingReview(job: AgentJob | undefined): boolean {
	return job?.status === "QUEUED" || job?.status === "RUNNING" || job?.deliveryStatus === "PENDING";
}

/** Keep every tie: provider timestamps can have less precision than separate review occasions. */
function latestSignals(signals: TracedSignal[]): TracedSignal[] {
	const latest = new Map<string, TracedSignal[]>();
	for (const signal of signals) {
		const existing = latest.get(signal.signal);
		const at = signal.occurredAt.getTime();
		const previous = existing?.[0]?.occurredAt.getTime();
		if (previous === undefined || at > previous) {
			latest.set(signal.signal, [signal]);
		} else if (at === previous) {
			existing?.push(signal);
		}
	}
	return [...latest.values()].flat();
}

/** Where a review stands among the reviews of its work, where its row says both. */
function reviewPosition(
	row: { positionOnWork?: number; reviewsOnWork?: number } | undefined,
): { index: number; total: number } | undefined {
	if (row?.positionOnWork === undefined || row.reviewsOnWork === undefined) {
		return undefined;
	}
	return { index: row.positionOnWork, total: row.reviewsOnWork };
}
