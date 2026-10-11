/** The definition contract of a practice script, as the runner parses it. */
import type {
	EmbeddingModelV4,
	EmbeddingModelV4CallOptions,
	EmbeddingModelV4Result,
	Experimental_DecisionModelV4,
	Experimental_DecisionModelV4CallOptions,
	Experimental_DecisionModelV4Result,
	LanguageModelV4,
	LanguageModelV4CallOptions,
	LanguageModelV4GenerateResult,
	RerankingModelV4,
	RerankingModelV4CallOptions,
	RerankingModelV4Result,
} from "@ai-sdk/provider";
import { z } from "zod";

import { isJsonObject } from "./json.ts";

/** A practice slug as the server writes it: `SandboxLayout.PRACTICE_SLUG`, anchored. */
export const PRACTICE_SLUG = /^[a-z0-9][a-z0-9-]{0,63}$/u;

/**
 * The header that names the practice of every model call of a practice script, so the proxy can
 * attribute its usage.
 */
export const PRACTICE_SCRIPT_PRACTICE_HEADER = "x-hephaestus-practice";

/**
 * How a practice's script ended, as `<slug>.json` records it. "skipped": a model that the script
 * requires is not available. "timeout": the script was stopped at its deadline, or the stage deadline
 * came before it started.
 */
export const RUN_STATUSES = ["ok", "skipped", "error", "timeout"] as const;
export type RunStatus = (typeof RUN_STATUSES)[number];
/** How a script that returned no result ended: with an error, or at its deadline. */
export type FailedStatus = Extract<RunStatus, "error" | "timeout">;
export const isFailed = (status: RunStatus): status is FailedStatus =>
	status === "error" || status === "timeout";

/** What a practice script may ask a model to do. One slot, one kind of model. */
export const MODEL_SLOTS = ["chat", "decision", "embedding", "reranking"] as const;
export type ModelSlot = (typeof MODEL_SLOTS)[number];
export const modelSlotSchema = z.enum(MODEL_SLOTS);

/**
 * The most items that one call to a slot's model may hold: decision questions, embedding values and
 * rerank documents. The proxy refuses a larger call on the slot's own API (`LlmProxyService`), so the
 * runner refuses it first, as "too-large", whatever the protocol of the slot's model.
 */
export const MODEL_SLOT_CAPS = {
	decision: 64,
	embedding: 64,
	reranking: 256,
} as const;

/**
 * "required": without a bound model the script does not run, and its section says which model it
 * needs. "optional": the script runs without it and says what it could not rate.
 */
const needSchema = z.enum(["required", "optional"]);
export type Need = z.infer<typeof needSchema>;

/** Why something was not rated. Never "nothing found". */
const REASONS = [
	"unavailable",
	"budget",
	"deadline",
	"too-large",
	"off-format",
	"refused",
	"error",
] as const;
const reasonSchema = z.enum(REASONS);
export type Reason = z.infer<typeof reasonSchema>;

/** One line of text: a newline would let a script write its own headings into the reviewer's turn. */
const line = (max: number) =>
	z
		.string()
		.min(1)
		.max(max)
		.refine((text) => !/[\r\n]/u.test(text), "must be one line");

const LEAD_KIND = /^[a-z][a-z0-9-]{1,47}$/u;

export const metaSchema = z.strictObject({
	models: z.partialRecord(modelSlotSchema, needSchema).optional(),
	/** This practice's token ceiling, inside the stage's own ceiling. */
	tokens: z.int().positive().max(500_000).optional(),
	/** Lead kind → the sentence that the reviewer reads for it. */
	kinds: z.record(z.string().regex(LEAD_KIND), line(300)),
});
export type ParsedMeta = z.infer<typeof metaSchema>;

const scalar = z.union([
	line(200),
	z.number().refine(Number.isFinite, "must be finite"),
	z.boolean(),
]);
const factsSchema = z
	.record(z.string().regex(/^[A-Za-z][A-Za-z0-9]{0,63}$/u), scalar)
	.refine((facts) => Object.keys(facts).length <= 32, "at most 32 facts");
