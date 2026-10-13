/** The contract of a practice program, as the host parses it, and of the result it returns. */
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

/**
 * The error of a script whose default export is not a `definePracticeScript` definition, such as a
 * plain function: the form that scripts had before `definePracticeScript`.
 */
export const RETIRED_FORM_ERROR =
	"This script uses the retired positional form. Rewrite it with definePracticeScript.";

/**
 * The error of a script written for the retired leads contract: its `meta` names a version, lead kinds
 * or a token ceiling, or its `run` returns leads. Nothing reads that form any more.
 */
export const LEGACY_CONTRACT_ERROR =
	"This practice script uses the retired leads contract (meta.version). Accept the practice's catalog update, or rewrite run() to return a result, for example return ctx.review({ leads }).";

/** The keys of the retired `meta`, in the order the error names the first one present. */
const LEGACY_META_KEYS = ["version", "kinds", "tokens"] as const;

/** {@link LEGACY_CONTRACT_ERROR}, naming the retired key that the script used. */
export function legacyContractError(key: (typeof LEGACY_META_KEYS)[number]): string {
	return LEGACY_CONTRACT_ERROR.replace("(meta.version)", `(meta.${key})`);
}

/** A practice slug as the server writes it: `SandboxLayout.PRACTICE_SLUG`, anchored. */
export const PRACTICE_SLUG = /^[a-z0-9][a-z0-9-]{0,63}$/u;

/**
 * The header that names the practice of every direct model call of a practice program, so the proxy
 * can attribute its usage.
 */
export const PRACTICE_SCRIPT_PRACTICE_HEADER = "x-hephaestus-practice";

/** What a practice program may ask a model to do directly. One slot, one kind of model. */
export const MODEL_SLOTS = ["chat", "decision", "embedding", "reranking"] as const;
export type ModelSlot = (typeof MODEL_SLOTS)[number];
export const modelSlotSchema = z.enum(MODEL_SLOTS);

/**
 * The most items that one call to a slot's model may hold: decision questions, embedding values and
 * rerank documents. The proxy refuses a larger call on the slot's own API (`LlmProxyService`), so the
 * host refuses it first, as "too-large", whatever the protocol of the slot's model.
 */
export const MODEL_SLOT_CAPS = {
	decision: 64,
	embedding: 64,
	reranking: 256,
} as const;

/**
 * "required": without a bound model the program does not run, and the practice is skipped.
 * "optional": the program runs without it and says what it could not rate.
 */
const needSchema = z.enum(["required", "optional"]);
export type Need = z.infer<typeof needSchema>;

