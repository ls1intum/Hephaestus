import { RadarIcon, WorkflowIcon } from "lucide-react";
import type { ReactNode } from "react";

import type { ReviewRunSummary, TracedArtifact } from "@/api/types.gen";
import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import {
	ReviewRunDayTable,
	ReviewRunDayTableSkeleton,
} from "@/components/practice-trace/ReviewRunDayTable";
import { NO_REVIEW_STARTED } from "@/components/practice-vocabulary/review-status-defs";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { reviewedWorkName } from "@/lib/artifact-kinds";
import type { PagedListState } from "@/runtime/tanstack-query/infinite-list";

import { REQUEST_BLOCKED, type RowReviewRequest } from "./RequestReviewAction";
import type { PracticeFacetOption } from "./ResultFilters";
import { clearedScope, hasAppliedScope, readScope } from "./review-scope";
import { listsWorkWithoutReview, REVIEW_PAGE_SIZE, type RunsSearch } from "./review-search";
import { ReviewListEnd } from "./ReviewListEnd";
import type { ReviewPeople } from "./ReviewPersonFacet";
import { ReviewResultsSkeleton } from "./ReviewResultsSkeleton";
import { ReviewRowList } from "./ReviewRow";
import {
	clearedRunFacets,
	clearedRunFilters,
	hasRunFilter,
	ReviewRunFilters,
} from "./ReviewRunFilters";
import { REVIEW_RUN_COLUMNS, ReviewRunRow } from "./ReviewRunRow";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";
import { ReviewTabIntro } from "./ReviewTabIntro";
import { UnreviewedWorkRow, workRowKey } from "./UnreviewedWorkRow";

/** A piece of work as a review request names it. */
export interface RequestableWork {
	artifactKind: string;
	artifactId: number;
}

/**
 * What a row can ask for, and what its ask answered: a requested review of its work, from either
 * list on the tab.
 */
export interface ReviewRequestActions {
	/** Whether a person can ask for a review of this kind of work at all. */
	canRequest: (work: RequestableWork) => boolean;
	onRequest: (work: RequestableWork) => void;
	isRequesting: (work: RequestableWork) => boolean;
	/** Why the latest ask about this work started nothing, drawn by the route. */
	refusalFor: (work: RequestableWork) => ReactNode;
}

const workKey = (work: RequestableWork) => `${work.artifactKind}:${work.artifactId}`;

/**
 * A row's Request review: blocked, with the reason, while a review of the same work is queued or
 * running among the loaded rows, or when nobody can ask about its kind of work.
 */
export function rowReviewRequest(
	actions: ReviewRequestActions,
	work: RequestableWork | undefined,
	workName: string,
	activeWork: ReadonlySet<string>,
): RowReviewRequest {
	if (work === undefined) {
		return { work: workName, blocked: REQUEST_BLOCKED.noWork };
	}
	if (!actions.canRequest(work)) {
		return { work: workName, blocked: REQUEST_BLOCKED.kind };
	}
	return {
		work: workName,
		onRequest: () => actions.onRequest(work),
		requesting: actions.isRequesting(work),
		blocked: activeWork.has(workKey(work)) ? REQUEST_BLOCKED.active : undefined,
	};
}

/** The work a review names, as a request names it; absent when it names none or no plain id. */
function reviewWork(review: ReviewRunSummary): RequestableWork | undefined {
	const work = review.target.reviewedWork;
	const artifactId = Number(work?.id);
	return work !== undefined && Number.isSafeInteger(artifactId)
		? { artifactKind: work.kind, artifactId }
		: undefined;
}

/** The work of every loaded review still queued or running: asking again would only join it. */
function activeWorkOf(reviews: readonly ReviewRunSummary[]): ReadonlySet<string> {
	return new Set(
		reviews.flatMap((review) => {
			const work = reviewWork(review);
			return work !== undefined && (review.status === "QUEUED" || review.status === "RUNNING")
				? [workKey(work)]
				: [];
		}),
	);
}