export type Facts = z.infer<typeof factsSchema>;

/**
 * Where a lead points, in the coordinates an observation cites: a line the change adds or removes,
 * a repository file, or a captured record under the context folder. The runner writes the quote.
 */
const citationSchema = z.union([
	z.strictObject({
		change: z.string().min(1),
		line: z.int().positive(),
		endLine: z.int().positive().optional(),
		side: z.enum(["OLD", "NEW"]).optional(),
	}),
	z.strictObject({
		file: z.string().min(1),
		line: z.int().positive(),
		endLine: z.int().positive().optional(),
	}),
	z.strictObject({
		record: z.string().min(1),
		line: z.int().nonnegative().optional(),
	}),
]);
export type Citation = z.infer<typeof citationSchema>;

const ratingSchema = z.strictObject({
	value: z.string().regex(LEAD_KIND),
	score: z.number().min(0).max(1),
});

const leadSchema = z.strictObject({
	at: citationSchema,
	kind: z.string().regex(LEAD_KIND),
	facts: factsSchema.optional(),
	/** A model's answer about the cited place. The reviewer sees that it was rated, never the score. */
	rating: ratingSchema.optional(),
});
export type Lead = z.infer<typeof leadSchema>;

const unratedSchema = z.strictObject({
	what: line(160),
	count: z.int().nonnegative(),
	reason: reasonSchema,
});
export type Unrated = z.infer<typeof unratedSchema>;

export const practiceScriptResultSchema = z.strictObject({
	leads: z.array(leadSchema).max(200),
	facts: factsSchema.optional(),
	unrated: z.array(unratedSchema).max(20).optional(),
	/** Directions to the reviewer. The script's author writes them; model text does not belong here. */
	directions: z.array(line(300)).max(10).optional(),
});
export type PracticeScriptResult = z.infer<typeof practiceScriptResultSchema>;

const PROTOCOLS = [
	"openai-completions",
	"openai-responses",
	"openai-decisions",
	"openai-embeddings",
	"cohere-rerank",
] as const;
const boundModelSchema = z.strictObject({
	protocol: z.enum(PROTOCOLS),
	modelId: z.string().min(1),
	reasoningEffort: z.string().optional(),
});
/**
 * The models the server bound for this job, as `practice-script-models.json` holds them: one entry
 * per slot that has a model. A slot without an entry has no model.
 */
export const boundModelsSchema = z.partialRecord(modelSlotSchema, boundModelSchema);
export type BoundModels = z.infer<typeof boundModelsSchema>;

/** The provider model of each slot, its call options, and what a call returns. */
interface SlotTypes {
	chat: {
		model: LanguageModelV4;
		options: LanguageModelV4CallOptions;
		result: LanguageModelV4GenerateResult;
	};
	decision: {
		model: Experimental_DecisionModelV4;
		options: Experimental_DecisionModelV4CallOptions;
		result: Experimental_DecisionModelV4Result;
	};
	embedding: {
		model: EmbeddingModelV4;
		options: EmbeddingModelV4CallOptions;
		result: EmbeddingModelV4Result;
	};
	reranking: {
		model: RerankingModelV4;
		options: RerankingModelV4CallOptions;
		result: RerankingModelV4Result;
	};
}

/** The provider model of each slot: in the runner, in a script's process, and in `ctx.models`. */
export type SlotModels = { [S in ModelSlot]: SlotTypes[S]["model"] };

/** What one slot's model returned, named by its slot. */
export type SlotAnswer<S extends ModelSlot = ModelSlot> = {
	[K in S]: { slot: K; result: SlotTypes[K]["result"] };
}[S];

type CallOptions<S extends ModelSlot> = SlotTypes[S]["options"] & Record<string, unknown>;

