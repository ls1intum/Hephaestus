import {
	BookText,
	Boxes,
	Bug,
	Database,
	Eye,
	FileText,
	Flag,
	Folder,
	GitBranch,
	GitPullRequest,
	Hammer,
	ListChecks,
	Lock,
	type LucideIcon,
	MessageSquareReply,
	Package,
	Rocket,
	ShieldAlert,
	ShieldCheck,
	Sparkles,
	TestTube,
	Users,
	Wrench,
	Zap,
} from "lucide-react";

/**
 * Visual identity for a practice area: a lucide icon (the primary, glanceable identity) plus an
 * accessible colour pill. Colour is a redundant cue only — the icon and the area NAME always carry
 * the meaning, so the chip stays legible for colour-blind readers and never relies on hue alone.
 *
 * An area persists an optional {@code icon} (a lucide name) and {@code color} (a palette key) that an
 * admin can edit; when set they win, otherwise the seeded defaults below apply, otherwise a keyword
 * fallback. `blocking` marks the three correctness/security areas whose practices may present as
 * merge-blockers (mirrors `PracticeDetectionResultParser.BLOCKING_ELIGIBLE_PRACTICES` server-side).
 *
 * Pill classes are written as full literal strings so Tailwind keeps them (no runtime interpolation).
 */
export type AreaVisual = { Icon: LucideIcon; pill: string; blocking: boolean };

/** Palette family → accessible chip classes (AA on Tailwind 50–950, light + dark). */
export const PILL: Record<string, string> = {
	rose: "bg-rose-100 text-rose-700 dark:bg-rose-950 dark:text-rose-200",
	red: "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-200",
	amber: "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200",
	sky: "bg-sky-100 text-sky-700 dark:bg-sky-950 dark:text-sky-200",
	cyan: "bg-cyan-100 text-cyan-800 dark:bg-cyan-950 dark:text-cyan-200",
	teal: "bg-teal-100 text-teal-700 dark:bg-teal-950 dark:text-teal-200",
	emerald: "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-200",
	violet: "bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-200",
	indigo: "bg-indigo-100 text-indigo-700 dark:bg-indigo-950 dark:text-indigo-200",
	slate: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200",
	fuchsia: "bg-fuchsia-100 text-fuchsia-700 dark:bg-fuchsia-950 dark:text-fuchsia-200",
};

/** The colour keys offered in the admin picker, in display order. */
export const COLOR_KEYS = Object.keys(PILL);

/** Curated lucide icons offered in the admin picker (the area defaults plus a few general extras). */
export const ICON_COMPONENTS: Record<string, LucideIcon> = {
	ShieldAlert,
	ShieldCheck,
	Lock,
	TestTube,
	Bug,
	Package,
	GitPullRequest,
	MessageSquareReply,
	Eye,
	Users,
	Wrench,
	Hammer,
	Sparkles,
	FileText,
	ListChecks,
	Flag,
	BookText,
	GitBranch,
	Rocket,
	Database,
	Boxes,
	Zap,
};

/** The icon names offered in the admin picker, in display order. */
export const ICON_NAMES = Object.keys(ICON_COMPONENTS);

/** Resolve a stored lucide name to a component, or undefined if unknown/unset. */
export function resolveIcon(name?: string | null): LucideIcon | undefined {
	return name ? ICON_COMPONENTS[name] : undefined;
}

type Seed = { icon: string; color: string; blocking: boolean };

/** Icon + colour for each of the 11 seeded practice areas (keyed on slug). */
const AREA_SEEDS: Record<string, Seed> = {
	"robust-error-handling": { icon: "ShieldAlert", color: "rose", blocking: true },
	"secure-by-default-changes": { icon: "ShieldCheck", color: "red", blocking: true },
	"testing-discipline": { icon: "TestTube", color: "amber", blocking: true },
	"review-ready-work": { icon: "Package", color: "sky", blocking: false },
	"acting-on-review-feedback": { icon: "MessageSquareReply", color: "cyan", blocking: false },
	"constructive-code-review": { icon: "Eye", color: "teal", blocking: false },
	"code-craftsmanship": { icon: "Wrench", color: "emerald", blocking: false },
	"actionable-issue-authoring": { icon: "FileText", color: "violet", blocking: false },
	"issue-traceability-and-lifecycle": { icon: "ListChecks", color: "indigo", blocking: false },
	"decisions-and-documentation": { icon: "BookText", color: "slate", blocking: false },
	"delivery-and-version-control-discipline": {
		icon: "GitBranch",
		color: "fuchsia",
		blocking: false,
	},
};

/** The slugs of the seeded areas — exported for tests/guards. */
export const SEEDED_AREA_SLUGS = Object.keys(AREA_SEEDS);

const FALLBACK: Seed = { icon: "Folder", color: "slate", blocking: false };

/** The effective icon/colour *names* for an area before any admin override — for picker highlighting. */
export function areaSeed(
	slug: string,
	name = "",
): { icon: string; color: string; blocking: boolean } {
	return seedFor(slug, name);
}

/** Seed defaults for any slug: the curated entry, else a keyword guess, else a neutral folder. */
function seedFor(slug: string, name: string): Seed {
	const known = AREA_SEEDS[slug];
	if (known) return known;
	const h = `${slug} ${name}`.toLowerCase();
	// Never infer the blocking flag for an admin-created area — only the three seeded areas are eligible.
	if (/secur|shield|auth|permission|escap/.test(h))
		return { icon: "ShieldCheck", color: "red", blocking: false };
	if (/error|fail|crash|exception|panic/.test(h))
		return { icon: "ShieldAlert", color: "rose", blocking: false };
	if (/test/.test(h)) return { icon: "TestTube", color: "amber", blocking: false };
	if (/issue|triage|plan|track|backlog/.test(h))
		return { icon: "ListChecks", color: "indigo", blocking: false };
	if (/review|comment|feedback/.test(h))
		return { icon: "MessageSquareReply", color: "cyan", blocking: false };
	if (/doc|decision|rationale|record/.test(h))
		return { icon: "BookText", color: "slate", blocking: false };
	if (/commit|branch|deliver|version|merge/.test(h))
		return { icon: "GitBranch", color: "fuchsia", blocking: false };
	return FALLBACK;
}

/**
 * Resolve the visual for an area. An explicit (admin-set) `icon`/`color` wins; otherwise the seeded
 * default for the slug; otherwise a keyword fallback. Unknown icon names / colour keys degrade to the
 * seed so the chip always renders.
 */
export function getAreaVisual(
	slug: string,
	name = "",
	icon?: string | null,
	color?: string | null,
): AreaVisual {
	const seed = seedFor(slug, name);
	const Icon = resolveIcon(icon) ?? ICON_COMPONENTS[seed.icon] ?? Folder;
	const pill = (color && PILL[color]) || PILL[seed.color] || PILL.slate;
	return { Icon, pill, blocking: seed.blocking };
}
