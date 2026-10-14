import {
	BotMessageSquareIcon,
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	EyeOffIcon,
	FileTextIcon,
	LoaderIcon,
	MapPinIcon,
	MessageSquareTextIcon,
	Undo2Icon,
} from "lucide-react";

import type { FeedbackCopy, FeedbackCopyCounts } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";
import { andList } from "@/lib/text";

export type FeedbackCopyKind = FeedbackCopy["kind"];
export type FeedbackCopyState = FeedbackCopy["state"];

/** The form one copy of a piece of feedback takes where it appears. */
export const FEEDBACK_COPY_KIND_DEFS: StatusDefs<FeedbackCopyKind> = {
	SUMMARY: {
		label: "Summary",
		icon: MessageSquareTextIcon,
		badgeVariant: "outline",
		description: "One comment on the work as a whole, or the card on the practice page.",
	},
	LINE_NOTE: {
		label: "Line note",
		icon: MapPinIcon,
		badgeVariant: "outline",
		description: "Anchored to specific lines, so it is read next to what it is about.",
	},
	FILE_NOTE: {
		label: "File note",
		icon: FileTextIcon,
		badgeVariant: "outline",
		description: "Anchored to one file rather than to lines in it.",
	},
	CHAT_TURN: {
		label: "Conversation turn",
		icon: BotMessageSquareIcon,
		badgeVariant: "outline",
		description: "Raised by Heph in a turn of the developer’s conversation.",
	},
};

/**
 * Where one copy stands. A copy that started writing but never confirmed is not repeated: the
 * provider is checked first, so an uncertain write never becomes a second comment.
 */
export const FEEDBACK_COPY_STATE_DEFS: StatusDefs<FeedbackCopyState> = {
	PLANNED: {
		label: "Planned",
		icon: CircleDashedIcon,
		badgeVariant: "secondary",
		description: "Composed and waiting to be written.",
	},
	WRITE_STARTED: {
		label: "Writing",
		icon: LoaderIcon,
		badgeVariant: "warning",
		description:
			"A write to the provider started. Until the provider confirms it, the copy may already appear.",
	},
	POSTED: {
		label: "Posted",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "The provider confirmed the copy.",
	},
	FAILED: {
		label: "Failed",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "The write failed before any text appeared.",
	},
	WITHHELD: {
		label: "Withheld",
		icon: EyeOffIcon,
		badgeVariant: "outline",
		description: "A check stopped this copy before it was written.",
	},
	RETRACTED: {
		label: "Retracted",
		icon: Undo2Icon,
		badgeVariant: "outline",
		description: "It was posted, then removed or corrected on the provider.",
	},
};

/** Counts per state, in the order a reader scans a package: what landed, then what did not. */
const COUNT_ORDER = [
	["posted", "posted"],
	["writeStarted", "writing"],
	["planned", "planned"],
	["failed", "failed"],
	["withheld", "withheld"],
	["retracted", "retracted"],
] as const satisfies readonly (readonly [keyof FeedbackCopyCounts, string])[];

/**
 * The copy counts as one phrase: "1 posted · 2 failed". Empty when the package has no copies,
 * so a caller never prints a false "0 posted".
 */
export function copyCountsSentence(counts: FeedbackCopyCounts): string {
	return COUNT_ORDER.filter(([key]) => counts[key] > 0)
		.map(([key, word]) => `${counts[key]} ${word}`)
		.join(" · ");
}

/** Whether some copies landed and others did not: a partial package, which no single state names. */
export function isPartialPackage(counts: FeedbackCopyCounts): boolean {
	return (
		counts.posted > 0 && counts.failed + counts.withheld + counts.planned + counts.writeStarted > 0
	);
}

const KIND_NOUNS: Record<FeedbackCopyKind, [singular: string, plural: string]> = {
	SUMMARY: ["summary", "summaries"],
	LINE_NOTE: ["line note", "line notes"],
	FILE_NOTE: ["file note", "file notes"],
	CHAT_TURN: ["conversation turn", "conversation turns"],
};

/** "a summary and 2 line notes": the kinds a package carries, for a sentence. */
export function copyKindsPhrase(copies: readonly Pick<FeedbackCopy, "kind">[]): string {
	const counts = new Map<FeedbackCopyKind, number>();
	for (const { kind } of copies) {
		counts.set(kind, (counts.get(kind) ?? 0) + 1);
	}
	return andList.format(
		[...counts].map(([kind, count]) => {
			const [singular, plural] = KIND_NOUNS[kind];
			return count === 1 ? `a ${singular}` : `${count} ${plural}`;
		}),
	);
}
