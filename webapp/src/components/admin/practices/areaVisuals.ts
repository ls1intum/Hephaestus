import {
	Activity,
	AlertTriangle,
	Archive,
	Award,
	Bell,
	Bookmark,
	BookOpen,
	BookText,
	Box,
	Boxes,
	Braces,
	Bug,
	Calendar,
	CheckCheck,
	CircleAlert,
	CircleCheck,
	Clipboard,
	ClipboardCheck,
	ClipboardList,
	Clock,
	Cloud,
	Code,
	Code2,
	Cog,
	Compass,
	Cpu,
	Database,
	Eye,
	FileCheck,
	FileCode,
	FileText,
	Filter,
	Flag,
	FlaskConical,
	Folder,
	FolderGit2,
	GitBranch,
	GitCommitHorizontal,
	GitCommitVertical,
	GitMerge,
	GitPullRequest,
	Globe,
	Hammer,
	Heart,
	Inbox,
	Key,
	KeyRound,
	Layers,
	LayoutDashboard,
	Lightbulb,
	ListChecks,
	ListTodo,
	Lock,
	type LucideIcon,
	Mail,
	MapPin,
	Megaphone,
	MessageCircle,
	MessageSquare,
	MessageSquareReply,
	Microscope,
	Milestone,
	Monitor,
	Network,
	Package,
	Pencil,
	PenTool,
	PieChart,
	Pin,
	Puzzle,
	Radar,
	Rocket,
	Ruler,
	Scale,
	Search,
	Server,
	Settings,
	Shield,
	ShieldAlert,
	ShieldCheck,
	ShieldX,
	Signpost,
	Siren,
	Sparkles,
	Star,
	Tag,
	Target,
	Terminal,
	TestTube,
	TestTubeDiagonal,
	ThumbsUp,
	Timer,
	TrendingUp,
	Trophy,
	Users,
	Wand2,
	Workflow,
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
 * Every family is contrast-checked: text-700/800 on bg-100 (light) and text-200 on bg-950/800 (dark)
 * all clear AA. Free-form hex is intentionally NOT offered — it would defeat this guaranteed-legible,
 * theme-aware, purge-safe contract; admins pick from this curated, accessible spectrum instead.
 */
export type AreaVisual = { Icon: LucideIcon; pill: string; blocking: boolean };

/** Palette family → accessible chip classes (AA on Tailwind 50–950, light + dark). */
export const PILL: Record<string, string> = {
	red: "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-200",
	orange: "bg-orange-100 text-orange-800 dark:bg-orange-950 dark:text-orange-200",
	amber: "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200",
	yellow: "bg-yellow-100 text-yellow-800 dark:bg-yellow-950 dark:text-yellow-200",
	lime: "bg-lime-100 text-lime-800 dark:bg-lime-950 dark:text-lime-200",
	green: "bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-200",
	emerald: "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-200",
	teal: "bg-teal-100 text-teal-700 dark:bg-teal-950 dark:text-teal-200",
	cyan: "bg-cyan-100 text-cyan-800 dark:bg-cyan-950 dark:text-cyan-200",
	sky: "bg-sky-100 text-sky-700 dark:bg-sky-950 dark:text-sky-200",
	blue: "bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-200",
	indigo: "bg-indigo-100 text-indigo-700 dark:bg-indigo-950 dark:text-indigo-200",
	violet: "bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-200",
	purple: "bg-purple-100 text-purple-700 dark:bg-purple-950 dark:text-purple-200",
	fuchsia: "bg-fuchsia-100 text-fuchsia-700 dark:bg-fuchsia-950 dark:text-fuchsia-200",
	pink: "bg-pink-100 text-pink-700 dark:bg-pink-950 dark:text-pink-200",
	rose: "bg-rose-100 text-rose-700 dark:bg-rose-950 dark:text-rose-200",
	slate: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200",
	gray: "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-200",
	zinc: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200",
	stone: "bg-stone-100 text-stone-700 dark:bg-stone-800 dark:text-stone-200",
};

/** The colour keys offered in the admin picker, in spectrum-then-neutral display order. */
export const COLOR_KEYS = Object.keys(PILL);

/** Curated lucide icons offered in the admin picker — a broad, searchable software-practice set. */
export const ICON_COMPONENTS: Record<string, LucideIcon> = {
	ShieldAlert,
	ShieldCheck,
	ShieldX,
	Shield,
	Lock,
	Key,
	KeyRound,
	Siren,
	AlertTriangle,
	CircleAlert,
	Bug,
	TestTube,
	TestTubeDiagonal,
	FlaskConical,
	Microscope,
	CheckCheck,
	CircleCheck,
	FileCheck,
	ClipboardCheck,
	ClipboardList,
	Clipboard,
	ListChecks,
	ListTodo,
	Flag,
	Milestone,
	Target,
	Signpost,
	Compass,
	Radar,
	Search,
	GitPullRequest,
	GitBranch,
	GitMerge,
	GitCommitHorizontal,
	GitCommitVertical,
	FolderGit2,
	MessageSquareReply,
	MessageSquare,
	MessageCircle,
	Megaphone,
	Mail,
	Inbox,
	Bell,
	Eye,
	Users,
	ThumbsUp,
	Heart,
	Award,
	Trophy,
	Star,
	Sparkles,
	Wand2,
	Lightbulb,
	Wrench,
	Hammer,
	Cog,
	Settings,
	Workflow,
	Puzzle,
	Layers,
	Boxes,
	Box,
	Package,
	Archive,
	Code,
	Code2,
	Braces,
	Terminal,
	FileCode,
	PenTool,
	Pencil,
	FileText,
	BookText,
	BookOpen,
	Bookmark,
	Ruler,
	Scale,
	Filter,
	Tag,
	Pin,
	MapPin,
	Calendar,
	Clock,
	Timer,
	Activity,
	TrendingUp,
	PieChart,
	LayoutDashboard,
	Database,
	Server,
	Cpu,
	Cloud,
	Network,
	Globe,
	Monitor,
	Rocket,
	Zap,
	Folder,
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
