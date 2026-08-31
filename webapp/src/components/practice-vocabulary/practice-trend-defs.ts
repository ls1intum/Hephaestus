import { CircleDashedIcon, CircleHelpIcon, TrendingDownIcon, TrendingUpIcon } from "lucide-react";

import type { PracticeTrend } from "@/api/types.gen";

import type { StatusDefs } from "./status-def";

export type TrendDirection = PracticeTrend["direction"];

/**
 * Which way recent evidence points, in developer-facing words.
 *
 * `UNCERTAIN` and `INSUFFICIENT_EVIDENCE` are different answers and must not read alike: the first
 * means the comparison was made and did not separate, the second that no comparison was possible
 * yet. Both render muted, so the icon carries the distinction — a question mark against a dashed
 * circle.
 *
 * The wording deliberately claims nothing about ability. What the direction describes is a stretch
 * of recent work, which is also what the chip's tooltip says out loud.
 */
export const PRACTICE_TREND_DEFS: StatusDefs<TrendDirection> = {
	IMPROVING: {
		label: "More positive recently",
		icon: TrendingUpIcon,
		badgeVariant: "success",
		description: "Recent reviewed work carried more strengths than the stretch before it.",
	},
	DECLINING: {
		label: "More difficulties recently",
		icon: TrendingDownIcon,
		badgeVariant: "destructive",
		description: "Recent reviewed work carried more problems than the stretch before it.",
	},
	UNCERTAIN: {
		label: "Direction unclear",
		icon: CircleHelpIcon,
		badgeVariant: "secondary",
		description: "The two stretches were compared and did not separate far enough to call.",
	},
	INSUFFICIENT_EVIDENCE: {
		label: "Not enough to compare yet",
		icon: CircleDashedIcon,
		badgeVariant: "outline",
		description: "There is not yet enough reviewed work on both sides to compare.",
	},
};