export interface ReviewRunsPageProps {
	search: RunsSearch;
	onSearchChange: (patch: Partial<RunsSearch>) => void;
	/** The reviews the current search selects, as far as they are loaded. */
	reviews: PagedListState<ReviewRunSummary>;
	workspaceSlug: string;
	/**
	 * The work none of whose moments started a review, shown in place of the reviews while the Status
	 * filter is "No review started".
	 */
	unreviewed: PagedListState<TracedArtifact>;
	/** Request review on every row; left out where the screen offers no way to ask. */
	requestReview?: ReviewRequestActions;
	/** The one record the scope is filtered to, by name, for its pill. */
	record?: ScopeRecordFilter;
	/** The options of the shared scope's group, practice and developer facets. */
	groups: FacetSource;
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
	/** The review whose level is in front of the page, whose row keeps a bar on its edge. */
	openReviewId?: string;
}

const LIST_LABEL = "Practice reviews, newest first";

/**
 * The date filter's word for the time a row shows, when the review was created. Provisional: the
 * word "Requested" is the tag of a review a person asked for, so the time cannot share it.
 */
const TIME_LABEL = "Created";

/**
 * The list of reviews, given its loaded pages. It neither fetches nor polls: the route asks for the
 * next page when the end of the list asks for it and keeps asking while a review is still running,
 * and this screen only ever sees the answer — which is why a still-running review looks the same
 * here as anywhere else.
 */
export function ReviewRunsPage({
	search,
	onSearchChange,
	reviews,
	workspaceSlug,
	unreviewed,
	requestReview,
	record,
	groups,
	practices,
	people,
	openReviewId,
}: ReviewRunsPageProps) {
	const listed = listsWorkWithoutReview(search) ? unreviewed : reviews;
	const filters = (
		<ReviewRunFilters
			record={record}
			search={search}
			onPatch={onSearchChange}
			onReset={() => onSearchChange(clearedRunFacets())}
			onClearScope={() => onSearchChange(clearedScope())}
			groups={groups}
			practices={practices}
			people={people}
			total={listed.status === "ready" ? listed.total : undefined}
		/>
	);
	// One section for both lists, so the filters stay mounted, and the Status facet open, as the
	// list under them changes.
	if (listsWorkWithoutReview(search)) {
		return (
			<RunsSection work filters={filters}>
				<UnreviewedWorkResults
					search={search}
					work={unreviewed}
					workspaceSlug={workspaceSlug}
					actions={requestReview}
				/>
			</RunsSection>
		);
	}
	const rows = reviews.status === "ready" ? reviews.rows : [];
	const activeWork = activeWorkOf(rows);
	const hasFilter = hasRunFilter(search);
	// The toolbar's Reset and the empty state's button are one action, not two copies of it.
	const reset = () => onSearchChange(clearedRunFilters());
	// Filtered to one piece of work and nothing else, an empty list is a question about that work.
	const onlyWork =
		search.artifactKind !== undefined && !hasRunFilter({ ...search, artifactKind: undefined });
	let emptyDescription = hasFilter
		? "Change or clear the filters to see more."
		: "Reviews appear when an enabled practice is triggered or a contributor requests one.";
	if (onlyWork) {
		emptyDescription = "See what happened on this work and why no review started.";
	}
	let results: ReactNode;
	if (reviews.status === "error") {
		results = (
			<QueryErrorAlert
				error={reviews.error}
				title="We could not load reviews"
				onRetry={reviews.onRetry}
			/>
		);
	} else if (reviews.status === "loading") {
		results = (
			<ReviewRunDayTableSkeleton
				aria-label={LIST_LABEL}
				timeLabel={TIME_LABEL}
				columns={REVIEW_RUN_COLUMNS}
				rows={REVIEW_PAGE_SIZE}
			/>
		);
	} else {
		results = (
			<>
				<ReviewRunDayTable
					aria-label={LIST_LABEL}
					timeLabel={TIME_LABEL}
					columns={REVIEW_RUN_COLUMNS}
					runs={rows}
					dateOf={(review) => review.createdAt}
					runKey={(review) => review.id}
					renderRow={(review) => (
						<ReviewRunRow
							review={review}
							open={review.id === openReviewId}
							request={
								requestReview &&
								rowReviewRequest(
									requestReview,
									reviewWork(review),
									review.target.reviewedWork === undefined
										? review.target.title
										: reviewedWorkName(review.target.reviewedWork),
									activeWork,
								)
							}
						/>
					)}
					empty={
						<Empty>
							<EmptyHeader>
								<EmptyMedia variant="icon">
									<WorkflowIcon />
								</EmptyMedia>
								<EmptyTitle>
									{onlyWork && "No review ran on this work"}
									{!onlyWork && (hasFilter ? "No reviews match these filters" : "No reviews yet")}
								</EmptyTitle>
								<EmptyDescription>{emptyDescription}</EmptyDescription>
							</EmptyHeader>
							{hasFilter && (
								<EmptyContent>
									{onlyWork ? (
										<Button
											variant="outline"
											size="sm"
											onClick={() => onSearchChange({ status: [NO_REVIEW_STARTED] })}
										>
											Show why no review started
										</Button>
									) : (
										<Button variant="outline" size="sm" onClick={reset}>
											Clear all filters
										</Button>
									)}
								</EmptyContent>
							)}
						</Empty>
					}
				/>
				{rows.length > 0 && <ReviewListEnd {...reviews} noun="reviews" />}
			</>
		);
	}

	return (
		<RunsSection work={false} filters={filters}>
			{results}
		</RunsSection>
	);
}

