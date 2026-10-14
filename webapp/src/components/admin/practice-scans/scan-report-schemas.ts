import { z } from "zod";

import type { AccountRef, ItemRef, ResultList } from "@/api/types.gen";
import { PROBLEM_BASELINE_STATES } from "@/components/practice-vocabulary/scan-baseline-defs";
import { isRecord } from "@/lib/is-record";

/**
 * The scan report's two lists reach the client untyped: the problem list has no generated DTO, and
 * the generated item union is circular, so its members collapse to `any`. Neither passes through a
 * response transformer, so every timestamp is still the ISO string. Both are validated here, at the
 * route boundary, and the components take what these schemas produce.
 *
 * A field the server writes as `null` is accepted when left out, as the same value.
 */
function orNull<T extends z.ZodType>(schema: T) {
	return schema.nullish().transform((value) => value ?? null);
}

const OUTCOMES = ["MET", "NOT_MET", "DOES_NOT_APPLY", "COULD_NOT_DETERMINE"] as const;
const SEVERITIES = ["CRITICAL", "MAJOR", "MINOR", "TRIVIAL"] as const;
const CURRENTNESS = ["CURRENT", "STALE", "UNVERIFIABLE"] as const;

const itemRefSchema = z.object({
	id: z.string(),
	kind: z.string(),
	label: z.string(),
	container: orNull(z.string()),
	parentId: orNull(z.string()),
	path: orNull(z.string()),
	provider: orNull(z.enum(["GITHUB", "GITLAB", "SLACK", "OUTLINE"])),
	removedAt: orNull(z.coerce.date()),
	title: orNull(z.string()),
	url: orNull(z.string()),
}) satisfies z.ZodType<ItemRef>;

const entryBase = {
	item: itemRefSchema,
	practiceSlug: z.string(),
	practiceName: z.string(),
	itemVersion: z.string(),
};

const resultFields = {
	resultId: z.string(),
	sourceReviewId: z.string(),
	outcome: z.enum(OUTCOMES),
	severity: orNull(z.enum(SEVERITIES)),
	summary: orNull(z.string()),
	problemCount: z.number().int().nonnegative(),
	currentness: z.enum(CURRENTNESS),
	recordedAt: z.coerce.date(),
};

/** An earlier result of the same file and practice: earlier evidence, never this scan's verdict. */
const earlierResultSchema = z.object({
	id: z.string(),
	outcome: z.enum(OUTCOMES),
	summary: orNull(z.string()),
	recordedAt: z.coerce.date(),
}) satisfies z.ZodType<Pick<ResultList, "id" | "outcome" | "summary" | "recordedAt">>;

const waitingFields = {
	reason: orNull(z.string()),
	reasonDescription: orNull(z.string()),
	lastResult: orNull(earlierResultSchema),
};

const scanItemEntrySchema = z.discriminatedUnion("status", [
	z.object({ ...entryBase, ...resultFields, status: z.literal("JUDGED") }),
	z.object({ ...entryBase, ...resultFields, status: z.literal("REUSED") }),
	z.object({
		...entryBase,
		status: z.literal("WITHHELD"),
		reason: z.string(),
		reasonDescription: orNull(z.string()),
	}),
	z.object({ ...entryBase, ...waitingFields, status: z.literal("LISTED") }),
	z.object({ ...entryBase, ...waitingFields, status: z.literal("NOT_REVIEWED") }),
]);

export const scanItemsPageSchema = z.object({
	content: z.array(scanItemEntrySchema),
	nextCursor: orNull(z.string()),
	hasNext: z.boolean(),
});

const citationSchema = z.object({
	path: orNull(z.string()),
	startLine: orNull(z.number()),
	endLine: orNull(z.number()),
});

const scanProblemEntrySchema = z.object({
	problem: z.object({
		id: z.string(),
		resultId: z.string(),
		severity: z.enum(SEVERITIES),
		summary: z.string(),
		explanation: orNull(z.string()),
		nextStep: orNull(z.string()),
		citations: z.array(citationSchema).catch([]),
	}),
	resultId: z.string(),
	item: itemRefSchema,
	practiceSlug: z.string(),
	practiceName: z.string(),
	baselineState: orNull(z.enum(PROBLEM_BASELINE_STATES)),
	previousProblemId: orNull(z.string()),
	absentReason: orNull(z.literal("REPAIRED")),
});

export const scanProblemsPageSchema = z.object({
	content: z.array(scanProblemEntrySchema),
	nextCursor: orNull(z.string()),
	hasNext: z.boolean(),
});

/** One file against one practice in a scan. */
export type ScanItemEntry = z.infer<typeof scanItemEntrySchema>;
export type ScanItemsPage = z.infer<typeof scanItemsPageSchema>;
/** One problem a scan recorded, with how it compares with the scan before. */
export type ScanProblemEntry = z.infer<typeof scanProblemEntrySchema>;
export type ScanProblemsPage = z.infer<typeof scanProblemsPageSchema>;

const requesterSchema = z.object({
	displayName: orNull(z.string()),
	identities: z.array(z.object({ login: orNull(z.string()) })).catch([]),
}) satisfies z.ZodType<Pick<AccountRef, "displayName">>;

/**
 * The person who started a scan, by name. The generated type of `requestedBy` is `unknown` (a
 * nullable reference the generator widens), so it is read through a schema rather than trusted.
 */
export function requesterName(requestedBy: unknown): string | undefined {
	const parsed = requesterSchema.safeParse(requestedBy);
	if (!parsed.success) {
		return undefined;
	}
	const login = parsed.data.identities.find((identity) => identity.login !== null)?.login;
	return parsed.data.displayName ?? login ?? undefined;
}

/** Whether a nullable record the generator typed `unknown` is present. */
export function isPresent(value: unknown): boolean {
	return isRecord(value);
}
