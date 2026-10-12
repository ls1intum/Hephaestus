import { z } from "zod";

import { statusValues } from "@/components/common/status-def";
import { REVIEW_FILTER_MAX_LENGTH } from "@/components/practice-trace/trace-format";
import { DELIVERY_STATE_DEFS } from "@/components/practice-vocabulary/delivery-outcome-defs";
import { FEEDBACK_LOCATION_DEFS } from "@/components/practice-vocabulary/feedback-location-defs";
import { OBSERVATION_ORIGIN_DEFS } from "@/components/practice-vocabulary/observation-origin-defs";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import {
	NO_REVIEW_STARTED,
	ON_HOLD,
	WAITING,
	RESULT_PROCESSING_DEFS,
	REVIEW_STATUS_FILTER_DEFS,
	type ReviewStatus,
	type ReviewStatusFilter,
} from "@/components/practice-vocabulary/review-status-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import {
	reasonsInFamilies,
	WITHHOLDING_FAMILY_DEFS,
} from "@/components/practice-vocabulary/withholding-defs";
import { ARTIFACT_KIND_VALUES } from "@/lib/artifact-kinds";
import { dayAfterInstant, dayStartInstant, fromDayParam } from "@/lib/date-range-search";
import { multiValue, narrowToEnum, nonEmpty } from "@/lib/search-params";
import { hasText } from "@/lib/text";

import type { ReviewScopeSearch } from "./review-scope";

/**
 * Read by the query and by the skeleton that stands in for the results, so a skeleton cannot draw a
 * different number of rows than the first page it replaces.
 */
export const REVIEW_PAGE_SIZE = 25;

/**
 * How often a queued or running review is re-asked for, on every screen that watches one. Applied
 * through TanStack Query's `refetchInterval`, which stops on its own at a terminal status.
 */
export const ACTIVE_REVIEW_POLL_MS = 5000;

/**
 * Every admin read of what reviews produced, which a change to one review, observation or piece of
 * feedback can move: its row in any list, every count, the queue and the review time.
 */
export const PRACTICE_REVIEW_READS: ReadonlySet<string> = new Set([
	"getPracticeReviewOverview",
	"listPracticeReviews",
	"listPracticeReviewObservations",
	"getPracticeReviewObservation",
	"listPracticeReviewFeedback",
	"getPracticeReviewFeedback",
	"getPracticeReviewLineage",
	"getPracticeReviewQueue",
	"getPracticeReviewTiming",
	"countPracticeReviewFeedbackByLocation",
	"countPracticeReviewFeedbackByReason",
]);

/**
 * Ordering names the server understands. `ACTIONABILITY` puts not met observations first, worst
 * severity down to informational, then met observations, then the observations that judged nothing.
 */
export const OBSERVATION_SORTS = ["NEWEST", "ACTIONABILITY"] as const;

export type ObservationSort = (typeof OBSERVATION_SORTS)[number];

/** Ordering names the server understands for feedback: when it was composed, either way round. */
export const FEEDBACK_SORTS = ["NEWEST", "OLDEST"] as const;

export type FeedbackSort = (typeof FEEDBACK_SORTS)[number];

const uuidParam = z.uuid().optional().catch(undefined);
const positiveId = z.coerce.number().int().positive().optional().catch(undefined);
const day = z.iso.date().optional().catch(undefined);
/**
 * Every allowlist below is a status registry's own key set, so a URL filter and the dropdown that
 * offers it cannot come apart.
 *
 * <p>A parser must not admit a value the control it feeds has no way to display or clear: a
 * hand-typed value the toolbar cannot offer would apply an invisible filter that the next tick of the
 * facet then discards without saying so — a facet emits only the options it was given.
 */
const enumValues = <T extends string>(allowed: readonly T[]) =>
	multiValue.transform((values): T[] | undefined => narrowToEnum(values, allowed));

/**
 * The shared scope that every tab keeps in its URL; `review-scope.ts` says which tab applies which
 * part. The person and the practices are declared by each schema, because the feedback list names
 * its person `recipientUserId`.
 */
const scope = {
	agentJobId: uuidParam,
	observationId: uuidParam,
	feedbackId: uuidParam,
	selectedPractice: z.string().min(1).max(REVIEW_FILTER_MAX_LENGTH).optional().catch(undefined),
	artifactKind: z.enum(ARTIFACT_KIND_VALUES).optional().catch(undefined),
	artifactId: positiveId,
	// Only `true`: the record above was picked with a crosshair, not filtered by (`review-selection.ts`).
	selected: z.literal(true).optional().catch(undefined),
	from: day,
	to: day,
	groupSlug: multiValue,
};