function RunsSection({
	work,
	filters,
	children,
}: {
	work: boolean;
	filters: ReactNode;
	children: ReactNode;
}) {
	return (
		<section aria-label={work ? "Work without a review" : "Practice reviews"} className="space-y-4">
			<ReviewTabIntro tab={work ? "unreviewed" : "runs"} />
			{filters}
			{children}
		</section>
	);
}

/** No review of work on this list has started, so none of it is queued or running. */
const NO_ACTIVE_WORK: ReadonlySet<string> = new Set();

const UNREVIEWED_LABEL = "Work without a review, most recent first";

interface UnreviewedWorkResultsProps extends Pick<ReviewRunsPageProps, "search" | "workspaceSlug"> {
	work: PagedListState<TracedArtifact>;
	actions: ReviewRequestActions | undefined;
}

/**
 * The work none of whose moments started a review, each with its latest moment and why it started
 * none: the list to open when a pull request was expected to be reviewed and was not. Only the dates
 * and one piece of work narrow it; work no review read has no developer, practice or group yet.
 */
function UnreviewedWorkResults({ search, work, actions }: UnreviewedWorkResultsProps) {
	const rows = work.status === "ready" ? work.rows : [];
	const hasFilter = hasAppliedScope("unreviewed", readScope(search));
	let results: ReactNode;
	if (work.status === "error") {
		results = (
			<QueryErrorAlert
				error={work.error}
				title="We could not load the work"
				onRetry={work.onRetry}
			/>
		);
	} else if (work.status === "loading") {
		results = <ReviewResultsSkeleton label="Loading work" rows={REVIEW_PAGE_SIZE} />;
	} else if (rows.length === 0) {
		results = (
			<Empty variant="outlined">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<RadarIcon />
					</EmptyMedia>
					<EmptyTitle>
						{hasFilter ? "A review started on all of this work" : "A review started on all work"}
					</EmptyTitle>
					<EmptyDescription>
						Work appears here when moments were recorded on it and none of them started a review.
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	} else {
		results = (
			<>
				<ReviewRowList label={UNREVIEWED_LABEL}>
					{rows.map((row) => (
						<UnreviewedWorkRow
							key={workRowKey(row)}
							work={row}
							request={
								actions &&
								rowReviewRequest(actions, row, reviewedWorkName(row.reviewedWork), NO_ACTIVE_WORK)
							}
							refusal={actions?.refusalFor(row)}
						/>
					))}
				</ReviewRowList>
				<ReviewListEnd {...work} noun="work" />
			</>
		);
	}
	return results;
}
