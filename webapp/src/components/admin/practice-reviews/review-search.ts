import { z } from "zod";
import { ASSESSMENT_DEFS } from "@/components/practice-vocabulary/assessment-defs";
import { DELIVERY_STATE_DEFS } from "@/components/practice-vocabulary/delivery-outcome-defs";
import { FILTERABLE_PLACES } from "@/components/practice-vocabulary/delivery-place-defs";
import { PRESENCE_DEFS } from "@/components/practice-vocabulary/presence-defs";
import { REVIEW_STATUS_DEFS } from "@/components/practice-vocabulary/review-status-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { statusValues } from "@/components/practice-vocabulary/status-def";
import {
	reasonsInFamilies,
	WITHHOLDING_FAMILY_DEFS,
} from "@/components/practice-vocabulary/withholding-defs";
import { ARTIFACT_KIND_VALUES, type KnownArtifactKind } from "@/lib/artifact-kinds";
import { dayAfterInstant, dayStartInstant, fromDayParam } from "@/lib/date-range-search";
import { multiValue, narrowToEnum } from "@/lib/search-params";

/**
 * One page size for every practice-review list, and for the skeleton that stands in for one.
 *
 * The three lists ran on 25, 25 and 20, and every skeleton drew five rows — so arriving results
 * pushed the pagination down the screen by four rows' worth on two screens and fifteen on the third,
 * which is the jump the skeleton exists to prevent (NN/g, "Skeleton Screens": a skeleton mimics the
 * layout of the page it replaces). One constant, read by the query and by the skeleton, is what
 * stops the two from disagreeing again.
 */
export const REVIEW_PAGE_SIZE = 25;

/**
 * How the observations list is ordered, as the server names it.
 *
 * `ACTIONABILITY` puts shortfalls first, worst severity down to informational, then strengths, then
 * the observations that judged nothing. The endpoint has understood it all along and no screen ever
 * asked: "show me the worst thing first" is the question this list exists for, and it was the one
 * question an operator could not put to it.
 */
export const OBSERVATION_SORTS = ["NEWEST", "ACTIONABILITY"] as const;
export type ObservationSort = (typeof OBSERVATION_SORTS)[number];

const uuidParam = z.uuid().optional().catch(undefined);
const positiveId = z.coerce.number().int().positive().optional().catch(undefined);
const page = z.coerce.number().int().min(0).optional().catch(undefined);
const day = z.iso.date().optional().catch(undefined);
/**
 * Every allowlist below is the status registry's own key set, so a URL filter and the dropdown that
 * offers it cannot come apart — and a value the server adds shows up in both at once.
 *
 * <p>Place narrows to `FILTERABLE_PLACES` rather than the whole wire union, so a hand-typed
 * `channel=PROFILE` is dropped at the door. Accepting it would apply a filter the toolbar cannot
 * show — no chip, no count, an empty page for no visible reason — and the next tick of any place
 * would silently discard it, because a facet emits only the options it was given. A parser must not
 * admit a value the control it feeds has no way to display or clear.
 */
const enumValues = <T extends string>(allowed: readonly T[]) =>
	multiValue.transform((values): T[] | undefined => narrowToEnum(values, allowed));

const scope = {
	agentJobId: uuidParam,
	artifactKind: z.enum(ARTIFACT_KIND_VALUES).optional().catch(undefined),
	artifactId: positiveId,
	from: day,
	to: day,
};

function canonicalDateRange<T extends { from?: string; to?: string }>(search: T): T {
	if (!search.from || (search.to && search.to < search.from)) {
		return { ...search, to: undefined };
	}
	return search;
}

export const feedbackSearchSchema = z
	.object({
		...scope,
		page,
		deliveryState: enumValues(statusValues(DELIVERY_STATE_DEFS)),
		// The URL carries families, not the fourteen reasons: it is the question an operator asks,
		// and `feedbackQuery` expands it to the reasons the API filters on.
		withheldFamily: enumValues(statusValues(WITHHOLDING_FAMILY_DEFS)),
		channel: enumValues(FILTERABLE_PLACES),
		recipientUserId: positiveId,
	})
	.transform(canonicalDateRange);

export const observationsSearchSchema = z
	.object({
		...scope,
		page,
		areaSlug: multiValue,
		practiceSlug: multiValue,
		presence: enumValues(statusValues(PRESENCE_DEFS)),
		assessment: enumValues(statusValues(ASSESSMENT_DEFS)),
		severity: enumValues(statusValues(SEVERITY_DEFS)),
		subjectUserId: positiveId,
		// In the URL like every filter, so an ordering is part of what a bookmarked or pasted link
		// carries. `NEWEST` is the server's default and is left out rather than written down.
		//
		// Spelled `order` rather than `sort`, which is what the endpoint calls it: two other routes
		// already put a `sort` in the URL with entirely different values, and TanStack's search params
		// are one namespace — a third meaning of the word makes `search={(previous) => previous}`, the
		// idiom every link on this screen uses to carry the reader's filters forward, stop compiling.
		order: z.enum(OBSERVATION_SORTS).optional().catch(undefined),
	})
	.transform(canonicalDateRange);

export const runsSearchSchema = z.object({
	page,
	status: z.enum(statusValues(REVIEW_STATUS_DEFS)).optional().catch(undefined),
});

export type FeedbackSearch = z.infer<typeof feedbackSearchSchema>;
export type ObservationsSearch = z.infer<typeof observationsSearchSchema>;
export type RunsSearch = z.infer<typeof runsSearchSchema>;

export type ReviewScopeSearch = {
	agentJobId?: string;
	artifactKind?: KnownArtifactKind;
	artifactId?: number;
	from?: string;
	to?: string;
};

export function reviewScopeSearch(search: ReviewScopeSearch): ReviewScopeSearch {
	return {
		agentJobId: search.agentJobId,
		artifactKind: search.artifactKind,
		artifactId: search.artifactKind ? search.artifactId : undefined,
		from: search.from,
		to: search.to,
	};
}

function scopeQuery(search: ReviewScopeSearch) {
	const from = fromDayParam(search.from);
	const to = fromDayParam(search.to);
	return {
		agentJobId: search.agentJobId,
		artifactKind: search.artifactKind,
		artifactId: search.artifactKind ? search.artifactId : undefined,
		from: from ? dayStartInstant(from) : undefined,
		to: to ? dayAfterInstant(to) : undefined,
	};
}

export function feedbackQuery(search: FeedbackSearch, size: number) {
	return {
		...scopeQuery(search),
		page: search.page ?? 0,
		size,
		deliveryState: search.deliveryState,
		// A family expands to exactly the reasons it covers, so the rows that come back are the rows
		// whose own sentence sits under that family heading.
		suppressionReason: search.withheldFamily?.length
			? reasonsInFamilies(search.withheldFamily)
			: undefined,
		channel: search.channel,
		recipientUserId: search.recipientUserId,
	};
}

export function observationsQuery(search: ObservationsSearch, size: number) {
	return {
		...scopeQuery(search),
		page: search.page ?? 0,
		size,
		areaSlug: search.areaSlug?.length ? search.areaSlug : undefined,
		practiceSlug: search.practiceSlug?.length ? search.practiceSlug : undefined,
		presence: search.presence,
		assessment: search.assessment,
		severity: search.severity,
		subjectUserId: search.subjectUserId,
		sort: search.order,
	};
}