function canonicalDateRange<T extends { from?: string; to?: string }>(search: T): T {
	if (!hasText(search.from) || (hasText(search.to) && search.to < search.from)) {
		return { ...search, to: undefined };
	}
	return search;
}

export const feedbackSearchSchema = z
	.object({
		...scope,
		deliveryState: enumValues(statusValues(DELIVERY_STATE_DEFS)),
		// The URL carries families, not individual reasons: the family is the question an operator
		// asks, and `feedbackQuery` expands it to the reasons the API filters on.
		withheldFamily: enumValues(statusValues(WITHHOLDING_FAMILY_DEFS)),
		location: enumValues(statusValues(FEEDBACK_LOCATION_DEFS)),
		recipientUserId: positiveId,
		practiceSlug: multiValue,
		// The endpoint's `sort`. The observations list declares `order` too, with other values. A link
		// without `from` types `previous` in `search={(previous) => …}` as every route's search, where
		// a param two sibling routes declare with different types holds both, so returning it to either
		// list stops compiling: a relative link on either list names its `from`.
		order: z.enum(FEEDBACK_SORTS).optional().catch(undefined),
	})
	.transform(canonicalDateRange);

export const observationsSearchSchema = z
	.object({
		...scope,
		practiceSlug: multiValue,

		outcome: enumValues(statusValues(OUTCOME_DEFS)),
		invalidated: z.boolean().optional().catch(undefined),
		disputed: z.boolean().optional().catch(undefined),

		severity: enumValues(statusValues(SEVERITY_DEFS)),
		subjectUserId: positiveId,
		origin: enumValues(statusValues(OBSERVATION_ORIGIN_DEFS)),
		// The endpoint's `sort`; the feedback list's `order` says what sharing the word costs.
		order: z.enum(OBSERVATION_SORTS).optional().catch(undefined),
	})
	.transform(canonicalDateRange);

/**
 * `from`/`to` window when a review was *requested*. The status "No review started" lists the work on
 * which no review started instead of the reviews. An address from before, with `withoutReview=true`,
 * opens it too.
 */
export const runsSearchSchema = z
	.object({
		...scope,
		practiceSlug: multiValue,
		subjectUserId: positiveId,
		status: enumValues(statusValues(REVIEW_STATUS_FILTER_DEFS)),
		resultProcessing: enumValues(statusValues(RESULT_PROCESSING_DEFS)),
		// Only `true` filters: the overview links here with it, and its pill clears it.
		finishedAfterMerge: z.literal(true).optional().catch(undefined),
		// The old toggle's parameter: read as the status, and cleared by the next change of the status.
		withoutReview: z.literal(true).optional().catch(undefined),
	})
	.transform((search) => {
		const work: ReviewStatusFilter[] = [NO_REVIEW_STARTED];
		return canonicalDateRange(search.withoutReview === true ? { ...search, status: work } : search);
	});

/** Whether the Reviews tab lists the work on which no review started instead of the reviews. */
export function listsWorkWithoutReview(search: Pick<RunsSearch, "status">): boolean {
	return search.status?.includes(NO_REVIEW_STARTED) === true;
}

/**
 * The address of the Work tab, which is gone: read only to send it to the Reviews tab. `kind` is the
 * work kind it filtered by, which the Reviews tab has no facet for.
 */
export const workSearchSchema = z
	.object({
		...scope,
		practiceSlug: multiValue,
		subjectUserId: positiveId,
		kind: z.string().min(1).max(REVIEW_FILTER_MAX_LENGTH).optional().catch(undefined),
	})
	.transform(canonicalDateRange);

/** The overview applies none of the scope, but keeps it for the next tab. */
export const overviewSearchSchema = z
	.object({
		...scope,
		practiceSlug: multiValue,
		subjectUserId: positiveId,
		// The Practices table's own filters, apart from the scope the overview does not apply.
		practicesGroup: multiValue,
		showTurnedOff: z.literal(true).optional().catch(undefined),
		// The default, "Needs a look first", is left out of the URL.
		practicesSort: z.literal("BUSIEST").optional().catch(undefined),
	})
	.transform(canonicalDateRange);

/**
 * One review's own page keeps the scope for the tabs, and adds the record it opens on: `focus`,
 * `observation:<id>`, `feedback:<id>` or `work` (`review-trace.ts`).
 */