/** Why something was not rated or not reached. Never "nothing found". */
export const REASONS = [
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

/** One line of text: a newline would let a program write its own headings into the reviewer's turn. */
const line = (max: number) =>
	z
		.string()
		.min(1)
		.max(max)
		.refine((text) => !/[\r\n]/u.test(text), "must be one line");

/**
 * A practice program's `meta`: the direct models it needs, and nothing else. A key of the retired
 * leads contract is refused with {@link LEGACY_CONTRACT_ERROR} before this schema reads it.
 */
export const metaSchema = z.strictObject({
	models: z.partialRecord(modelSlotSchema, needSchema).optional(),
});
export type ParsedMeta = z.infer<typeof metaSchema>;

/** The retired key that a `meta` value carries, or undefined. */
export function legacyMetaKey(meta: unknown): (typeof LEGACY_META_KEYS)[number] | undefined {
	return isJsonObject(meta) ? LEGACY_META_KEYS.find((key) => Object.hasOwn(meta, key)) : undefined;
}

/** Parse a program's `meta`, or say why it is not one: a retired key, or a shape the contract does not name. */
export function parseMeta(
	meta: unknown,
): { meta: ParsedMeta } | { error: string; legacy: boolean } {
	const legacy = legacyMetaKey(meta);
	if (legacy !== undefined) {
		return { error: legacyContractError(legacy), legacy: true };
	}
	const parsed = metaSchema.safeParse(meta);
	if (!parsed.success) {
		const issue = parsed.error.issues[0];
		return {
			error: `meta: ${issue ? `${issue.path.join(".")} ${issue.message}`.trim() : "invalid"}`,
			legacy: false,
		};
	}
	return { meta: parsed.data };
}

const scalar = z.union([
	line(200),
	z.number().refine(Number.isFinite, "must be finite"),
	z.boolean(),
]);
/** Scalar facts that a program computed: at most 32, each a short one-line text, a number or a flag. */
export const factsSchema = z
	.record(z.string().regex(/^[A-Za-z][A-Za-z0-9]{0,63}$/u), scalar)
	.refine((facts) => Object.keys(facts).length <= 32, "at most 32 facts");
export type Facts = z.infer<typeof factsSchema>;

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

/** The provider model of each slot: in the host, in a program's process, and in `ctx.models`. */
export type SlotModels = { [S in ModelSlot]: SlotTypes[S]["model"] };

/** What one slot's model returned, named by its slot. */
export type SlotAnswer<S extends ModelSlot = ModelSlot> = {
	[K in S]: { slot: K; result: SlotTypes[K]["result"] };
}[S];

type CallOptions<S extends ModelSlot> = SlotTypes[S]["options"] & Record<string, unknown>;

export interface SlotCall<S extends ModelSlot> {
	/** The options a program may set. Provider options and headers stay with the host. */
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
	counts.every((n) => n !== undefined && Number.isSafeInteger(n) && n >= 0)
		? counts.reduce<number>((s, n) => s + (n ?? 0), 0)
		: undefined;

const tokenCount = z.int().nonnegative().max(Number.MAX_SAFE_INTEGER);
const chatUsageReceipt = z.union([
	z.object({ prompt_tokens: tokenCount, completion_tokens: tokenCount }),
	z.object({ input_tokens: tokenCount, output_tokens: tokenCount }),
]);
const languageUsageReceipt = z
	.object({
		inputTokens: z.object({
			total: tokenCount,
			noCache: tokenCount.optional(),
			cacheRead: tokenCount.optional(),
			cacheWrite: tokenCount.optional(),
		}),
		outputTokens: z.object({
			total: tokenCount,
			text: tokenCount.optional(),
			reasoning: tokenCount.optional(),
		}),
	})
	.refine(
		({ inputTokens, outputTokens }) =>
			(inputTokens.cacheRead ?? 0) + (inputTokens.cacheWrite ?? 0) <= inputTokens.total &&
			(outputTokens.reasoning ?? 0) <= outputTokens.total,
	);

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

/** How the host calls each slot's model for a program. */
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
				// Compatible providers can normalize absent counts to zero; only their raw receipt proves presence.
				tokens:
					!languageUsageReceipt.safeParse(result.usage).success ||
					(result.usage.raw !== undefined && !chatUsageReceipt.safeParse(result.usage.raw).success)
						? undefined
						: sum(inputTokens.total, outputTokens.total),
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
 * What a program reads about its review: the kind of the work, its record, the cause, the role the
 * practice is about, its revision and scope, and the previous claim. The server stages them; the host
 * validates them here.
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

/** `ResultOrigin`: the population that the review's results belong to. */
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
 * context folder. A field that the server does not write is not in the type: the host drops an
 * unknown field, and a known field of another shape fails the record. Only a descriptive field with a
 * defined meaning for "not known" falls back to it (`notKnownWhenUnnamed`), so that a value that a
 * provider adds later does not fail the review's script.
 */

/**
 * A descriptive field whose absence means that it is not known. A value that the contract does not
 * name is not known either: it reads as absent, and the record stays valid.
 */
function notKnownWhenUnnamed<const T extends readonly [string, ...string[]]>(values: T) {
	return z.union([z.enum(values).optional(), z.unknown().transform(() => undefined)]);
}

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
	/** The admitted repository's id: a full name is unique only within one provider. */
	repository_id: z.int().positive().optional(),
	/** That repository's provider, as the mirror held it at capture. Absent when it could not be told. */
	provider_id: z.int().positive().optional(),
	source_branch: z.string(),
	target_branch: z.string(),
	/** The reviewed head. */
	commit_sha: z.string().regex(COMMIT),
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
	/** What the checks or the pipeline said about the reviewed head: `CheckState`. */
	head_checks: notKnownWhenUnnamed([
		"SUCCESS",
		"FAILURE",
		"PENDING",
		"CANCELLED",
		"NONE",
		"NO_PIPELINE",
		"SKIPPED",
	]),
});

