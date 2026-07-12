import {
	CircleCheck,
	CircleDot,
	GitMerge,
	GitPullRequestArrow,
	GitPullRequestClosed,
	type LucideIcon,
	MessagesSquare,
	Sparkles,
	TrendingDown,
	TrendingUp,
} from "lucide-react";
import type {
	ArtifactKind,
	ArtifactState,
	PracticeStatus,
	PracticeTrend,
} from "@/components/practices/practice-types";
import { cn } from "@/lib/utils";

/**
 * The one shared visual vocabulary for status and trend across every practice surface.
 * Status is criterion referenced per practice and carries no number and no rank. Trend copy
 * is blame free by construction ("Declining since last cycle", never "Slipped").
 */
export const STATUS_META: Record<
	PracticeStatus,
	{ label: string; dotClassName: string; chipClassName: string }
> = {
	STRENGTH: {
		label: "Strength",
		dotClassName: "bg-provider-success-foreground",
		chipClassName: "bg-provider-success/20 text-provider-success-foreground",
	},
	MIXED: {
		label: "Mixed",
		dotClassName: "bg-provider-done-foreground/70",
		chipClassName: "bg-provider-done/20 text-provider-done-foreground",
	},
	DEVELOPING: {
		label: "Developing",
		dotClassName: "bg-provider-attention-foreground",
		chipClassName: "bg-provider-attention/25 text-provider-attention-foreground",
	},
	NO_ACTIVITY: {
		label: "No activity yet",
		dotClassName: "border border-muted-foreground/40 bg-transparent",
		chipClassName: "bg-muted text-muted-foreground",
	},
};

export const TREND_META: Partial<
	Record<PracticeTrend, { icon: LucideIcon; label: string; toneClassName: string }>
> = {
	IMPROVING: {
		icon: TrendingUp,
		label: "Improving since last cycle",
		toneClassName: "text-provider-success-foreground",
	},
	WORSENING: {
		icon: TrendingDown,
		label: "Declining since last cycle",
		toneClassName: "text-provider-attention-foreground",
	},
	NEW: {
		icon: Sparkles,
		label: "New this cycle",
		toneClassName: "text-muted-foreground",
	},
};

export interface StatusDotProps {
	status: PracticeStatus;
	className?: string;
}

/** A 2.5px status dot, the smallest unit of the language. NO_ACTIVITY renders hollow. */
export function StatusDot({ status, className }: StatusDotProps) {
	const meta = STATUS_META[status];
	return (
		<span
			role="img"
			aria-label={meta.label}
			className={cn("inline-block size-2.5 shrink-0 rounded-full", meta.dotClassName, className)}
		/>
	);
}

export interface StatusChipProps {
	status: PracticeStatus;
	className?: string;
}

/** The readable status chip for places with more room. Same vocabulary as the dot. */
export function StatusChip({ status, className }: StatusChipProps) {
	const meta = STATUS_META[status];
	return (
		<span
			className={cn(
				"inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap",
				meta.chipClassName,
				className,
			)}
		>
			{meta.label}
		</span>
	);
}

export interface TrendGlyphProps {
	trend: PracticeTrend;
	className?: string;
}

/** Directional trend glyph. STEADY carries no signal, so it deliberately renders nothing. */
export function TrendGlyph({ trend, className }: TrendGlyphProps) {
	const meta = TREND_META[trend];
	if (!meta) return null;
	const Icon = meta.icon;
	return (
		<span
			role="img"
			aria-label={meta.label}
			title={meta.label}
			className={cn("inline-flex shrink-0", meta.toneClassName, className)}
		>
			<Icon className="size-3.5" aria-hidden="true" />
		</span>
	);
}

/** Trend glyph plus its plain sentence, for detail surfaces. Nothing for STEADY. */
export function TrendNote({ trend, className }: TrendGlyphProps) {
	const meta = TREND_META[trend];
	if (!meta) return null;
	const Icon = meta.icon;
	return (
		<span
			className={cn(
				"inline-flex items-center gap-1.5 text-xs font-medium",
				meta.toneClassName,
				className,
			)}
		>
			<Icon className="size-3.5" aria-hidden="true" />
			{meta.label}
		</span>
	);
}

const PULL_REQUEST_ICON: Record<
	ArtifactState,
	{ icon: LucideIcon; toneClassName: string; label: string }
> = {
	OPEN: {
		icon: GitPullRequestArrow,
		toneClassName: "text-provider-open-foreground",
		label: "Open pull request",
	},
	MERGED: {
		icon: GitMerge,
		toneClassName: "text-provider-done-foreground",
		label: "Merged pull request",
	},
	CLOSED: {
		icon: GitPullRequestClosed,
		toneClassName: "text-provider-closed-foreground",
		label: "Closed pull request",
	},
};

const ISSUE_ICON: Record<
	ArtifactState,
	{ icon: LucideIcon; toneClassName: string; label: string }
> = {
	OPEN: { icon: CircleDot, toneClassName: "text-provider-open-foreground", label: "Open issue" },
	MERGED: {
		icon: CircleCheck,
		toneClassName: "text-provider-done-foreground",
		label: "Closed issue",
	},
	CLOSED: {
		icon: CircleCheck,
		toneClassName: "text-provider-closed-foreground",
		label: "Closed issue",
	},
};

export interface ArtifactStateIconProps {
	kind: ArtifactKind;
	state?: ArtifactState;
	className?: string;
}

/** The provider-tinted PR or issue state icon used wherever an artifact is referenced. */
export function ArtifactStateIcon({ kind, state, className }: ArtifactStateIconProps) {
	const meta =
		kind === "CONVERSATION_THREAD"
			? { icon: MessagesSquare, toneClassName: "text-muted-foreground", label: "Conversation" }
			: kind === "PULL_REQUEST"
				? PULL_REQUEST_ICON[state ?? "OPEN"]
				: ISSUE_ICON[state ?? "OPEN"];
	const Icon = meta.icon;
	return (
		<span
			role="img"
			aria-label={meta.label}
			className={cn("inline-flex shrink-0", meta.toneClassName, className)}
		>
			<Icon className="size-4" aria-hidden="true" />
		</span>
	);
}