export interface SlotCall<S extends ModelSlot> {
	/** The options a script may set. Provider options and headers stay with the runner. */
	options: readonly string[];
	/**
	 * Whether the options hold each field that a call to this slot's model requires, as a list, a record
	 * or text. It also checks the entries of embedding values and rerank documents, and the type and
	 * instructions of each decision question. It does not check a chat prompt's messages, what a
	 * decision state holds, or the optional fields: the provider client checks them, and a call that it
	 * refuses is not rated, with "error".
	 */
	accepts: (options: Record<string, unknown>) => options is CallOptions<S>;
	/** The items a call holds, and the most that one call may hold. */
	items?: { count: (options: Record<string, unknown>) => number; max: number };
	/** Call the model. The tokens are input and output together, or undefined when it reports none. */
	call: (
		model: SlotTypes[S]["model"],
		options: SlotTypes[S]["options"],
	) => Promise<{ value: SlotAnswer; tokens: number | undefined }>;
}

/** The entries of a list, or the keys of a record. */
function size(value: unknown): number {
	if (Array.isArray(value)) {
		return value.length;
	}
	return isJsonObject(value) ? Object.keys(value).length : 0;
}

const sum = (...counts: (number | undefined)[]): number | undefined =>
	counts.every((n) => n === undefined)
		? undefined
		: counts.reduce<number>((s, n) => s + (n ?? 0), 0);

function isChatCall(options: Record<string, unknown>): options is CallOptions<"chat"> {
	return Array.isArray(options.prompt);
}

const QUESTION_TYPES = new Set<unknown>(["boolean", "choice", "score"]);

function isDecisionCall(options: Record<string, unknown>): options is CallOptions<"decision"> {
	const { state, questions } = options;
	return (
		(typeof state === "string" || (typeof state === "object" && state !== null)) &&
		isJsonObject(questions) &&
		Object.values(questions).every(
			(question) =>
				isJsonObject(question) &&
				QUESTION_TYPES.has(question.type) &&
				question.instructions !== undefined,
		)
	);
}

function isEmbeddingCall(options: Record<string, unknown>): options is CallOptions<"embedding"> {
	return Array.isArray(options.values) && options.values.every((v) => typeof v === "string");
}

function isRerankingCall(options: Record<string, unknown>): options is CallOptions<"reranking"> {
	const { query, documents } = options;
	if (typeof query !== "string" || !isJsonObject(documents) || !Array.isArray(documents.values)) {
		return false;
	}
	return documents.type === "text"
		? documents.values.every((v) => typeof v === "string")
		: documents.type === "object" && documents.values.every(isJsonObject);
}

/** How the runner calls each slot's model for a script. */
export const MODEL_SLOT_CALLS = {
	chat: {
		options: [
			"prompt",
			"tools",
			"toolChoice",
			"responseFormat",
			"temperature",
			"topP",
			"seed",
			"stopSequences",
			"maxOutputTokens",
		],
		accepts: isChatCall,
		call: async (model, options) => {
			const result = await model.doGenerate(options);
			const { inputTokens, outputTokens } = result.usage;
			return {
				value: { slot: "chat", result },
				tokens: sum(inputTokens.total, outputTokens.total),
			};
		},
	},
	decision: {
		options: ["state", "questions"],
		accepts: isDecisionCall,
		items: { count: (options) => size(options.questions), max: MODEL_SLOT_CAPS.decision },
		call: async (model, options) => {
			const result = await model.doDecide(options);
			return {
				value: { slot: "decision", result },
				tokens: sum(result.usage?.inputTokens, result.usage?.outputTokens),
			};
		},
	},
	embedding: {
		options: ["values"],
		accepts: isEmbeddingCall,
		items: { count: (options) => size(options.values), max: MODEL_SLOT_CAPS.embedding },
		call: async (model, options) => {
			const result = await model.doEmbed(options);
			return { value: { slot: "embedding", result }, tokens: result.usage?.tokens };
		},
	},
	reranking: {
		options: ["documents", "query", "topN"],
		accepts: isRerankingCall,
		items: {
			count: (options) =>
				size(isJsonObject(options.documents) ? options.documents.values : undefined),
			max: MODEL_SLOT_CAPS.reranking,
		},
		call: async (model, options) => {
			const result = await model.doRerank(options);
			return { value: { slot: "reranking", result }, tokens: undefined };
		},
	},
} satisfies { [S in ModelSlot]: SlotCall<S> };

