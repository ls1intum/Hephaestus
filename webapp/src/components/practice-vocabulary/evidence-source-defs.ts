import {
	CircleDotIcon,
	FileDiffIcon,
	FileQuestionIcon,
	FileTextIcon,
	FolderTreeIcon,
	GitPullRequestIcon,
	HistoryIcon,
	Layers2Icon,
	LibraryIcon,
	LinkIcon,
	MailCheckIcon,
	MessageCircleIcon,
	MessageSquareCodeIcon,
	MessageSquareIcon,
	MessageSquareTextIcon,
	MessagesSquareIcon,
} from "lucide-react";
import type { EvidenceCitation } from "@/api/types.gen";
import type { StatusDef } from "./status-def";

/**
 * Where a citation's line numbers point, which decides whether a surface may show them.
 *
 * <p>`code` — the range is a real span of a real file, and the server proved it: only diff
 * citations are checked against the annotated unified diff, and only diff and repository-tree
 * citations are treated as code anywhere downstream. A `path:12–18` locator is true here.
 *
 * <p>`object` — the range is an offset inside the serialised context artifact the quote was pulled
 * from: a line of `conversation_thread.json`, not a position in the Slack thread. The schema demands
 * a number ≥ 1 so one is always present, but nothing verifies it points at the quote. Rendering it
 * would be a precise-looking coordinate into a file the reader cannot open, so these citations show
 * their `path` as the label it is — the name of the comment, message or document — and no numbers.
 */
export type EvidenceLocator = "code" | "object";

export interface EvidenceSourceDef extends StatusDef {
	locator: EvidenceLocator;
}

/**
 * The registered inputs a review may quote from, in operator-facing words.
 *
 * <p>`sourceKind` is a `string` on the wire, but it is not free-form: the server admits a citation
 * only when its kind is in the shipped artifact-source catalog *and* was staged for that job, so the
 * registry below is every value that can be written today. It is keyed by the constant and
 * never shows it — `scm.pull-request.diff` is a contract id, and a screen that prints one has asked
 * its reader to learn the pipeline's filing system in order to read a quote.
 *
 * <p>Not a `StatusDefs` record, and not a total one over a union, because the wire type is `string`:
 * an unknown kind falls back rather than failing a lookup. {@link evidenceSourceDef} is the only way
 * in, so a surface cannot forget the fallback.
 */
const EVIDENCE_SOURCE_DEFS: Record<string, EvidenceSourceDef> = {
	"scm.pull-request.core": {
		label: "The pull request itself",
		icon: GitPullRequestIcon,
		badgeVariant: "outline",
		description: "Its title, description and state, as the author wrote them.",
		locator: "object",
	},
	"scm.pull-request.diff": {
		label: "The code changes",
		icon: FileDiffIcon,
		badgeVariant: "outline",
		description: "The diff under review, quoted at the lines it was read from.",
		locator: "code",
	},
	"scm.pull-request.comments": {
		label: "Comments on the pull request",
		icon: MessageSquareIcon,
		badgeVariant: "outline",
		description: "What people said on the request as a whole.",
		locator: "object",
	},
	"scm.repository.tree": {
		label: "Files in the repository",
		icon: FolderTreeIcon,
		badgeVariant: "outline",
		description: "Files that were not changed, read for context around the work.",
		locator: "code",
	},
	"scm.issue.core": {
		label: "The issue itself",
		icon: CircleDotIcon,
		badgeVariant: "outline",
		description: "Its title, description and state.",
		locator: "object",
	},
	"scm.issue.comments": {
		label: "Comments on the issue",
		icon: MessageSquareTextIcon,
		badgeVariant: "outline",
		description: "The discussion under the issue.",
		locator: "object",
	},
	"docs.document.core": {
		label: "The document itself",
		icon: FileTextIcon,
		badgeVariant: "outline",
		description: "The document under review, as it read at the time.",
		locator: "object",
	},
	"slack.conversation.thread": {
		label: "The conversation",
		icon: MessageCircleIcon,
		badgeVariant: "outline",
		description: "The chat thread under review, message by message.",
		locator: "object",
	},
	"scm.linked-work-items": {
		label: "Linked issues and requests",
		icon: LinkIcon,
		badgeVariant: "outline",
		description: "Work the reviewed item points at, for the intent behind it.",
		locator: "object",
	},
	"scm.review-threads": {
		label: "Review threads on the code",
		icon: MessageSquareCodeIcon,
		badgeVariant: "outline",
		description: "Conversations anchored to specific lines by human reviewers.",
		locator: "object",
	},
	"scm.general-review-comments": {
		label: "Review comments",
		icon: MessagesSquareIcon,
		badgeVariant: "outline",
		description: "Reviewers' remarks about the change overall.",
		locator: "object",
	},
	"workspace.project-inventory": {
		label: "What this project contains",
		icon: Layers2Icon,
		badgeVariant: "outline",
		description: "The languages, frameworks and layout the project was found to use.",
		locator: "object",
	},
	"outline.documents": {
		label: "Referenced documents",
		icon: LibraryIcon,
		badgeVariant: "outline",
		description: "Team documentation the reviewed work links to, such as a guideline.",
		locator: "object",
	},
	// Keyed by `hephaestus.*` on the wire; the words never say so. The product's name in a source
	// label would tell an operator which service wrote the row, which is not the question a piece of
	// evidence answers.
	"hephaestus.observation-history": {
		label: "Earlier observations",
		icon: HistoryIcon,
		badgeVariant: "outline",
		description: "What past reviews of this developer's work already recorded.",
		locator: "object",
	},
	"hephaestus.feedback-history": {
		label: "Feedback already sent",
		icon: MailCheckIcon,
		badgeVariant: "outline",
		description: "Feedback this developer has already had, so the review does not repeat it.",
		locator: "object",
	},
};

