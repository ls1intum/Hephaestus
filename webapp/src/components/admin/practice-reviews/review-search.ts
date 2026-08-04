import { z } from "zod";
import { dayAfterInstant, dayStartInstant, fromDayParam } from "@/lib/date-range-search";
import { multiValue, narrowToEnum } from "@/lib/search-params";
import type {
	FeedbackChannel,
	FeedbackDeliveryState,
	FeedbackSuppressionReason,
	Presence,
	ReviewResult,
	Severity,
} from "./review-format";
import {
	DELIVERY_STATE_LABELS,
	FEEDBACK_CHANNELS,
	PRESENCE_LABELS,
	REVIEW_RESULT_LABELS,
	SEVERITY_LABELS,
	SUPPRESSION_REASON_LABELS,
} from "./review-format";

const uuidParam = z.uuid().optional().catch(undefined);
const positiveId = z.coerce.number().int().positive().optional().catch(undefined);
const page = z.coerce.number().int().min(0).optional().catch(undefined);
const day = z.iso.date().optional().catch(undefined);
const PRESENCES = Object.keys(PRESENCE_LABELS) as Presence[];
const REVIEW_RESULTS = Object.keys(REVIEW_RESULT_LABELS) as ReviewResult[];
const SEVERITIES = Object.keys(SEVERITY_LABELS) as Severity[];
const DELIVERY_STATES = Object.keys(DELIVERY_STATE_LABELS) as FeedbackDeliveryState[];
const SUPPRESSION_REASONS = Object.keys(SUPPRESSION_REASON_LABELS) as FeedbackSuppressionReason[];
const enumValues = <T extends string>(allowed: readonly T[]) =>
	multiValue.transform((values): T[] | undefined => narrowToEnum(values, allowed));

const scope = {
	agentJobId: uuidParam,
	artifactType: z
		.enum(["PULL_REQUEST", "ISSUE", "CONVERSATION_THREAD"])
		.optional()
		.catch(undefined),
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
		deliveryState: enumValues(DELIVERY_STATES),
		suppressionReason: enumValues(SUPPRESSION_REASONS),
		channel: enumValues<FeedbackChannel>(FEEDBACK_CHANNELS),
		recipientUserId: positiveId,
	})
	.transform(canonicalDateRange);

export const findingsSearchSchema = z
	.object({
		...scope,
		page,
		areaSlug: multiValue,
		practiceSlug: multiValue,
		presence: enumValues(PRESENCES),
		assessment: enumValues(REVIEW_RESULTS),
		severity: enumValues(SEVERITIES),
		subjectUserId: positiveId,
	})
	.transform(canonicalDateRange);

export const runsSearchSchema = z.object({
	page,
	status: z
		.enum(["QUEUED", "RUNNING", "COMPLETED", "FAILED", "TIMED_OUT", "CANCELLED"])
		.optional()
		.catch(undefined),
});

export type FeedbackSearch = z.infer<typeof feedbackSearchSchema>;
export type FindingsSearch = z.infer<typeof findingsSearchSchema>;
export type RunsSearch = z.infer<typeof runsSearchSchema>;

export type ReviewScopeSearch = {
	agentJobId?: string;
	artifactType?: "PULL_REQUEST" | "ISSUE" | "CONVERSATION_THREAD";
	artifactId?: number;
	from?: string;
	to?: string;
};

export function reviewScopeSearch(search: ReviewScopeSearch): ReviewScopeSearch {
	return {
		agentJobId: search.agentJobId,
		artifactType: search.artifactType,
		artifactId: search.artifactType ? search.artifactId : undefined,
		from: search.from,
		to: search.to,
	};
}

function scopeQuery(search: ReviewScopeSearch) {
	const from = fromDayParam(search.from);
	const to = fromDayParam(search.to);
	return {
		agentJobId: search.agentJobId,
		artifactType: search.artifactType,
		artifactId: search.artifactType ? search.artifactId : undefined,
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
		suppressionReason: search.suppressionReason,
		channel: search.channel,
		recipientUserId: search.recipientUserId,
	};
}

export function findingsQuery(search: FindingsSearch, size: number) {
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
	};
}