/*
 * What a script reads about its review: the kind of the work, its record, the cause, the role the
 * practice is about, and the previous claim. The server stages them; the runner validates them here.
 */

/**
 * The kinds of reviewed work, the moments of each, and the dimensions of its review state, as the
 * artifact descriptors declare them. A moment is each signal of the kind but the request by hand.
 * `PracticeScriptContractSyncTest` keeps these lists equal to the descriptors, which stay their home.
 */
export const KINDS = [
	"scm.pull_request",
	"scm.issue",
	"chat.conversation_thread",
	"docs.document",
] as const;
export type Kind = (typeof KINDS)[number];

export const MOMENTS = {
	"scm.pull_request": [
		"scm.pull_request.opened",
		"scm.pull_request.ready",
		"scm.pull_request.synchronized",
		"scm.pull_request.edited",
		"scm.pull_request.linked_issue_updated",
		"scm.pull_request.reviewed",
		"scm.pull_request.merged",
		"scm.pull_request.closed",
	],
	"scm.issue": ["scm.issue.opened", "scm.issue.updated", "scm.issue.closed"],
	"chat.conversation_thread": ["chat.conversation_thread.settled"],
	"docs.document": ["docs.document.published", "docs.document.updated", "docs.document.archived"],
} as const satisfies Record<Kind, readonly [string, ...string[]]>;
export type Moment<K extends Kind = Kind> = (typeof MOMENTS)[K][number];

export const REVIEW_STATES = {
	"scm.pull_request": { draftStatus: ["DRAFT", "NOT_DRAFT"], state: ["OPEN", "CLOSED", "MERGED"] },
	"scm.issue": { state: ["OPEN", "CLOSED"] },
	"chat.conversation_thread": {},
	"docs.document": { state: ["ACTIVE", "ARCHIVED"] },
} as const satisfies Record<Kind, Record<string, readonly [string, ...string[]]>>;

/** `ObservationOrigin`: the population that the review's observations belong to. */
export const ORIGINS = ["LIVE", "MANUAL", "BACKFILL"] as const;
export const OUTCOMES = ["MET", "NOT_MET", "NOT_APPLICABLE", "UNDETERMINED"] as const;
export const SEVERITIES = ["CRITICAL", "MAJOR", "MINOR", "INFO"] as const;
/** The roles that a practice can be about. A review is never about an assignee. */
export const SUBJECT_ROLES = ["AUTHOR", "REVIEWER", "MERGER"] as const;
export const CAUSE_TYPES = ["moment", "recheck", "requested"] as const;

/** The file names of a practice's inputs beside its script: `SandboxLayout` on the server. */
export const CAUSE_FILE_SUFFIX = ".cause.json";
export const PREVIOUS_FILE_SUFFIX = ".previous.json";

const COMMIT = /^(?:[0-9a-f]{40}|[0-9a-f]{64})$/u;
/** A full commit id, as git prints it. */
export const isCommit = (value: string): boolean => COMMIT.test(value);

/*
 * The record of the reviewed work, one for each kind, as the server's content source stages it in the
 * context folder. A field that the server does not write is not in the type: the runner drops an
 * unknown field, and a known field of another shape fails the record.
 */

