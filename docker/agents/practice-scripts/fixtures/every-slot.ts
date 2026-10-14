import {
	scm,
	addedComments,
	commentKinds,
	expectKind,
	requireChange,
	type AddedComment,
	type DiffFile,
} from "../kits/scm/index.ts";
import {
	agent,
	classify,
	cosineSimilarity,
	definePracticeScript,
	embedMany,
	generateText,
	Output,
	rerank,
	step,
	z,
	type Facts,
	type Omission,
	type PracticeScriptContext,
	type ReviewLead,
} from "../lib/core/practice-script.ts";

export const LABELS = {
	untrackedTodo: "A TODO without a tracking reference.",
	commentKind: "An added comment that a model rated as one that may need a second look.",
	declaration: "Where an agent found the declaration that the change relies on.",
	mostUnfinished: "The added comment that a reranker ranked closest to unfinished work.",
} as const;

const NEEDS_A_LOOK = new Set([
	"restates-code",
	"commented-out-code",
	"narrates-change",
	"temporary-stub",
	"misleading",
]);

const OPEN_WORK = /\b(?:TODO|FIXME|HACK|XXX)\b/u;
const TRACKING = /#\d+|\b[A-Z][A-Z0-9]+-\d+\b|https?:\/\/\S+/u;

/** How many pairs of vectors are nearly the same direction. */
function similarPairs(vectors: readonly number[][]): number {
	let similar = 0;
	for (const [i, a] of vectors.entries()) {
		for (const b of vectors.slice(i + 1)) {
			if (cosineSimilarity(a, b) > 0.9) {
				similar += 1;
			}
		}
	}
	return similar;
}

const meta = {
	models: {
		chat: "optional",
		decision: "optional",
		embedding: "optional",
		reranking: "optional",
	},
} as const;

type Context = PracticeScriptContext<typeof meta.models>;

interface Preparation {
	leads?: ReviewLead[];
	unrated?: Omission[];
	facts?: Facts;
}

async function commentKindLeads(ctx: Context, unmarked: AddedComment[]): Promise<Preparation> {
	const { ratings, unrated } = await classify({
		model: ctx.models.decision,
		set: commentKinds,
		items: unmarked,
		what: "added comments without a TODO marker",
	});
	const leads: ReviewLead[] = [];
	for (const [comment, rating] of ratings) {
		if (NEEDS_A_LOOK.has(rating.value)) {
			leads.push({
				at: comment.at,
				label: LABELS.commentKind,
				facts: { kind: rating.value, score: rating.score },
			});
		}
	}
	return { leads, unrated };
}

async function mostUnfinished(ctx: Context, comments: AddedComment[]): Promise<Preparation> {
	const { reranking } = ctx.models;
	if (reranking === undefined || comments.length === 0) {
		return {};
	}
	const ranked = await step("rerank", async () =>
		rerank({
			model: reranking,
			query: "temporary or unfinished work that should not be merged",
			documents: comments.map((c) => c.text),
			topN: 1,
		}),
	);
	if ("unrated" in ranked) {
		return {
			unrated: [{ what: "rerank of the added comments", count: 1, reason: ranked.unrated }],
		};
	}
	const top = ranked.value.ranking[0];
	const comment = top === undefined ? undefined : comments[top.originalIndex];
	return comment === undefined ? {} : { leads: [{ at: comment.at, label: LABELS.mostUnfinished }] };
}

async function similarComments(ctx: Context, comments: AddedComment[]): Promise<Preparation> {
	const { embedding } = ctx.models;
	if (embedding === undefined || comments.length < 2) {
		return {};
	}
	const embedded = await step("embed", async () =>
		embedMany({ model: embedding, values: comments.map((c) => c.text) }),
	);
	if ("unrated" in embedded) {
		return {
			unrated: [
				{
					what: "embedding of the added comments",
					count: comments.length,
					reason: embedded.unrated,
				},
			],
		};
	}
	const vectors = embedded.value.embeddings;
	return {
		facts: {
			embeddingDimensions: vectors[0]?.length ?? 0,
			similarCommentPairs: similarPairs(vectors),
		},
	};
}

async function changeKind(
	ctx: Context,
	change: ReadonlyMap<string, DiffFile>,
): Promise<Preparation> {
	const { chat } = ctx.models;
	if (chat === undefined) {
		return {};
	}
	// `output` throws when the answer has none, so it is read inside the step.
	const kind = await step("change kind", async () => {
		const { output } = await generateText({
			model: chat,
			output: Output.choice({ options: ["code", "documentation", "configuration", "mixed"] }),
			prompt: `Which kind of change adds these files: ${[...change.keys()].join(", ")}?`,
		});
		return output;
	});
	return "value" in kind
		? { facts: { changeKind: kind.value } }
		: { unrated: [{ what: "kind of the change", count: 1, reason: kind.unrated }] };
}

async function declaration(ctx: Context): Promise<Preparation> {
	const { chat } = ctx.models;
	if (chat === undefined) {
		return {};
	}
	const found = await step("locate", async () =>
		agent({
			model: chat,
			tools: ctx.evidence.tools,
			instructions: "You locate code in a repository. Use the tools to look; do not guess.",
			prompt:
				"Find where the function parseDiff is declared. Submit the file path relative to the repository root and the 1-based line of the declaration.",
			schema: z.object({ file: z.string(), line: z.int().positive() }),
			maxSteps: 8,
			abortSignal: ctx.abortSignal,
		}),
	);
	if ("unrated" in found) {
		return {
			unrated: [{ what: "agent search for the declaration", count: 1, reason: found.unrated }],
		};
	}
	const { output, steps } = found.value;
	return {
		leads: [
			{
				at: { file: output.file, line: output.line },
				label: LABELS.declaration,
				facts: { agentSteps: steps },
			},
		],
	};
}

export default definePracticeScript({
	meta,
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const change = requireChange(domain.files);
		const comments = addedComments(change);
		const untrackedTodos: ReviewLead[] = [];
		const unmarked: AddedComment[] = [];
		for (const comment of comments) {
			if (!OPEN_WORK.test(comment.text)) {
				unmarked.push(comment);
			} else if (!TRACKING.test(comment.text)) {
				untrackedTodos.push({ at: comment.at, label: LABELS.untrackedTodo });
			}
		}
		const parts = await Promise.all([
			commentKindLeads(ctx, unmarked),
			mostUnfinished(ctx, comments),
			similarComments(ctx, comments),
			changeKind(ctx, change),
			declaration(ctx),
		]);
		const facts: Facts = { comments: comments.length };
		for (const part of parts) {
			Object.assign(facts, part.facts);
		}
		return ctx.review({
			leads: [...untrackedTodos, ...parts.flatMap((p) => p.leads ?? [])],
			facts,
			limitations: parts.flatMap((p) => p.unrated ?? []),
			directions: ["Check each lead against the criterion before you cite it."],
		});
	},
});