export const reviewPageSearchSchema = z
	.object({
		...scope,
		practiceSlug: multiValue,
		subjectUserId: positiveId,
		focus: z.string().min(1).max(REVIEW_FILTER_MAX_LENGTH).optional().catch(undefined),
	})
	.transform(canonicalDateRange);

export type FeedbackSearch = z.infer<typeof feedbackSearchSchema>;
export type ObservationsSearch = z.infer<typeof observationsSearchSchema>;
export type RunsSearch = z.infer<typeof runsSearchSchema>;

/**
 * A picked pair of days becomes the half-open instant window the API takes: midnight on `from`, and
 * midnight on the day *after* `to`, so the day the reader picked last is included whole.
 */
export function dateWindowQuery(search: { from?: string; to?: string }) {
	const from = fromDayParam(search.from);
	const to = fromDayParam(search.to);
	return {
		from: from ? dayStartInstant(from) : undefined,
		to: to ? dayAfterInstant(to) : undefined,
	};
}

/** The selection as the review, observation and feedback lists filter by it. */
function selectionQuery(search: ReviewScopeSearch) {
	return {
		agentJobId: search.agentJobId,
		artifactKind: search.artifactKind,
		artifactId: search.artifactKind ? search.artifactId : undefined,
	};
}

function scopeQuery(search: ReviewScopeSearch) {
	return { ...selectionQuery(search), ...dateWindowQuery(search) };
}

/**
 * The practices a list narrows to: a tracked practice alone, since tracking it is the question the
 * reader asks now, or else the practice filter.
 */
function practiceQuery(search: { practiceSlug?: string[]; selectedPractice?: string }) {
	if (hasText(search.selectedPractice)) {
		return [search.selectedPractice];
	}
	return search.practiceSlug !== undefined && search.practiceSlug.length > 0
		? search.practiceSlug
		: undefined;
}

/** The one practice a list narrows to, or undefined where it narrows to none or to several. */
export function onePracticeQuery(search: {
	practiceSlug?: string[];
	selectedPractice?: string;
}): string | undefined {
	const slugs = practiceQuery(search);
	return slugs?.length === 1 ? slugs[0] : undefined;
}

export function runsQuery(search: Partial<RunsSearch>, size: number) {
	return {
		...scopeQuery(search),
		size,
		status: nonEmpty(
			(search.status ?? []).filter(
				(status): status is ReviewStatus =>
					status !== NO_REVIEW_STARTED && status !== ON_HOLD && status !== WAITING,
			),
		),
		// "On hold" and "Waiting" are no status a review has: the server adds the held reviews, or the
		// queued ones a worker could pick up now, to the listed statuses.
		onHold: search.status?.includes(ON_HOLD) === true ? true : undefined,
		waiting: search.status?.includes(WAITING) === true ? true : undefined,
		resultProcessing: search.resultProcessing,
		finishedAfterMerge: search.finishedAfterMerge,
		subjectUserId: search.subjectUserId,
		practiceSlug: practiceQuery(search),
		groupSlug: nonEmpty(search.groupSlug ?? []),
	};
}

/**
 * The work whose moments started no review, in the range and narrowed to one piece of work where the
 * scope names one. The rest of the scope does not apply: work that no review read has no developer,
 * practice or group yet.
 */
export function workWithoutReviewQuery(search: Partial<RunsSearch>, size: number) {
	return {
		...dateWindowQuery(search),
		size,
		withoutReview: true,
		artifactKind: search.artifactKind,
		artifactId: search.artifactKind ? search.artifactId : undefined,
	};
}

export function feedbackQuery(search: Partial<FeedbackSearch>, size: number) {
	return {
		...scopeQuery(search),
		observationId: search.observationId,
		size,
		deliveryState: search.deliveryState,
		suppressionReason:
			search.withheldFamily !== undefined && search.withheldFamily.length > 0
				? reasonsInFamilies(search.withheldFamily)
				: undefined,
		location: search.location,
		recipientUserId: search.recipientUserId,
		groupSlug: nonEmpty(search.groupSlug ?? []),
		practiceSlug: practiceQuery(search),
		sort: search.order,
	};
}

export function observationsQuery(search: Partial<ObservationsSearch>, size: number) {
	return {
		...scopeQuery(search),
		size,
		groupSlug:
			search.groupSlug !== undefined && search.groupSlug.length > 0 ? search.groupSlug : undefined,
		practiceSlug: practiceQuery(search),

		outcome: search.outcome,
		invalidated: search.invalidated,
		disputed: search.disputed,

		severity: search.severity,
		subjectUserId: search.subjectUserId,
		origin: search.origin,
		sort: search.order,
	};
}
