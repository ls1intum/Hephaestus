import { Dot, type LucideIcon, TrendingDown, TrendingUp } from "lucide-react";
import type { AreaStatusCell } from "@/api/types.gen";
import { cn } from "@/lib/utils";

export type Trend = AreaStatusCell["trend"];

// Direction versus the prior review cycle — criterion-referenced, never a peer comparison. STEADY
// carries no signal worth surfacing, so it's deliberately absent from this map: both renderers below
// treat a missing entry as "render nothing." Single source of truth for the icon, tone, and copy so
// the roster glyph and the reflection-card note never drift apart.
export const TREND_META: Partial<
	Record<Trend, { icon: LucideIcon; ariaLabel: string; sentence: string; toneClassName: string }>
> = {
	IMPROVING: {
		icon: TrendingUp,
		ariaLabel: "improving",
		sentence: "Improving since last cycle",
		toneClassName: "text-provider-done-foreground",
	},
	WORSENING: {
		icon: TrendingDown,
		ariaLabel: "worsening",
		sentence: "Declining since last cycle",
		toneClassName: "text-provider-attention-foreground",
	},
	NEW: {
		icon: Dot,
		ariaLabel: "new since last cycle",
		sentence: "New this cycle",
		toneClassName: "text-muted-foreground",
	},
};

/** A compact icon-only glyph for dense layouts (e.g. the roster). Renders nothing for STEADY. */
export function TrendGlyph({ trend }: { trend: Trend }) {
	const meta = TREND_META[trend];
	if (!meta) return null;
	const Icon = meta.icon;
	return (
		<span className={cn("inline-flex", meta.toneClassName)} role="img" aria-label={meta.ariaLabel}>
			<Icon className="size-3.5" aria-hidden="true" />
		</span>
	);
}

/** A short, readable line pairing the icon with plain-language copy. Renders nothing for STEADY. */
export function TrendNote({ trend }: { trend: Trend }) {
	const meta = TREND_META[trend];
	if (!meta) return null;
	const Icon = meta.icon;
	return (
		<p className={cn("flex items-center gap-1.5 text-sm font-medium", meta.toneClassName)}>
			<Icon className="size-4" aria-hidden="true" />
			{meta.sentence}
		</p>
	);
}
