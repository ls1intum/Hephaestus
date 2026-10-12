import {
	BotIcon,
	ListChecksIcon,
	type LucideIcon,
	MessageSquareTextIcon,
	TargetIcon,
	ZapIcon,
} from "lucide-react";

import { type RailStage, TRACE_STAGE_LABELS } from "./lineage-story";
import type { TraceBottleneck } from "./trace-bottleneck";

/** The headers of the rail: the four stages, and the results as a part of the review of their own. */
export type StageKey = RailStage["stage"] | "results";

/** Each header's name, icon and what the stage holds, for the rail and the legend. */
export const STAGE_HEADERS: Record<StageKey, { label: string; icon: LucideIcon; holds: string }> = {
	trigger: {
		label: TRACE_STAGE_LABELS.trigger,
		icon: ZapIcon,
		holds: "the work and what started the review",
	},
	review: {
		label: TRACE_STAGE_LABELS.review,
		icon: BotIcon,
		holds: "how it ran, with queue and run times",
	},
	results: {
		label: "Results",
		icon: ListChecksIcon,
		holds: "what the review observed",
	},
	feedback: {
		label: TRACE_STAGE_LABELS.feedback,
		icon: MessageSquareTextIcon,
		holds: "each piece, where it went or why not",
	},
	effect: {
		label: TRACE_STAGE_LABELS.effect,
		icon: TargetIcon,
		holds: "what the developer made of it",
	},
};

export const STAGE_ORDER: readonly StageKey[] = [
	"trigger",
	"review",
	"results",
	"feedback",
	"effect",
];

/** The mark on the header of the stage the bottleneck line names, for the rail and the legend. */
export const STAGE_MARKS: Record<TraceBottleneck["kind"], string> = {
	failed: "Stopped here",
	lost: "Lost here",
	slow: "Time went here",
	waiting: "Waiting here",
	none: "",
};