/** `metadata.json` of a pull request: `PullRequestContentSource.buildPullRequestMetadata`. */
const pullRequestRecordSchema = z.object({
	/** Which time each part describes: `admission_fields` come from the job, the rest from the mirror. */
	basis: z.object({
		admission_fields: z.array(z.string()),
		admitted_at: z.string().optional(),
		other_fields: z.string(),
		mirror_synced_at: z.string().optional(),
	}),
	pr_number: z.int(),
	pr_url: z.string(),
	repository_full_name: z.string(),
	source_branch: z.string(),
	target_branch: z.string(),
	/** The reviewed head. */
	commit_sha: z.string(),
	subject_role: z.enum(["AUTHOR", "REVIEWER"]),
	/** Absent when the job does not carry the text: then it is not known. */
	title: z.string().optional(),
	body: z.string().nullable().optional(),
	state: z.enum(["OPEN", "CLOSED", "MERGED"]).optional(),
	is_draft: z.boolean(),
	/** Apart from `state`: a pull request closed by its merge is stored CLOSED with `merged_at` set. */
	is_merged: z.boolean(),
	created_at: z.string().optional(),
	closed_at: z.string().optional(),
	merged_at: z.string().optional(),
	additions: z.int(),
	deletions: z.int(),
	changed_files: z.int(),
	author: z.string().optional(),
	author_id: z.int().optional(),
	/** The provider classified the author as automation. */
	author_bot: z.literal(true).optional(),
	merged_by: z.string().optional(),
	labels: z.array(z.string()),
	assignees: z.array(z.string()),
	milestone: z.string().optional(),
	/** Only the GraphQL sync fills it. */
	merge_state_status: z.string().optional(),
	/** Only the GraphQL sync fills it. */
	review_decision: z.string().optional(),
	/** What the checks or the pipeline said about the reviewed head. */
	head_checks: z.enum(["SUCCESS", "FAILURE", "PENDING", "CANCELLED", "NONE"]).optional(),
});

/** `metadata.json` of an issue: `IssueContentSource`. */
const issueRecordSchema = z.object({
	issue_number: z.int(),
	title: z.string().nullable(),
	body: z.string(),
	/** UNKNOWN when the mirror holds no state. */
	state: z.enum(["OPEN", "CLOSED", "UNKNOWN"]),
	state_reason: z.string().nullable(),
	html_url: z.string().nullable(),
	repository_full_name: z.string(),
	author: z.string().nullable(),
	author_id: z.int().nullable(),
	issue_type: z.string().nullable(),
	is_locked: z.boolean(),
	sub_issues_total: z.int().nullable(),
	sub_issues_completed: z.int().nullable(),
	/** Where the sub-issue counts come from. Absent when they are not known. */
	sub_issues_source: z.enum(["provider", "children"]).optional(),
	milestone: z.string().nullable(),
	closed_at: z.string().nullable(),
	labels: z.array(z.string()),
	assignees: z.array(z.string()),
});

/** `conversation_thread.json`: `ConversationThreadProjection.buildThreadPayload`. Its `_meta` notice is for a model. */
const conversationRecordSchema = z.object({
	channel: z.string(),
	threadTs: z.string(),
	messages: z.array(
		z.object({
			ts: z.string(),
			author: z.string().nullable(),
			authorLogin: z.string().optional(),
			authorName: z.string().optional(),
			text: z.string().nullable(),
			/** Whether the member under review wrote the message. */
			underReview: z.boolean().optional(),
			edited: z.literal(true).optional(),
		}),
	),
	messageCount: z.int(),
	truncated: z.boolean(),
});

/** `document.json`: `DocumentContentSource`. The body is `document.md` in the same folder. */
const documentRecordSchema = z.object({
	title: z.string().nullable(),
	collection: z.string().nullable(),
	collectionSlug: z.string().nullable(),
	slug: z.string().nullable(),
	createdBy: z.string().nullable(),
	updatedBy: z.string().nullable(),
	createdAt: z.string().nullable(),
	updatedAt: z.string().nullable(),
	archived: z.boolean(),
});

/** Each kind's record: its file in the context folder, and its schema. */
export const WORK_RECORDS = {
	"scm.pull_request": { file: "metadata.json", schema: pullRequestRecordSchema },
	"scm.issue": { file: "metadata.json", schema: issueRecordSchema },
	"chat.conversation_thread": {
		file: "conversation_thread.json",
		schema: conversationRecordSchema,
	},
	"docs.document": { file: "document.json", schema: documentRecordSchema },
} as const;

const previousCitationSchema = z.strictObject({
	sourceKind: z.string().min(1),
	/** A repository path for a citation of the change or the checkout, else the cited record. */
	path: z.string().min(1),
	startLine: z.int().positive(),
	endLine: z.int().positive(),
	side: z.enum(["OLD", "NEW"]).optional(),
});

/**
 * The inputs of a practice's script for work of one kind: its moments, the dimensions of its review
 * state, as recorded at admission, and its record.
 */