/**
 * The words for one source kind, or a neutral entry naming the unknown kind verbatim.
 *
 * A kind the server adds ships ahead of the words for it, and a citation rendering as a blank
 * heading is worse than one rendering its raw id: the id is at least searchable.
 */
export function evidenceSourceDef(sourceKind: string): EvidenceSourceDef {
	return (
		EVIDENCE_SOURCE_DEFS[sourceKind] ?? {
			label: sourceKind,
			icon: FileQuestionIcon,
			badgeVariant: "outline",
			description: "A source this version of the app has no description for.",
			locator: "object",
		}
	);
}

/** Every kind the app has words for, for a story that walks them all. */
export function knownEvidenceSourceKinds(): string[] {
	return Object.keys(EVIDENCE_SOURCE_DEFS);
}

/**
 * Which side of the diff a passage was read from — a question only the diff can be asked.
 *
 * The server enforces the biconditional: `side` is set exactly when the kind is the diff, and is
 * absent on all fourteen others. So this is never a general citation field, and the surface only
 * looks for it inside a code locator.
 */
export const DIFF_SIDE_LABELS = {
	OLD: "before",
	NEW: "after",
} satisfies Record<NonNullable<EvidenceCitation["side"]>, string>;

/**
 * A place in a file, as the coordinate a developer would paste: `path:12–18`.
 *
 * One spelling for two surfaces that both name a span of source — the citation an observation rests
 * on, and the line an inline note was anchored to — because a reader comparing the two is comparing
 * the same coordinate and a second formatter is a second en dash to get wrong.
 *
 * <p>A single line prints as one number rather than as `12–12`: the citation wire type defaults
 * `endLine` to `startLine`, and a placement leaves it absent. The diff side stays out of the string —
 * it is not part of a file coordinate, and it renders beside this as its own tag.
 */
export function codeCitationLocator(span: {
	path: string;
	startLine: number;
	endLine?: number;
}): string {
	const { path, startLine, endLine } = span;
	return endLine && endLine > startLine
		? `${path}:${startLine}–${endLine}`
		: `${path}:${startLine}`;
}

/**
 * Citations gathered under the source they came from, in the order the sources first appear.
 *
 * The old surface listed every citation flat and repeated its source kind on each one, having
 * already printed the same set of kinds as a row of badges above — the same string three times for
 * a finding with two diff quotes. Grouping says it once, where it explains the quotes under it.
 */
export interface EvidenceSourceGroup {
	sourceKind: string;
	def: EvidenceSourceDef;
	citations: EvidenceCitation[];
}

export function groupCitationsBySource(citations: EvidenceCitation[]): EvidenceSourceGroup[] {
	const groups = new Map<string, EvidenceSourceGroup>();
	for (const citation of citations) {
		const group = groups.get(citation.sourceKind);
		if (group) {
			group.citations.push(citation);
		} else {
			groups.set(citation.sourceKind, {
				sourceKind: citation.sourceKind,
				def: evidenceSourceDef(citation.sourceKind),
				citations: [citation],
			});
		}
	}
	return [...groups.values()];
}