/** `metadata.json` of an issue: `IssueContentSource`. */
const issueRecordSchema = z.object({
	issue_number: z.int(),
	title: z.string().nullable(),
	body: z.string(),
	/** UNKNOWN when the mirror holds no state, or a state that the contract does not name. */
	state: z.union([
		z.enum(["OPEN", "CLOSED", "UNKNOWN"]),
		z.unknown().transform(() => "UNKNOWN" as const),
	]),
	state_reason: z.string().nullable(),
	html_url: z.string().nullable(),
	repository_full_name: z.string(),
	/** The admitted repository's id: a full name is unique only within one provider. */
	repository_id: z.int().positive().optional(),
	/** That repository's provider, as the mirror held it at capture. Absent when it could not be told. */
	provider_id: z.int().positive().optional(),
	author: z.string().nullable(),
	author_id: z.int().nullable(),
	issue_type: z.string().nullable(),
	is_locked: z.boolean(),
	sub_issues_total: z.int().nullable(),
	sub_issues_completed: z.int().nullable(),
	/** Where the sub-issue counts come from. Absent when it is not known. */
	sub_issues_source: notKnownWhenUnnamed(["provider", "children"]),
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
		/** The admitted revision of the practice: its criteria and its program. */
		practice: z.strictObject({ revisionId: z.int().positive() }),
		/** The authored scope that the result is about. The key is opaque. */
		scope: z.strictObject({
			key: z.string().min(1).max(255),
			mode: z.literal("change"),
			description: z.string(),
		}),
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
				/** The admitted result that this previous claim is. */
				practiceResultId: z.string().min(1),
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
 * A practice's `<slug>.cause.json` (`kind`, `subject`, `practice`, `scope`, `cause`), with its
 * `<slug>.previous.json` as `previous` and the kind's record as `work`.
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

/*
 * The result a practice program returns: one candidate per practice. Admission on the server parses
 * it again with the same rules and the same refusal codes (`CandidateParser`), and checks every quote
 * against the captured sources; `CandidateContractSyncTest` keeps the bounds equal.
 */

/** The bounds of a candidate. Counts are integers from 0 to {@link CANDIDATE_LIMITS.maxCount}. */
export const CANDIDATE_LIMITS = {
	summaryChars: 160,
	explanationChars: 10_000,
	problemExplanationChars: 4000,
	nextStepChars: 1000,
	exampleNoteChars: 280,
	quoteChars: 4000,
	openQuestionChars: 500,
	warrantFieldChars: 500,
	resultCitations: 50,
	citationsPerProblem: 20,
	problems: 50,
	openQuestions: 20,
	omissions: 20,
	omissionUnits: 50,
	consultedSources: 20,
	candidateBytes: 131_072,
	maxCount: 1_000_000,
} as const;

/** Why a candidate is refused, in the order the rules apply: the first failing rule names it. */
export const CANDIDATE_REFUSALS = [
	"malformed_candidate",
	"outcome_problem_mismatch",
	"severity_outcome_mismatch",
	"severity_below_worst_problem",
	"warrant_outcome_mismatch",
	"result_citations_required",
	"met_coverage_incomplete",
	"coverage_inconsistent",
	"example_outcome_mismatch",
	"example_overlaps_problem",
	"basis_citation_out_of_range",
	"duplicate_problem_key",
] as const;
export type CandidateRefusal = (typeof CANDIDATE_REFUSALS)[number];

export type NonEmpty<T> = readonly [T, ...T[]];
export type Severity = (typeof SEVERITIES)[number];
export type Outcome = (typeof OUTCOMES)[number];

/** The source kinds whose citations carry a side or a revision. */
export const DIFF_SOURCE = "scm.pull-request.diff";
export const TREE_SOURCE = "scm.repository.tree";

/**
 * A verified place in a captured source, in canonical coordinates. The quote is the exact text at
 * those lines; admission reads it again from the source.
 */
export interface Citation {
	/** Integration-neutral, e.g. "scm.pull-request.diff", "scm.repository.tree", "scm.pull-request.core". */
	sourceKind: string;
	/** The staged artifact, e.g. "context/change.json", "repos/reviewed/.git/HEAD", "context/metadata.json". */
	artifactPath: string;
	/** The repository path for code sources; the record path otherwise. */
	path: string;
	/** Exactly on scm.pull-request.diff. */
	side?: "OLD" | "NEW";
	/** A full commit SHA; only on scm.repository.tree and scm.pull-request.diff. */
	revision?: string;
	startLine: number;
	endLine: number;
	quote: string;
}

export interface CodeCitationIdentity {
	repository: string;
	revision: string;
}

type ResolveCodeCitation = (citation: Citation) => CodeCitationIdentity | undefined;
/** What a search for something missing consulted, looked for, and did not cover. Consulted names source kinds. */
export interface SearchWarrant {
	consulted: NonEmpty<string>;
	lookedFor: string;
	boundary: string;
}
export interface InapplicabilityWarrant {
	consulted: NonEmpty<string>;
	subject: string;
	ruledOutBy: string;
}
/** A real semantic question the evidence leaves open, located where it can be. */
export interface OpenQuestion {
	openQuestion: string;
	wouldSettleIt: string;
	citation?: Citation;
}
/** One place of the work that shows the practice done well. */
export interface Example {
	citation: Citation;
	note: string;
}
/** Why a problem is this work's: the indices name the problem's own citations. */
export type ClaimBasis =
	| { type: "introduced"; head: number; explanation: string }
	| { type: "worsened"; base: number; head: number; explanation: string }
	| { type: "duty"; criterionQuote: string; event: number; explanation: string }
	| { type: "state"; explanation: string };
export interface Problem {
	/** Practice-slug shaped and unique within the candidate. */
	key?: string;
	severity: Severity;
	/** One line. */
	summary: string;
	explanation: string;
	citations: NonEmpty<Citation>;
	basis: ClaimBasis;
	nextStep?: string;
}
export const OMISSION_REASONS = ["selection", "truncated", ...REASONS, "cancelled"] as const;
export type OmissionReason = (typeof OMISSION_REASONS)[number];
/** What a program or a review did not cover, and why. */
export interface Omission {
	what: string;
	count: number;
	reason: OmissionReason;
	units?: readonly string[];
}
type Completeness =
	| { complete: true; omitted?: never }
	| { complete: false; omitted: NonEmpty<Omission> };
/**
 * What the result covers. "host": the host derived it from its own read receipts. "claimed": program
 * code typed it, and it stays a claim.
 */
export type Coverage =
	| ({ source: "host"; reads: number; truncatedReads: number; failedReads: number } & Completeness)
	| ({ source: "claimed"; selected: number; checked: number; total: number | null } & Completeness);

interface ResultBase {
	summary: string;
	explanation: string;
	coverage: Coverage;
}
export type Candidate = ResultBase &
	(
		| {
				outcome: "MET";
				citations: NonEmpty<Citation>;
				coverage: { complete: true };
				example?: Example;
				evidence: { search?: SearchWarrant };
		  }
		| {
				outcome: "NOT_MET";
				citations: readonly Citation[];
				problems: NonEmpty<Problem>;
				severity?: Severity;
				example?: Example;
				evidence: { search?: SearchWarrant; undecidability?: NonEmpty<OpenQuestion> };
		  }
		| {
				outcome: "NOT_APPLICABLE";
				citations: NonEmpty<Citation>;
				evidence: { inapplicability: InapplicabilityWarrant };
		  }
		| {
				outcome: "UNDETERMINED";
				citations: NonEmpty<Citation>;
				evidence: { search?: SearchWarrant; undecidability: NonEmpty<OpenQuestion> };
		  }
	);
type DistributiveOmit<T, K extends PropertyKey> = T extends unknown ? Omit<T, K> : never;
/** What a review session submits: a candidate without numbers. The host adds the coverage it measured. */
export type SessionCandidate = DistributiveOmit<Candidate, "coverage">;

const count = z.int().min(0).max(CANDIDATE_LIMITS.maxCount);
const lineNumber = z.int().min(1).max(2_147_483_647);
const bounded = (max: number) => z.string().min(1).max(max);
const listOf = <T extends z.ZodType>(item: T, max: number) => z.array(item).max(max);
const nonEmptyListOf = <T extends z.ZodType>(item: T, max: number) =>
	z
		.array(item)
		.min(1)
		.max(max)
		.transform((items, ctx) => {
			const listed = nonEmpty(items);
			if (listed === undefined) {
				ctx.addIssue({ code: "custom", message: "must list at least one" });
				return z.NEVER;
			}
			return listed;
		});

/**
 * A file of a repository as a quote of code names it: relative, with no empty, `.` or `..` part, and outside the
 * repository's `.git` directory, which holds no reviewed code.
 */
export function isRepositoryPath(path: string): boolean {
	return (
		!path.includes("\\") &&
		path.split("/").every((part) => part !== "" && part !== "." && part !== ".." && part !== ".git")
	);
}

const citationShape = z
	.strictObject({
		sourceKind: z.string().min(1),
		artifactPath: z.string().min(1),
		path: z.string().min(1),
		side: z.enum(["OLD", "NEW"]).optional(),
		revision: z.string().regex(COMMIT).optional(),
		startLine: lineNumber,
		endLine: lineNumber,
		quote: bounded(CANDIDATE_LIMITS.quoteChars),
	})
	.refine(
		(citation) => citation.endLine >= citation.startLine,
		"endLine must be at least startLine",
	)
	.refine(
		(citation) => (citation.side !== undefined) === (citation.sourceKind === DIFF_SOURCE),
		`side is set exactly on a ${DIFF_SOURCE} citation`,
	)
	.refine(
		(citation) =>
			citation.revision === undefined ||
			citation.sourceKind === DIFF_SOURCE ||
			citation.sourceKind === TREE_SOURCE,
		`revision is set only on a ${TREE_SOURCE} or ${DIFF_SOURCE} citation`,
	)
	.refine(
		(citation) =>
			(citation.sourceKind !== DIFF_SOURCE && citation.sourceKind !== TREE_SOURCE) ||
			isRepositoryPath(citation.path),
		"a quote of code names a file of the repository: a relative path outside .git, with no empty, . or .. part",
	);
const consulted = nonEmptyListOf(
	bounded(CANDIDATE_LIMITS.warrantFieldChars),
	CANDIDATE_LIMITS.consultedSources,
);
const warrantField = bounded(CANDIDATE_LIMITS.warrantFieldChars);
const searchShape = z.strictObject({ consulted, lookedFor: warrantField, boundary: warrantField });
const inapplicabilityShape = z.strictObject({
	consulted,
	subject: warrantField,
	ruledOutBy: warrantField,
});
const openQuestionShape = z.strictObject({
	openQuestion: bounded(CANDIDATE_LIMITS.openQuestionChars),
	wouldSettleIt: bounded(CANDIDATE_LIMITS.openQuestionChars),
	citation: citationShape.optional(),
});
const exampleShape = z.strictObject({
	citation: citationShape,
	note: bounded(CANDIDATE_LIMITS.exampleNoteChars),
});
const basisExplanation = bounded(CANDIDATE_LIMITS.problemExplanationChars);
const basisShape = z.discriminatedUnion("type", [
	z.strictObject({ type: z.literal("introduced"), head: count, explanation: basisExplanation }),
	z.strictObject({
		type: z.literal("worsened"),
		base: count,
		head: count,
		explanation: basisExplanation,
	}),
	z.strictObject({
		type: z.literal("duty"),
		criterionQuote: bounded(CANDIDATE_LIMITS.quoteChars),
		event: count,
		explanation: basisExplanation,
	}),
	z.strictObject({ type: z.literal("state"), explanation: basisExplanation }),
]);
const severityShape = z.enum(SEVERITIES);
const problemShape = z.strictObject({
	key: z.string().regex(PRACTICE_SLUG).optional(),
	severity: severityShape,
	summary: line(CANDIDATE_LIMITS.summaryChars),
	explanation: bounded(CANDIDATE_LIMITS.problemExplanationChars),
	citations: nonEmptyListOf(citationShape, CANDIDATE_LIMITS.citationsPerProblem),
	basis: basisShape,
	nextStep: bounded(CANDIDATE_LIMITS.nextStepChars).optional(),
});
const omissionShape = z.strictObject({
	what: line(CANDIDATE_LIMITS.summaryChars),
	count,
	reason: z.enum(OMISSION_REASONS),
	units: listOf(bounded(CANDIDATE_LIMITS.warrantFieldChars), CANDIDATE_LIMITS.omissionUnits)
		.readonly()
		.optional(),
});
const omitted = listOf(omissionShape, CANDIDATE_LIMITS.omissions).optional();
const coverageShape = z.discriminatedUnion("source", [
	z.strictObject({
		source: z.literal("host"),
		reads: count,
		truncatedReads: count,
		failedReads: count,
		complete: z.boolean(),
		omitted,
	}),
	z.strictObject({
		source: z.literal("claimed"),
		selected: count,
		checked: count,
		total: count.nullable(),
		complete: z.boolean(),
		omitted,
	}),
]);

/**
 * Every key that any outcome may carry. A candidate is read against it first, so that a key in the
 * wrong outcome is named by its outcome rule, not as an unknown key.
 */
const supersetShape = z.strictObject({
	outcome: z.enum(OUTCOMES),
	summary: line(CANDIDATE_LIMITS.summaryChars),
	explanation: bounded(CANDIDATE_LIMITS.explanationChars),
	citations: listOf(citationShape, CANDIDATE_LIMITS.resultCitations),
	problems: listOf(problemShape, CANDIDATE_LIMITS.problems).optional(),
	severity: severityShape.optional(),
	example: exampleShape.optional(),
	evidence: z.strictObject({
		search: searchShape.optional(),
		inapplicability: inapplicabilityShape.optional(),
		undecidability: nonEmptyListOf(openQuestionShape, CANDIDATE_LIMITS.openQuestions).optional(),
	}),
	coverage: coverageShape,
});
type Superset = z.infer<typeof supersetShape>;

const SEVERITY_RANK: Readonly<Record<Severity, number>> = {
	CRITICAL: 3,
	MAJOR: 2,
	MINOR: 1,
	INFO: 0,
};

/** The most severe of the problems' severities. */
export function worstSeverity(problems: readonly { severity: Severity }[]): Severity | undefined {
	let worst: Severity | undefined;
	for (const { severity } of problems) {
		if (worst === undefined || SEVERITY_RANK[severity] > SEVERITY_RANK[worst]) {
			worst = severity;
		}
	}
	return worst;
}

function nonEmpty<T>(items: readonly T[]): NonEmpty<T> | undefined {
	const [first, ...rest] = items;
	return first === undefined ? undefined : [first, ...rest];
}

interface Refusal {
	ok: false;
	reasonCode: CandidateRefusal;
	message: string;
}
const refuse = (reasonCode: CandidateRefusal, message: string): Refusal => ({
	ok: false,
	reasonCode,
	message,
});

/** The coverage as typed, or why its counts and its completeness contradict each other. */
function readCoverage(coverage: Superset["coverage"]): Coverage | string {
	const omissions = nonEmpty(coverage.omitted ?? []);
	if (coverage.complete !== (omissions === undefined)) {
		return coverage.complete
			? "complete coverage lists no omission"
			: "incomplete coverage lists what it omitted";
	}
	if (coverage.source === "host") {
		const { reads, truncatedReads, failedReads } = coverage;
		if (coverage.complete !== (truncatedReads === 0 && failedReads === 0)) {
			return "host coverage is complete exactly when no read was truncated or failed";
		}
		return omissions === undefined
			? { source: "host", reads, truncatedReads, failedReads, complete: true }
			: { source: "host", reads, truncatedReads, failedReads, complete: false, omitted: omissions };
	}
	const { selected, checked, total } = coverage;
	if (checked > selected) {
		return "claimed coverage checked more units than it selected";
	}
	if (total !== null && selected > total) {
		return "claimed coverage selected more units than the total";
	}
	if (coverage.complete && (checked !== selected || (total !== null && selected !== total))) {
		return "complete claimed coverage checks every selected unit of the total";
	}
	return omissions === undefined
		? { source: "claimed", selected, checked, total, complete: true }
		: { source: "claimed", selected, checked, total, complete: false, omitted: omissions };
}

function overlaps(a: Citation, b: Citation, resolve?: ResolveCodeCitation): boolean {
	if (a.path !== b.path || a.startLine > b.endLine || b.startLine > a.endLine) {
		return false;
	}
	const code = (citation: Citation) =>
		citation.sourceKind === DIFF_SOURCE || citation.sourceKind === TREE_SOURCE;
	if (code(a) && code(b) && resolve !== undefined) {
		const left = resolve(a);
		const right = resolve(b);
		if (left !== undefined && right !== undefined) {
			return left.repository === right.repository && left.revision === right.revision;
		}
	}
	return (
		a.sourceKind === b.sourceKind &&
		a.artifactPath === b.artifactPath &&
		a.side === b.side &&
		a.revision === b.revision
	);
}

function basisIndices(basis: ClaimBasis): number[] {
	switch (basis.type) {
		case "introduced": {
			return [basis.head];
		}
		case "worsened": {
			return [basis.base, basis.head];
		}
		case "duty": {
			return [basis.event];
		}
		case "state": {
			return [];
		}
	}
}

function issueText(error: z.ZodError): string {
	const issue = error.issues[0];
	if (issue === undefined) {
		return "invalid";
	}
	const where = issue.path.map(String).join(".");
	return where === "" ? issue.message : `${where}: ${issue.message}`;
}

/** The JSON size of a value, or undefined when it is not JSON. */
function jsonBytes(value: unknown): number | undefined {
	try {
		const text: unknown = JSON.stringify(value);
		return typeof text === "string" ? Buffer.byteLength(text) : 0;
	} catch {
		return undefined;
	}
}

/** The warrants each outcome may carry, and what a refusal says about a wrong one. */
const WARRANT_RULES: Readonly<
	Record<Outcome, { holds: (evidence: Superset["evidence"]) => boolean; message: string }>
> = {
	MET: {
		holds: (e) => e.inapplicability === undefined && e.undecidability === undefined,
		message: "a MET result may carry a search warrant only",
	},
	NOT_MET: {
		holds: (e) => e.inapplicability === undefined,
		message: "a NOT_MET result may carry a search warrant and open questions only",
	},
	NOT_APPLICABLE: {
		holds: (e) =>
			e.inapplicability !== undefined && e.search === undefined && e.undecidability === undefined,
		message: "a NOT_APPLICABLE result carries its inapplicability warrant only",
	},
	UNDETERMINED: {
		holds: (e) => e.inapplicability === undefined && e.undecidability !== undefined,
		message: "an UNDETERMINED result carries its open questions, and a search warrant at most",
	},
};

/** The first outcome rule the candidate breaks, before its coverage is read. */
function outcomeRefusal(
	sent: Superset,
	problems: NonEmpty<NonNullable<Superset["problems"]>[number]> | undefined,
): Refusal | undefined {
	const { outcome } = sent;
	if (
		(outcome === "NOT_MET") !== (problems !== undefined) ||
		(sent.problems !== undefined && problems === undefined)
	) {
		return refuse(
			"outcome_problem_mismatch",
			outcome === "NOT_MET"
				? "a NOT_MET result lists its problems"
				: `a ${outcome} result lists no problem`,
		);
	}
	if (sent.severity !== undefined && outcome !== "NOT_MET") {
		return refuse("severity_outcome_mismatch", `a ${outcome} result has no severity`);
	}
	const worst = problems === undefined ? undefined : worstSeverity(problems);
	if (
		sent.severity !== undefined &&
		worst !== undefined &&
		SEVERITY_RANK[sent.severity] < SEVERITY_RANK[worst]
	) {
		return refuse(
			"severity_below_worst_problem",
			`the result severity ${sent.severity} is below its worst problem, ${worst}`,
		);
	}
	if (!WARRANT_RULES[outcome].holds(sent.evidence)) {
		return refuse("warrant_outcome_mismatch", WARRANT_RULES[outcome].message);
	}
	if (outcome !== "NOT_MET" && sent.citations.length === 0) {
		return refuse("result_citations_required", `a ${outcome} result cites what it rests on`);
	}
	if (outcome === "MET" && !sent.coverage.complete) {
		return refuse("met_coverage_incomplete", "a MET result needs complete coverage");
	}
	return undefined;
}

/** The first rule about the example and the problems that the candidate breaks. */
function problemRefusal(sent: Superset, resolve?: ResolveCodeCitation): Refusal | undefined {
	const { example, outcome } = sent;
	const problems = sent.problems ?? [];
	if (example !== undefined && outcome !== "MET" && outcome !== "NOT_MET") {
		return refuse("example_outcome_mismatch", `a ${outcome} result has no example`);
	}
	if (
		example !== undefined &&
		problems.some((problem) =>
			problem.citations.some((cited) => overlaps(cited, example.citation, resolve)),
		)
	) {
		return refuse("example_overlaps_problem", "the example cites the place of a problem");
	}
	for (const [index, problem] of problems.entries()) {
		const out = basisIndices(problem.basis).find((at) => at >= problem.citations.length);
		if (out !== undefined) {
			return refuse(
				"basis_citation_out_of_range",
				`problem ${index}: its basis names citation ${out}, and it has ${problem.citations.length}`,
			);
		}
	}
	const keys = problems.flatMap((problem) => (problem.key === undefined ? [] : [problem.key]));
	return new Set(keys).size === keys.length
		? undefined
		: refuse("duplicate_problem_key", "two problems share a key");
}

/** The candidate of its outcome, once every rule holds; undefined when the outcome's parts are not there. */
function candidateOf(sent: Superset, coverage: Coverage): Candidate | undefined {
	const { outcome, summary, explanation, example } = sent;
	const { search, inapplicability, undecidability } = sent.evidence;
	const base = { summary, explanation };
	const citations = nonEmpty(sent.citations);
	const problems = sent.problems === undefined ? undefined : nonEmpty(sent.problems);
	const searched = search === undefined ? {} : { search };
	const exemplified = example === undefined ? {} : { example };
	if (outcome === "MET" && citations !== undefined && coverage.complete) {
		return { ...base, outcome, citations, coverage, ...exemplified, evidence: searched };
	}
	if (outcome === "NOT_MET" && problems !== undefined) {
		return {
			...base,
			outcome,
			citations: sent.citations,
			problems,
			...(sent.severity === undefined ? {} : { severity: sent.severity }),
			...exemplified,
			coverage,
			evidence: { ...searched, ...(undecidability === undefined ? {} : { undecidability }) },
		};
	}
	if (outcome === "NOT_APPLICABLE" && citations !== undefined && inapplicability !== undefined) {
		return { ...base, outcome, citations, coverage, evidence: { inapplicability } };
	}
	if (outcome === "UNDETERMINED" && citations !== undefined && undecidability !== undefined) {
		return { ...base, outcome, citations, coverage, evidence: { ...searched, undecidability } };
	}
	return undefined;
}

/**
 * Parse a candidate: its shape first, then the outcome rules in the order of
 * {@link CANDIDATE_REFUSALS}. A value of another shape, such as a leads return, is refused as it is;
 * nothing guesses what it meant.
 */
export function parseCandidate(
	value: unknown,
	resolve?: ResolveCodeCitation,
):
	| { ok: true; candidate: Candidate }
	| { ok: false; reasonCode: CandidateRefusal; message: string } {
	const bytes = jsonBytes(value);
	if (bytes === undefined) {
		return refuse("malformed_candidate", "the candidate is not JSON");
	}
	if (bytes > CANDIDATE_LIMITS.candidateBytes) {
		return refuse(
			"malformed_candidate",
			`the candidate has ${bytes} bytes; at most ${CANDIDATE_LIMITS.candidateBytes}`,
		);
	}
	const parsed = supersetShape.safeParse(value);
	if (!parsed.success) {
		return refuse("malformed_candidate", issueText(parsed.error));
	}
	const sent = parsed.data;
	const problems = sent.problems === undefined ? undefined : nonEmpty(sent.problems);
	const before = outcomeRefusal(sent, problems);
	if (before !== undefined) {
		return before;
	}
	const coverage = readCoverage(sent.coverage);
	if (typeof coverage === "string") {
		return refuse("coverage_inconsistent", coverage);
	}
	const after = problemRefusal(sent, resolve);
	if (after !== undefined) {
		return after;
	}
	const candidate = candidateOf(sent, coverage);
	// Each outcome's parts are there once the rules hold; a value that reaches here broke none of them.
	return candidate === undefined
		? refuse("malformed_candidate", "the candidate matches no outcome")
		: { ok: true, candidate };
}

/*
 * What a program gives `ctx.review()`: prepared leads, scalar facts, authored directions, bounded
 * context and explicit limitations. It is input to the review session, never another result.
 */

/** Where a lead points: a line of the change, a repository file, or a captured record. The host quotes it. */
export const evidenceLocationSchema = z.union([
	z.strictObject({
		change: z.string().min(1),
		line: lineNumber,
		endLine: lineNumber.optional(),
		side: z.enum(["OLD", "NEW"]).optional(),
	}),
	z.strictObject({
		file: z.string().min(1),
		line: lineNumber,
		endLine: lineNumber.optional(),
		revision: z.string().regex(COMMIT).optional(),
	}),
	z.strictObject({
		record: z.string().min(1),
		line: z.int().nonnegative().optional(),
	}),
]);
export type EvidenceLocation = z.infer<typeof evidenceLocationSchema>;

export const MAX_REVIEW_OPTIONS_BYTES = 262_144;

const reviewLeadSchema = z.strictObject({
	at: evidenceLocationSchema,
	/** What the lead is, for the reviewer: one line. */
	label: line(300),
	facts: factsSchema.optional(),
});
export type ReviewLead = z.infer<typeof reviewLeadSchema>;

const preparedContextSchema = z.strictObject({
	label: line(160),
	value: z.json(),
	citations: listOf(citationShape, CANDIDATE_LIMITS.resultCitations).optional(),
});
export type PreparedContext = z.infer<typeof preparedContextSchema>;

export const REVIEW_LEAD_LIMIT = 200;

export const reviewOptionsSchema = z.strictObject({
	leads: listOf(reviewLeadSchema, REVIEW_LEAD_LIMIT).readonly().optional(),
	facts: factsSchema.optional(),
	/** Directions to the reviewer. The program's author writes them; reviewed text and model text belong in context. */
	directions: listOf(line(300), 10).readonly().optional(),
	context: listOf(preparedContextSchema, 20).readonly().optional(),
	limitations: listOf(omissionShape, CANDIDATE_LIMITS.omissions).readonly().optional(),
});
export type ReviewOptions = z.infer<typeof reviewOptionsSchema>;

/** Parse review options, or say why they are not. An invalid lead is an error, never dropped. */
export function parseReviewOptions(value: unknown): { options: ReviewOptions } | { error: string } {
	let bytes: number;
	try {
		bytes = Buffer.byteLength(JSON.stringify(value ?? {}));
	} catch {
		return { error: "review options are not JSON" };
	}
	if (bytes > MAX_REVIEW_OPTIONS_BYTES) {
		return { error: `review options have ${bytes} bytes; at most ${MAX_REVIEW_OPTIONS_BYTES}` };
	}
	const parsed = reviewOptionsSchema.safeParse(value ?? {});
	return parsed.success
		? { options: parsed.data }
		: { error: `review options: ${issueText(parsed.error)}` };
}

/*
 * The execution report: `out/practice-scripts.json`, one entry for each admitted practice. It records
 * how each program ran, never the candidate it returned.
 */

export const RUN_STATUSES = ["ok", "skipped", "failed", "not-reached"] as const;
export type RunStatus = (typeof RUN_STATUSES)[number];
export const STOP_REASONS = [
	...REASONS,
	"cancelled",
	"invalid-program",
	"invalid-candidate",
] as const;
export type StopReason = (typeof STOP_REASONS)[number];
export const PROGRAM_KINDS = ["builtin", "custom"] as const;
export type ProgramKind = (typeof PROGRAM_KINDS)[number];
export const EXECUTION_REPORT_SCHEMA_VERSION = 2;

const callsSchema = z.strictObject({
	review: count,
	chat: count,
	decision: count,
	embedding: count,
	reranking: count,
});
export type ProgramCalls = z.infer<typeof callsSchema>;
const coverageReachedSchema = z.strictObject({
	reads: count,
	truncatedReads: count,
	failedReads: count,
});

export const executionEntrySchema = z
	.strictObject({
		practiceSlug: z.string().regex(PRACTICE_SLUG),
		program: z.strictObject({
			kind: z.enum(PROGRAM_KINDS),
			digest: z
				.string()
				.regex(/^[0-9a-f]{64}$/u)
				.nullable(),
		}),
		models: z.array(
			z.strictObject({
				slot: modelSlotSchema,
				need: needSchema,
				bound: z.boolean(),
				notRated: z.partialRecord(reasonSchema, count),
			}),
		),
		status: z.enum(RUN_STATUSES),
		stopReason: z.enum(STOP_REASONS).nullable(),
		// 500 code points, as `errorLine` cuts it and the server reads it.
		error: z
			.string()
			.min(1)
			.refine((text) => (text.match(/./gsu) ?? []).length <= 500, "at most 500 code points")
			.optional(),
		calls: callsSchema,
		coverageReached: coverageReachedSchema,
		/** How long the program ran: up to the longest attempt, not a count. */
		durationMs: z.int().min(0).max(2_147_483_647).nullable(),
	})
	.refine(
		(entry) => (entry.status === "ok") === (entry.stopReason === null),
		"status ok exactly when there is no stop reason",
	)
	.refine(
		(entry) => entry.error === undefined || entry.status === "failed",
		"an error only on a failed practice",
	)
	.refine(
		(entry) => (entry.status === "not-reached") === (entry.durationMs === null),
		"no duration exactly for a practice not reached",
	)
	.refine(
		(entry) => (entry.program.kind === "builtin") === (entry.program.digest === null),
		"a digest exactly for a custom program",
	);
export type ExecutionEntry = z.infer<typeof executionEntrySchema>;

export const executionReportSchema = z.strictObject({
	schemaVersion: z.literal(EXECUTION_REPORT_SCHEMA_VERSION),
	truncated: z.boolean(),
	practices: z.array(executionEntrySchema),
});
export type ExecutionReport = z.infer<typeof executionReportSchema>;