function inputsOf<
	K extends Kind,
	M extends z.ZodType<Moment<K>>,
	S extends z.ZodType,
	W extends z.ZodType,
>(kind: K, signal: M, state: S, work: W) {
	const origin = z.enum(ORIGINS);
	const admittedAt = z.iso.datetime();
	return z.strictObject({
		kind: z.literal(kind),
		subject: z.strictObject({ role: z.enum(SUBJECT_ROLES) }),
		cause: z.discriminatedUnion("type", [
			z.strictObject({
				type: z.literal("moment"),
				signal,
				origin,
				state,
				admittedAt,
				/** Only on `scm.pull_request.reviewed`: the submitted review that the practice is about. */
				review: z.strictObject({ id: z.int().positive(), reviewer: z.string().min(1) }).optional(),
			}),
			z.strictObject({ type: z.literal("recheck"), signal, origin, state, admittedAt }),
			z.strictObject({ type: z.literal("requested"), origin, state, admittedAt }),
		]),
		/**
		 * The latest current claim of this practice about the same person on the same work, or null. It
		 * holds no model text and no delivery state.
		 */
		previous: z
			.strictObject({
				reviewedAt: z.iso.datetime(),
				cause: z.enum(CAUSE_TYPES),
				signal: signal.optional(),
				outcome: z.enum(OUTCOMES),
				severity: z.enum(SEVERITIES).optional(),
				citations: z.array(previousCitationSchema),
				/** Pull request only: the head that the previous review read. */
				head: z.string().regex(COMMIT).optional(),
			})
			.nullable(),
		work,
	});
}

/**
 * A practice's `<slug>.cause.json` (`kind`, `subject`, `cause`), with its `<slug>.previous.json`
 * as `previous` and the kind's record as `work`.
 */
export const practiceInputsSchema = z.discriminatedUnion("kind", [
	inputsOf(
		"scm.pull_request",
		z.enum(MOMENTS["scm.pull_request"]),
		z.strictObject({
			draftStatus: z.enum(REVIEW_STATES["scm.pull_request"].draftStatus).optional(),
			state: z.enum(REVIEW_STATES["scm.pull_request"].state).optional(),
		}),
		pullRequestRecordSchema,
	),
	inputsOf(
		"scm.issue",
		z.enum(MOMENTS["scm.issue"]),
		z.strictObject({ state: z.enum(REVIEW_STATES["scm.issue"].state).optional() }),
		issueRecordSchema,
	),
	inputsOf(
		"chat.conversation_thread",
		z.enum(MOMENTS["chat.conversation_thread"]),
		z.strictObject({}),
		conversationRecordSchema,
	),
	inputsOf(
		"docs.document",
		z.enum(MOMENTS["docs.document"]),
		z.strictObject({ state: z.enum(REVIEW_STATES["docs.document"].state).optional() }),
		documentRecordSchema,
	),
]);
export type PracticeInputs = z.infer<typeof practiceInputsSchema>;
type InputsOf<K extends Kind> = Extract<PracticeInputs, { kind: K }>;

/** The facts of the work that review conditions can name, as the review recorded them. */
export type ReviewState<K extends Kind = Kind> = InputsOf<K>["cause"]["state"];
/**
 * Why this practice is in this review. The practice's moments and review conditions are its review
 * occasion; the cause says which of them brought it in, or a recheck, or a request by hand.
 * "moment": the practice is bound to the moment of the review, and its review conditions matched. "recheck": its current claim about the author is not met and the
 * work changed since. "requested": a person or a campaign asked for each practice of the kind,
 * whatever the state of the work.
 */
export type Cause<K extends Kind = Kind> = InputsOf<K>["cause"];
export type StagedPrevious<K extends Kind = Kind> = NonNullable<InputsOf<K>["previous"]>;
export type PreviousCitation = z.infer<typeof previousCitationSchema>;
export type SubjectRole = PracticeInputs["subject"]["role"];
export type CauseType = PracticeInputs["cause"]["type"];
/** The record of the reviewed work of each kind. */
export type Work = { [K in Kind]: InputsOf<K>["work"] };
