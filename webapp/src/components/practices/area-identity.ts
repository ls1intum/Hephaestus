import type { LucideIcon } from "lucide-react";
import { areaSeed, getAreaVisual } from "@/components/admin/practices/areaVisuals";

/**
 * Visual identity of a practice area on the practice surfaces: one icon, one hue, everywhere.
 * The icon and colour come from the same source of truth as the admin catalogue
 * ({@link getAreaVisual} with the seeded defaults and keyword fallback), so an area looks the
 * same on a report card, a matrix column and the catalogue page. The report DTOs carry only
 * the area slug and name, which is exactly what the resolver needs.
 *
 * Colour is a secondary grouping cue only. The icon plus the area name carry the meaning, so
 * every surface stays legible for colour-blind readers.
 */
export interface AreaIdentity {
	Icon: LucideIcon;
	/** Foreground tone for the area icon, e.g. on chips and matrix headers. */
	iconClassName: string;
	/** Soft background tint matching the icon tone, for chips and card headers. */
	tintClassName: string;
}

/**
 * Foreground tone per palette key. Written as full literal strings so Tailwind keeps them.
 * The 600/400 split clears AA against the app background in both themes.
 */
const ICON_TONE: Record<string, string> = {
	red: "text-red-600 dark:text-red-400",
	orange: "text-orange-600 dark:text-orange-400",
	amber: "text-amber-600 dark:text-amber-400",
	yellow: "text-yellow-600 dark:text-yellow-400",
	lime: "text-lime-600 dark:text-lime-400",
	green: "text-green-600 dark:text-green-400",
	emerald: "text-emerald-600 dark:text-emerald-400",
	teal: "text-teal-600 dark:text-teal-400",
	cyan: "text-cyan-600 dark:text-cyan-400",
	sky: "text-sky-600 dark:text-sky-400",
	blue: "text-blue-600 dark:text-blue-400",
	indigo: "text-indigo-600 dark:text-indigo-400",
	violet: "text-violet-600 dark:text-violet-400",
	purple: "text-purple-600 dark:text-purple-400",
	fuchsia: "text-fuchsia-600 dark:text-fuchsia-400",
	pink: "text-pink-600 dark:text-pink-400",
	rose: "text-rose-600 dark:text-rose-400",
	slate: "text-slate-600 dark:text-slate-400",
	gray: "text-gray-600 dark:text-gray-400",
	zinc: "text-zinc-600 dark:text-zinc-400",
	stone: "text-stone-600 dark:text-stone-400",
};

/** Soft background tint per palette key, matching {@link ICON_TONE}. Full literals for Tailwind. */
const TINT: Record<string, string> = {
	red: "bg-red-100 dark:bg-red-950",
	orange: "bg-orange-100 dark:bg-orange-950",
	amber: "bg-amber-100 dark:bg-amber-950",
	yellow: "bg-yellow-100 dark:bg-yellow-950",
	lime: "bg-lime-100 dark:bg-lime-950",
	green: "bg-green-100 dark:bg-green-950",
	emerald: "bg-emerald-100 dark:bg-emerald-950",
	teal: "bg-teal-100 dark:bg-teal-950",
	cyan: "bg-cyan-100 dark:bg-cyan-950",
	sky: "bg-sky-100 dark:bg-sky-950",
	blue: "bg-blue-100 dark:bg-blue-950",
	indigo: "bg-indigo-100 dark:bg-indigo-950",
	violet: "bg-violet-100 dark:bg-violet-950",
	purple: "bg-purple-100 dark:bg-purple-950",
	fuchsia: "bg-fuchsia-100 dark:bg-fuchsia-950",
	pink: "bg-pink-100 dark:bg-pink-950",
	rose: "bg-rose-100 dark:bg-rose-950",
	slate: "bg-slate-100 dark:bg-slate-800",
	gray: "bg-gray-100 dark:bg-gray-800",
	zinc: "bg-zinc-100 dark:bg-zinc-800",
	stone: "bg-stone-100 dark:bg-stone-800",
};

/** Resolve the practice-surface identity for an area from its slug and name. */
export function getAreaIdentity(areaSlug: string, areaName = ""): AreaIdentity {
	const { Icon } = getAreaVisual(areaSlug, areaName);
	const { color } = areaSeed(areaSlug, areaName);
	return {
		Icon,
		iconClassName: ICON_TONE[color] ?? ICON_TONE.slate,
		tintClassName: TINT[color] ?? TINT.slate,
	};
}
