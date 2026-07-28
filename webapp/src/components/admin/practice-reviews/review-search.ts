import { z } from "zod";
import { dayAfterInstant, dayStartInstant, fromDayParam } from "@/lib/date-range-search";
import { multiValue, narrowToEnum } from "@/lib/search-params";
import type {
	Assessment,
	FeedbackChannel,
	FeedbackDeliveryState,
	FeedbackSuppressionReason,
	Presence,
	Severity,
} from "./review-format";
import {
	ASSESSMENT_LABELS,
	CHANNEL_LABELS,
	DELIVERY_STATE_LABELS,
	PRESENCE_LABELS,
	SEVERITY_LABELS,
	SUPPRESSION_REASON_LABELS,
} from "./review-format";

const uuidParam = z.uuid().optional().catch(undefined);
const positiveId = z.coerce.number().int().positive().optional().catch(undefined);
const page = z.coerce.number().int().min(0).optional().catch(undefined);
const day = z.iso.date().optional().catch(undefined);
const PRESENCES = Object.keys(PRESENCE_LABELS) as Presence[];
const ASSESSMENTS = Object.keys(ASSESSMENT_LABELS) as Assessment[];
const SEVERITIES = Object.keys(SEVERITY_LABELS) as Severity[];
const DELIVERY_STATES = Object.keys(DELIVERY_STATE_LABELS) as FeedbackDeliveryState[];
const SUPPRESSION_REASONS = Object.keys(SUPPRESSION_REASON_LABELS) as FeedbackSuppressionReason[];
const CHANNELS = Object.keys(CHANNEL_LABELS) as FeedbackChannel[];
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

export const feedbackSearchSchema = z.object({
	...scope,
	page,
	deliveryState: enumValues(DELIVERY_STATES),
	suppressionReason: enumValues(SUPPRESSION_REASONS),
	channel: enumValues(CHANNELS),
	recipientUserId: positiveId,
});

export const findingsSearchSchema = z.object({
	...scope,
	page,
	areaSlug: multiValue,
	practiceSlug: multiValue,
	presence: enumValues(PRESENCES),
	assessment: enumValues(ASSESSMENTS),
	severity: enumValues(SEVERITIES),
	subjectUserId: positiveId,
});

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
